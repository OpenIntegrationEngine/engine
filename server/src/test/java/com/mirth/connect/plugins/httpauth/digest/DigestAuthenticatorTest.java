package com.mirth.connect.plugins.httpauth.digest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.donkey.util.MessageMaps;
import com.mirth.connect.plugins.httpauth.AuthenticationResult;
import com.mirth.connect.plugins.httpauth.AuthenticationResult.AuthStatus;
import com.mirth.connect.plugins.httpauth.RequestInfo;
import com.mirth.connect.plugins.httpauth.digest.DigestHttpAuthProperties.Algorithm;
import com.mirth.connect.plugins.httpauth.digest.DigestHttpAuthProperties.QOPMode;

public class DigestAuthenticatorTest {

    private static final String REALM = "My Realm";
    private static final String OPAQUE = "opaque-value";
    private static final String URI = "/api/test";
    private static final String USER = "alice";
    private static final String PASSWORD = "secret";

    DigestAuthenticator authenticator;
    Connector connector;
    DigestAuthenticatorProvider provider;
    CustomMessageMap messageMap;
    DigestHttpAuthProperties props;

    @Before
    public void setup() {
        connector = Mockito.mock(Connector.class);
        Channel channel = Mockito.mock(Channel.class);
        doReturn("channelId").when(connector).getChannelId();
        doReturn(channel).when(connector).getChannel();
        doReturn("channelName").when(channel).getName();
        provider = Mockito.mock(DigestAuthenticatorProvider.class);
        doReturn(connector).when(provider).getConnector();
        messageMap = new CustomMessageMap();
        messageMap.map = new HashMap<>();
        authenticator = new DigestAuthenticator(provider, messageMap);
        props = new DigestHttpAuthProperties();
        props.setRealm(REALM);
        props.setOpaque(OPAQUE);
        props.setCredentialsMap(Collections.singletonMap(USER, PASSWORD));
        doReturn(props).when(provider).getProperties();
    }

    private static RequestInfo request(String uri, String authorization, byte[] entity) {
        Map<String, List<String>> headers = new HashMap<>();
        if (authorization != null) {
            headers.put("Authorization", Collections.singletonList(authorization));
        }
        return new RequestInfo("127.0.0.1", 50000, "127.0.0.1", 8080, "HTTP/1.1", "POST", uri, headers, new HashMap<>(), () -> entity);
    }

