// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.util.MirthSSLUtil;

/**
 * Exercises the client's certificate trust against the certificate the live server actually
 * presents, which is self-signed and generated on first boot under a hostname no certificate
 * could match.
 *
 * <p>These build their own clients rather than using the shared one, so the trust setting the
 * rest of the harness runs with is not disturbed.
 */
@DisplayName("Client certificate pinning")
class PinnedClientTrustTest {

    /** SHA-256 thumbprint of the certificate the server under test is presenting. */
    private static String thumbprint;

    @BeforeAll
    static void discoverServerCertificate() throws Exception {
        // The client side of TLS is identical across configurations; only the server image
        // that generated the certificate varies, and there are two of those.
        Harness.assumeConfiguration("alpine-temurin21-derby", "ubuntu-temurin21-derby");
        assumeTrue(HarnessConfig.BASE_URL.startsWith("https"),
                () -> "trust tests need an https base URL, got " + HarnessConfig.BASE_URL);

        thumbprint = ServerCertificate.discoverThumbprint(
                HarnessConfig.BASE_URL, HarnessConfig.REQUEST_TIMEOUT_MILLIS);
        assertTrue(thumbprint.matches("[0-9a-f]{64}"),
                () -> "discovered thumbprint is not a SHA-256 hex string: " + thumbprint);
    }

    @Test
    @DisplayName("logs in when the server's certificate is pinned")
    void loginSucceedsWithDiscoveredThumbprint() throws Exception {
        // Pinning alone: no pki, no localhost, and the hostname does not match the
        // certificate, so the pin is the only reason this connection can succeed.
        try (Client client = client(thumbprint)) {
            LoginStatus status = client.login(HarnessConfig.USERNAME, HarnessConfig.PASSWORD);

            assertNotNull(status, "no response to login");
            assertTrue(status.isSuccess(),
                    () -> "login failed: " + status.getStatus() + " " + status.getMessage());

            client.logout();
        }
    }

    @Test
    @DisplayName("refuses to connect when a different certificate is pinned")
    void loginFailsWithWrongThumbprint() {
        // Derived from the real one so it stays a well-formed token, and so a pass means the
        // comparison is exact rather than merely rejecting garbage.
        char last = thumbprint.charAt(thumbprint.length() - 1);
        String wrong = thumbprint.substring(0, thumbprint.length() - 1) + (last == '0' ? '1' : '0');

        assertTrustFailure(wrong);
    }

    @Test
    @DisplayName("refuses to connect to a self-signed certificate with pki alone")
    void loginFailsWithPkiOnly() {
        // Guards the regression this suite tripped over: pki must really consult the JVM
        // trust store rather than quietly accepting a self-signed certificate.
        assertTrustFailure("pki");
    }

    /** Builds what the harness builds, differing only in the trust configuration. */
    private static Client client(String pinnedClientTrust) throws Exception {
        return new Client(HarnessConfig.BASE_URL, HarnessConfig.REQUEST_TIMEOUT_MILLIS,
                MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS, MirthSSLUtil.DEFAULT_HTTPS_CIPHER_SUITES,
                pinnedClientTrust);
    }

    /**
     * Asserts that connecting with {@code pinnedClientTrust} fails for a TLS trust reason.
     *
     * <p>The assertion is on an {@link SSLException} anywhere in the cause chain rather than on
     * a specific type or message. The client wraps failures repeatedly (ClientException inside
     * ProcessingException, and so on), and a rejected pin surfaces as the JDK's "trustAnchors
     * parameter must be non-empty" rather than anything mentioning pinning, because a
     * thumbprint-only configuration defers to an empty trust store. Failing for the wrong
     * reason entirely -- server down, bad DNS, rejected credentials -- produces no SSLException
     * and so still fails here, with the whole chain printed.
     */
    private static void assertTrustFailure(String pinnedClientTrust) {
        Throwable failure = assertThrows(Throwable.class, () -> {
            try (Client client = client(pinnedClientTrust)) {
                client.login(HarnessConfig.USERNAME, HarnessConfig.PASSWORD);
            }
        }, () -> "expected trust '" + pinnedClientTrust + "' to be rejected, but the login succeeded");

        assertTrue(causeChain(failure).stream().anyMatch(SSLException.class::isInstance),
                () -> "expected a TLS trust failure for trust '" + pinnedClientTrust
                        + "', got: " + describeChain(failure));
    }

    private static List<Throwable> causeChain(Throwable throwable) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = throwable; current != null && !chain.contains(current);
                current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    private static String describeChain(Throwable throwable) {
        return causeChain(throwable).stream()
                .map(t -> t.getClass().getName() + ": " + t.getMessage())
                .collect(Collectors.joining("\n  caused by "));
    }
}
