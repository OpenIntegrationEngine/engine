// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.commons.encryption.Digester;
import com.mirth.connect.donkey.model.event.Event;
import com.mirth.connect.model.Credentials;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.LoginStrike;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.User;
import com.mirth.connect.server.util.SqlConfig;

/**
 * Covers what a failed login tells the caller, and the event that tells an administrator. A locked
 * account used to name itself and report its remaining lock time, and a wrong password reported the
 * attempts left, which let an unauthenticated caller tell a real username from a made-up one.
 *
 * These assert on the returned LoginStatus and the dispatched events only. Whether the cases take the
 * same amount of time is a separate property that is measured against a running server, not asserted
 * here.
 */
public class DefaultUserControllerAuthorizeUserTest {

    private static final String LOCKED_USERNAME = "lockeduser";
    private static final String ACTIVE_USERNAME = "activeuser";
    private static final String LAST_ATTEMPT_USERNAME = "lastattemptuser";
    private static final String NO_PASSWORD_USERNAME = "nopassworduser";
    private static final String PRE22_USERNAME = "pre22user";
    private static final String PRE22_PASSWORD = "pre22password";
    private static final String UNKNOWN_USERNAME = "nosuchuser";
    private static final String WRONG_PASSWORD = "wrongpassword";
    private static final String RIGHT_PASSWORD = "rightpassword";
    private static final String STORED_HASH = "stored hash";
    private static final String GENERIC_MESSAGE = "Login failed.";

    private static ControllerFactory controllerFactory;
    private static ConfigurationController configurationController;
    private static ExtensionController extensionController;
    private static UserController userController;
    private static EventController eventController;
    private static SqlSessionManager readOnlySqlSessionManager;

    private PasswordRequirements passwordRequirements;
    private Digester digester;
    private DefaultUserController controller;

    @BeforeClass
    public static void setupBeforeClass() {
        controllerFactory = mock(ControllerFactory.class);
        configurationController = mock(ConfigurationController.class);
        extensionController = mock(ExtensionController.class);
        userController = mock(UserController.class);
        eventController = mock(EventController.class);

        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);
        when(controllerFactory.createUserController()).thenReturn(userController);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        /*
         * authorizeUser takes a StatementLock, and StatementLock asks DatabaseUtil whether the
         * vacuum statement is mapped, which builds a real SqlConfig and its connection pool. Report
         * the statement as absent so the lock is a no-op and nothing tries to reach a database.
         */
        SqlConfig sqlConfig = mock(SqlConfig.class);
        SqlSessionManager sqlSessionManager = mock(SqlSessionManager.class);
        Configuration mybatisConfiguration = mock(Configuration.class);
        when(sqlConfig.getSqlSessionManager()).thenReturn(sqlSessionManager);
        when(sqlSessionManager.getConfiguration()).thenReturn(mybatisConfiguration);
        when(mybatisConfiguration.getMappedStatement(ArgumentMatchers.anyString()))
                .thenThrow(new IllegalArgumentException("statement not mapped in this test"));