    private static String md5(String... parts) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(String.join(":", parts).getBytes(StandardCharsets.ISO_8859_1));
        return Hex.encodeHexString(md.digest());
    }

    private static String directive(AuthenticationResult result, String name) {
        String header = result.getResponseHeaders().get("WWW-Authenticate").get(0);
        Matcher matcher = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(header);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String challengeNonce() {
        AuthenticationResult challenge = authenticator.authenticate(request(URI, null, null));
        assertEquals(AuthStatus.CHALLENGED, challenge.getStatus());
        return directive(challenge, "nonce");
    }

    private static String authorization(String username, String realm, String nonce, String uri, String response, String opaque, String extra) {
        return "Digest username=\"" + username + "\", realm=\"" + realm + "\", nonce=\"" + nonce + "\", uri=\"" + uri + "\", response=\"" + response + "\", opaque=\"" + opaque + "\"" + (extra == null ? "" : ", " + extra);
    }

    private AuthenticationResult authenticateWithQop(String nonce, String nc) throws Exception {
        String ha1 = md5(USER, REALM, PASSWORD);
        String ha2 = md5("POST", URI);
        String response = md5(ha1, nonce, nc, "cnonce1", "auth", ha2);
        return authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, response, OPAQUE, "qop=auth, nc=" + nc + ", cnonce=\"cnonce1\""), null));
    }

    @Test
    public void testChallengeWithoutAuthorizationHeader() {
        AuthenticationResult result = authenticator.authenticate(request(URI + "?a=b", null, null));

        assertEquals(AuthStatus.CHALLENGED, result.getStatus());
        assertTrue(result.getResponseHeaders().get("WWW-Authenticate").get(0).startsWith("Digest "));
        assertEquals(REALM, directive(result, "realm"));
        assertEquals(URI, directive(result, "domain"));
        assertEquals("MD5,MD5-sess", directive(result, "algorithm"));
        assertEquals("auth,auth-int", directive(result, "qop"));
        assertEquals(OPAQUE, directive(result, "opaque"));
        assertFalse(directive(result, "nonce").isEmpty());
        assertEquals(null, directive(result, "stale"));
    }

    @Test
    public void testChallengeOmitsEmptyQopAndOpaque() {
        props.setQopModes(EnumSet.noneOf(QOPMode.class));
        props.setOpaque("");
        AuthenticationResult result = authenticator.authenticate(request(URI, null, null));

        assertEquals(null, directive(result, "qop"));
        assertEquals(null, directive(result, "opaque"));
    }

    @Test
    public void testSuccessWithoutQop() throws Exception {
        String nonce = challengeNonce();
        String response = md5(md5(USER, REALM, PASSWORD), nonce, md5("POST", URI));

        AuthenticationResult result = authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, response, OPAQUE, null), null));

        assertEquals(AuthStatus.SUCCESS, result.getStatus());
        assertEquals(USER, result.getUsername());
        assertEquals(REALM, result.getRealm());
    }

    @Test
    public void testSuccessWithQopAuth() throws Exception {
        assertEquals(AuthStatus.SUCCESS, authenticateWithQop(challengeNonce(), "00000001").getStatus());
    }

    @Test
    public void testSuccessWithMd5Sess() throws Exception {
        String nonce = challengeNonce();
        String ha1 = md5(md5(USER, REALM, PASSWORD), nonce, "cnonce1");
        String response = md5(ha1, nonce, "00000001", "cnonce1", "auth", md5("POST", URI));
        String extra = "algorithm=MD5-sess, qop=auth, nc=00000001, cnonce=\"cnonce1\"";

        AuthenticationResult result = authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, response, OPAQUE, extra), null));

        assertEquals(AuthStatus.SUCCESS, result.getStatus());
    }

    @Test
    public void testSuccessWithQopAuthInt() throws Exception {
        String nonce = challengeNonce();
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        String ha2 = md5("POST", URI, md5("hello"));
        String response = md5(md5(USER, REALM, PASSWORD), nonce, "00000001", "cnonce1", "auth-int", ha2);
        String extra = "qop=auth-int, nc=00000001, cnonce=\"cnonce1\"";

        AuthenticationResult result = authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, response, OPAQUE, extra), body));

        assertEquals(AuthStatus.SUCCESS, result.getStatus());
    }

    @Test
    public void testEmptyUriMatchesRoot() throws Exception {
        String nonce = challengeNonce();
        String response = md5(md5(USER, REALM, PASSWORD), nonce, md5("POST", ""));

        AuthenticationResult result = authenticator.authenticate(request("/", authorization(USER, REALM, nonce, "", response, OPAQUE, null), null));

        assertEquals(AuthStatus.SUCCESS, result.getStatus());
    }

    @Test
    public void testWrongPasswordIsChallengedWithoutStale() throws Exception {
        String nonce = challengeNonce();
        String response = md5(md5(USER, REALM, "wrong"), nonce, md5("POST", URI));

        AuthenticationResult result = authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, response, OPAQUE, null), null));

        assertEquals(AuthStatus.CHALLENGED, result.getStatus());
        assertEquals(null, directive(result, "stale"));
    }

    @Test
    public void testUnknownNonceWithValidDigestIsStale() throws Exception {
        String nonce = "unknown-nonce";
        String response = md5(md5(USER, REALM, PASSWORD), nonce, md5("POST", URI));

        AuthenticationResult result = authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, response, "", null), null));

        assertEquals(AuthStatus.CHALLENGED, result.getStatus());
        assertEquals("true", directive(result, "stale"));
        assertFalse(nonce.equals(directive(result, "nonce")));
    }

    @Test
    public void testReplayedNonceCountIsRejected() throws Exception {
        String nonce = challengeNonce();

        assertEquals(AuthStatus.SUCCESS, authenticateWithQop(nonce, "00000002").getStatus());
        assertEquals(AuthStatus.CHALLENGED, authenticateWithQop(nonce, "00000002").getStatus());
        assertEquals(AuthStatus.CHALLENGED, authenticateWithQop(nonce, "00000001").getStatus());
        assertEquals(AuthStatus.SUCCESS, authenticateWithQop(nonce, "00000003").getStatus());
    }

    @Test
    public void testNonceCountAboveMaximumIsStale() throws Exception {
        AuthenticationResult result = authenticateWithQop(challengeNonce(), "00000401");

        assertEquals(AuthStatus.CHALLENGED, result.getStatus());
        assertEquals("true", directive(result, "stale"));
    }

    @Test
    public void testMalformedNonceCountIsRejected() throws Exception {
        AuthenticationResult result = authenticateWithQop(challengeNonce(), "zz");

        assertEquals(AuthStatus.CHALLENGED, result.getStatus());
        assertEquals(null, directive(result, "stale"));
    }

    @Test
    public void testNonceWithoutCountCanBeReused() throws Exception {
        String nonce = challengeNonce();
        String response = md5(md5(USER, REALM, PASSWORD), nonce, md5("POST", URI));
        String authorization = authorization(USER, REALM, nonce, URI, response, OPAQUE, null);

        assertEquals(AuthStatus.SUCCESS, authenticator.authenticate(request(URI, authorization, null)).getStatus());
        assertEquals(AuthStatus.SUCCESS, authenticator.authenticate(request(URI, authorization, null)).getStatus());
    }

    @Test
    public void testInvalidDirectivesAreChallenged() throws Exception {
        String nonce = challengeNonce();
        String ha1 = md5(USER, REALM, PASSWORD);
        String valid = md5(ha1, nonce, md5("POST", URI));

        String[] authorizations = new String[] {
                // missing username, realm, uri, response
                "Digest realm=\"" + REALM + "\", nonce=\"" + nonce + "\", uri=\"" + URI + "\", response=\"" + valid + "\"",
                "Digest username=\"" + USER + "\", nonce=\"" + nonce + "\", uri=\"" + URI + "\", response=\"" + valid + "\"",
                "Digest username=\"" + USER + "\", realm=\"" + REALM + "\", nonce=\"" + nonce + "\", response=\"" + valid + "\"",
                "Digest username=\"" + USER + "\", realm=\"" + REALM + "\", nonce=\"" + nonce + "\", uri=\"" + URI + "\"",
                // mismatched realm, uri, opaque
                authorization(USER, "Other Realm", nonce, URI, valid, OPAQUE, null),
                authorization(USER, REALM, nonce, "/other", valid, OPAQUE, null),
                authorization(USER, REALM, nonce, URI, valid, "other-opaque", null),
                // unknown user
                authorization("bob", REALM, nonce, URI, valid, OPAQUE, null),
                // unsupported algorithm and qop
                authorization(USER, REALM, nonce, URI, valid, OPAQUE, "algorithm=SHA-256"),
                authorization(USER, REALM, nonce, URI, valid, OPAQUE, "qop=bogus, nc=00000001, cnonce=\"c\""),
                // qop requires nc and cnonce
                authorization(USER, REALM, nonce, URI, valid, OPAQUE, "qop=auth, cnonce=\"c\""),
                authorization(USER, REALM, nonce, URI, valid, OPAQUE, "qop=auth, nc=00000005"),
                // MD5-sess requires cnonce
                authorization(USER, REALM, nonce, URI, valid, OPAQUE, "algorithm=MD5-sess"),
                // no nonce at all
                "Digest username=\"" + USER + "\", realm=\"" + REALM + "\", uri=\"" + URI + "\", response=\"" + valid + "\""
        };

        for (String authorization : authorizations) {
            AuthenticationResult result = authenticator.authenticate(request(URI, authorization, null));
            assertEquals(authorization, AuthStatus.CHALLENGED, result.getStatus());
            assertEquals(authorization, null, directive(result, "stale"));
        }

        // The nonce is still usable after all of the failed attempts
        assertEquals(AuthStatus.SUCCESS, authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, valid, OPAQUE, null), null)).getStatus());
    }

    @Test
    public void testDisabledAlgorithmAndQopAreRejected() throws Exception {
        props.setAlgorithms(EnumSet.of(Algorithm.MD5_SESS));
        props.setQopModes(EnumSet.of(QOPMode.AUTH_INT));
        String nonce = challengeNonce();
        String ha1 = md5(USER, REALM, PASSWORD);

        String md5Response = md5(ha1, nonce, md5("POST", URI));
        AuthenticationResult result = authenticator.authenticate(request(URI, authorization(USER, REALM, nonce, URI, md5Response, OPAQUE, "algorithm=MD5"), null));
        assertEquals(AuthStatus.CHALLENGED, result.getStatus());

        assertEquals(AuthStatus.CHALLENGED, authenticateWithQop(nonce, "00000001").getStatus());
    }

    @Test
    public void testGetCredentailsFromMap() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("user", "name");
        credentials.put("pass", "123");
        props.setCredentialsMap(credentials);

        Map<String, String> result = authenticator.getCredentials(props);
        assertEquals(credentials, result);
    }

    @Test
    public void testGetCredentailsFromVariable() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("user", "name");
        credentials.put("pass", "123");
        messageMap.map.put("myVar", credentials);
        props.setCredentialsVariable("myVar");
        props.setUseCredentialsVariable(true);

        Map<String, String> result = authenticator.getCredentials(props);
        assertEquals(credentials, result);
    }

    @Test
    public void testGetCredentialsFromVariableWithNonStringValues() {
        Map<Object, Object> credentials = new HashMap<>();
        credentials.put("user", "name");
        credentials.put("pass", "123");
        credentials.put("numValue", 1);
        credentials.put(4, 4);
        messageMap.map.put("myVar", credentials);
        props.setCredentialsVariable("myVar");
        props.setUseCredentialsVariable(true);

        HashMap<String, String> expected = new HashMap<>();
        expected.put("user", "name");
        expected.put("pass", "123");
        expected.put("numValue", "1");
        expected.put("4", "4");
        Map<String, String> result = authenticator.getCredentials(props);
        assertEquals(expected, result);
    }

    @Test
    public void testGetCredentialsFromMapWhenBothMapAndVariableAreSet() {
        Map<Object, Object> credentialsFromVar = new HashMap<>();
        credentialsFromVar.put("user", "name");
        credentialsFromVar.put("pass", "123");
        messageMap.map.put("myVar", credentialsFromVar);
        props.setCredentialsVariable("myVar");
        Map<String, String> credentialsMap = new HashMap<>();
        credentialsMap.put("user", "name");
        credentialsMap.put("pass", "123");
        props.setCredentialsMap(credentialsMap);
        props.setUseCredentialsVariable(false);

        Map<String, String> result = authenticator.getCredentials(props);
        assertEquals(credentialsMap, result);
    }

    @Test
    public void testGetCredentialsFromVariableWhenBothMapAndVariableAreSet() {
        Map<Object, Object> credentialsFromVar = new HashMap<>();
        credentialsFromVar.put("user", "name");
        credentialsFromVar.put("pass", "123");
        messageMap.map.put("myVar", credentialsFromVar);
        props.setCredentialsVariable("myVar");
        Map<String, String> credentialsMap = new HashMap<>();
        credentialsMap.put("user", "name");
        credentialsMap.put("pass", "123");
        props.setCredentialsMap(credentialsMap);
        props.setUseCredentialsVariable(false);

        Map<String, String> result = authenticator.getCredentials(props);
        assertEquals(credentialsFromVar, result);
    }

    @Test
    public void testGetEmptyMapWhenCredentialsVariableDoesNotExist() {
        props.setCredentialsVariable("doesn't exist");
        props.setUseCredentialsVariable(true);
        Map<String, String> result = authenticator.getCredentials(props);
        assertTrue(result.isEmpty());
    }

    class CustomMessageMap extends MessageMaps {
        protected Map<Object, Object> map;

        public CustomMessageMap(Map<Object, Object> map) {
            this.map = map;
        }

        public CustomMessageMap() {}

        @Override
        public Object get(String key, ConnectorMessage connectorMessage, boolean includeResponseMap) {
            return map.get(key);
        }
    }
}