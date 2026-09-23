// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.server.util;

import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.util.HttpUtil;
import com.mirth.connect.util.MirthSSLUtil;

/**
 * Checks a candidate password against a Have I Been Pwned style range API
 */
public class BreachedPasswordChecker {

    private static final Logger logger = LogManager.getLogger(BreachedPasswordChecker.class);

    private static final int REQUEST_TIMEOUT = 3000;

    private BreachedPasswordChecker() {
        // nop, static
    }

    /** Determines whether the password appears in a known breach. */
    public static boolean checkBreached(String plainPassword, String rangeUrl) {
        if (StringUtils.isBlank(rangeUrl)) {
            throw new IllegalArgumentException("rangeUrl must not be blank");
        }

        // HIBP uses the first 20 bits of the SHA-1 hash to limit disclosure (k-anonymity)
        String hash = DigestUtils.sha1Hex(plainPassword).toUpperCase(Locale.ROOT);
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);

        // getOrEmpty returns an empty string on any failure, so the check fails open
        String response = getOrEmpty(StringUtils.appendIfMissing(rangeUrl, "/") + prefix);
        return response.contains(suffix);
    }

    /** Insulate password checks from network and service failures */
    private static String getOrEmpty(String url) {
        FutureTask<String> task = new FutureTask<String>(() -> HttpUtil.executeGetRequest(url, 
            REQUEST_TIMEOUT, true, MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS, MirthSSLUtil.DEFAULT_HTTPS_CIPHER_SUITES));
        Thread thread = new Thread(task, "Breached Password Checker");
        thread.setDaemon(true);
        thread.start();

        try {
            return task.get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            logger.warn("The breached password service at " + url + " did not respond within " 
                + REQUEST_TIMEOUT + "ms. Skipping the breached password check.");
            return "";
        } finally {
            task.cancel(true);
        }
    }
}