        // The stored credentials for a user that gets as far as the password check
        readOnlySqlSessionManager = mock(SqlSessionManager.class);
        when(sqlConfig.getReadOnlySqlSessionManager()).thenReturn(readOnlySqlSessionManager);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                requestStaticInjection(SqlConfig.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
                bind(SqlConfig.class).toInstance(sqlConfig);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @Before
    public void setUp() throws Exception {
        reset(configurationController, extensionController, userController, eventController, readOnlySqlSessionManager);

        // No authorization or MFA plugin, so the controller performs authentication itself
        when(extensionController.getAuthorizationPlugin()).thenReturn(null);
        when(extensionController.getMultiFactorAuthenticationPlugin()).thenReturn(null);

        passwordRequirements = new PasswordRequirements();
        passwordRequirements.setRetryLimit(3);
        passwordRequirements.setLockoutPeriod(1);
        when(configurationController.getPasswordRequirements()).thenReturn(passwordRequirements);
        digester = mock(Digester.class);
        when(configurationController.getDigester()).thenReturn(digester);

        // A stored hash that isn't pre-2.2, checked by the mock digester, which matches only where a test says so
        Credentials credentials = new Credentials();
        credentials.setPassword(STORED_HASH);
        when(readOnlySqlSessionManager.selectOne(ArgumentMatchers.eq("User.getLatestUserCredentials"), ArgumentMatchers.any())).thenReturn(credentials);
        // No row for an unknown username's lookup (no user has id -1) or for a user with no password
        when(readOnlySqlSessionManager.selectOne(ArgumentMatchers.eq("User.getLatestUserCredentials"), ArgumentMatchers.eq(-1))).thenReturn(null);
        when(readOnlySqlSessionManager.selectOne(ArgumentMatchers.eq("User.getLatestUserCredentials"), ArgumentMatchers.eq(4))).thenReturn(null);
        Credentials pre22Credentials = new Credentials();
        pre22Credentials.setPassword(pre22Hash(PRE22_PASSWORD));
        when(readOnlySqlSessionManager.selectOne(ArgumentMatchers.eq("User.getLatestUserCredentials"), ArgumentMatchers.eq(5))).thenReturn(pre22Credentials);

        controller = spy(new DefaultUserController());
        doReturn(user(1, LOCKED_USERNAME, passwordRequirements.getRetryLimit() + 1)).when(controller).getUser(null, LOCKED_USERNAME);
        doReturn(user(2, ACTIVE_USERNAME, 0)).when(controller).getUser(null, ACTIVE_USERNAME);
        doReturn(user(3, LAST_ATTEMPT_USERNAME, passwordRequirements.getRetryLimit())).when(controller).getUser(null, LAST_ATTEMPT_USERNAME);
        doReturn(user(4, NO_PASSWORD_USERNAME, 0)).when(controller).getUser(null, NO_PASSWORD_USERNAME);
        doReturn(user(5, PRE22_USERNAME, 0)).when(controller).getUser(null, PRE22_USERNAME);
        doReturn(null).when(controller).checkOrUpdateUserPassword(5, PRE22_PASSWORD);
        doReturn(null).when(controller).getUser(null, UNKNOWN_USERNAME);

        // The database adds one strike to whatever the user had
        when(userController.incrementStrikes(2)).thenReturn(new LoginStrike(1, Calendar.getInstance()));
        when(userController.incrementStrikes(3)).thenReturn(new LoginStrike(passwordRequirements.getRetryLimit() + 1, Calendar.getInstance()));
    }

    @Test
    public void lockedAccountGetsGenericFailure() throws Exception {
        LoginStatus status = controller.authorizeUser(LOCKED_USERNAME, WRONG_PASSWORD, null);

        assertEquals(LoginStatus.Status.FAIL, status.getStatus());
        assertEquals(GENERIC_MESSAGE, status.getMessage());
        verify(eventController, never()).dispatchEvent(any(Event.class));
    }

    /**
     * The #240 regression test. Without the fix the locked account answers FAIL_LOCKED_OUT and names
     * itself while the unknown username answers FAIL, so the two responses identify a real account.
     */
    @Test
    public void lockedAccountIsIndistinguishableFromUnknownUsername() throws Exception {
        LoginStatus locked = controller.authorizeUser(LOCKED_USERNAME, WRONG_PASSWORD, null);
        LoginStatus unknown = controller.authorizeUser(UNKNOWN_USERNAME, WRONG_PASSWORD, null);

        assertEquals(unknown.getStatus(), locked.getStatus());
        assertEquals(unknown.getMessage(), locked.getMessage());
    }

    /**
     * Without the fix a wrong password for a real account reported how many attempts were left
     * before the lock, while an unknown username got the bare failure.
     */
    @Test
    public void wrongPasswordIsIndistinguishableFromUnknownUsername() throws Exception {
        LoginStatus wrongPassword = controller.authorizeUser(ACTIVE_USERNAME, WRONG_PASSWORD, null);
        LoginStatus unknown = controller.authorizeUser(UNKNOWN_USERNAME, WRONG_PASSWORD, null);

        assertEquals(unknown.getStatus(), wrongPassword.getStatus());
        assertEquals(unknown.getMessage(), wrongPassword.getMessage());
        verify(eventController, never()).dispatchEvent(any(Event.class));
    }

    /**
     * The failed login that locks the account dispatches one event naming it. Later attempts are
     * rejected before a strike is counted, so they dispatch nothing more.
     */
    @Test
    public void lockingAttemptDispatchesOneLockedEvent() throws Exception {
        LoginStatus locking = controller.authorizeUser(LAST_ATTEMPT_USERNAME, WRONG_PASSWORD, null);
        LoginStatus afterwards = controller.authorizeUser(LAST_ATTEMPT_USERNAME, WRONG_PASSWORD, null);

        assertEquals(LoginStatus.Status.FAIL, locking.getStatus());
        assertEquals(GENERIC_MESSAGE, locking.getMessage());
        assertEquals(GENERIC_MESSAGE, afterwards.getMessage());

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventController, times(1)).dispatchEvent(captor.capture());
        ServerEvent event = (ServerEvent) captor.getValue();
        assertEquals("User account locked", event.getName());
        assertEquals(ServerEvent.Level.WARNING, event.getLevel());
        assertEquals(ServerEvent.Outcome.FAILURE, event.getOutcome());
        assertEquals(LAST_ATTEMPT_USERNAME, event.getAttributes().get("username"));
    }

    /**
     * The lock wins over a correct password: the attempt fails like any other, and the strikes are
     * neither reset nor added to, so the lock isn't lifted or extended.
     */
    @Test
    public void lockedAccountWithRightPasswordStillFails() throws Exception {
        when(digester.matches(RIGHT_PASSWORD, STORED_HASH)).thenReturn(true);

        LoginStatus status = controller.authorizeUser(LOCKED_USERNAME, RIGHT_PASSWORD, null);

        assertEquals(LoginStatus.Status.FAIL, status.getStatus());
        assertEquals(GENERIC_MESSAGE, status.getMessage());
        verify(userController, never()).resetStrikes(any());
        verify(userController, never()).incrementStrikes(any());
    }

    @Test
    public void rightPasswordLogsIn() throws Exception {
        when(digester.matches(RIGHT_PASSWORD, STORED_HASH)).thenReturn(true);

        LoginStatus status = controller.authorizeUser(ACTIVE_USERNAME, RIGHT_PASSWORD, null);

        assertEquals(LoginStatus.Status.SUCCESS, status.getStatus());
        verify(userController, times(1)).resetStrikes(2);
    }

    /**
     * Every kind of failed attempt does the same work, one credentials query and one full digest, so
     * the time taken to reject it doesn't say which kind it was. The digest's cost is real only on a
     * running server; here the mock digester just counts the calls.
     */
    @Test
    public void everyFailedAttemptRunsOneQueryAndOneDigest() throws Exception {
        String[] usernames = { UNKNOWN_USERNAME, LOCKED_USERNAME, ACTIVE_USERNAME, NO_PASSWORD_USERNAME, PRE22_USERNAME };

        for (String username : usernames) {
            clearInvocations(digester, readOnlySqlSessionManager);

            LoginStatus status = controller.authorizeUser(username, WRONG_PASSWORD, null);

            assertEquals(username, GENERIC_MESSAGE, status.getMessage());
            verify(readOnlySqlSessionManager, times(1)).selectOne(eq("User.getLatestUserCredentials"), any());
            verify(digester, times(1)).matches(eq(WRONG_PASSWORD), any());
        }
    }

    /**
     * A pre-2.2 hash is still checked with its own algorithm, still upgraded on a successful login,
     * and still pays for the one full digest.
     */
    @Test
    public void pre22HashStillLogsIn() throws Exception {
        LoginStatus status = controller.authorizeUser(PRE22_USERNAME, PRE22_PASSWORD, null);

        assertEquals(LoginStatus.Status.SUCCESS, status.getStatus());
        verify(controller, times(1)).checkOrUpdateUserPassword(5, PRE22_PASSWORD);
        verify(digester, times(1)).matches(eq(PRE22_PASSWORD), any());
    }

    /**
     * The format Pre22PasswordChecker reads: SALT_, then base64 of an 8-byte salt, then base64 of
     * SHA-1 over the salt and the password.
     */
    private String pre22Hash(String password) {
        byte[] salt = "8bytesal".getBytes();
        return "SALT_" + Base64.encodeBase64String(salt) + Base64.encodeBase64String(DigestUtils.sha(ArrayUtils.addAll(salt, password.getBytes())));
    }

    /**
     * A user with the given strikes, the last one just now, so a count past the retry limit is
     * still inside the lockout period. LoginRequirementsChecker reads both values straight off the
     * User.
     */
    private User user(int id, String username, int strikeCount) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setStrikeCount(strikeCount);
        user.setLastStrikeTime(Calendar.getInstance());
        return user;
    }
}
