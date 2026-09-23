// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Open Integration Engine

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Calendar;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.commons.encryption.Digester;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.User;
import com.mirth.connect.server.util.SqlConfig;

/**
 * Covers what a failed login tells the caller. A locked account used to name itself and report its
 * remaining lock time, which let an unauthenticated caller tell a real username from a made-up one.
 *
 * These assert on the returned LoginStatus only. Whether the two cases take the same amount of time
 * is a separate property that is measured against a running server, not asserted here.
 */
public class DefaultUserControllerAuthorizeUserTest {

    private static final String LOCKED_USERNAME = "lockeduser";
    private static final String UNKNOWN_USERNAME = "nosuchuser";
    private static final String WRONG_PASSWORD = "wrongpassword";
    private static final String GENERIC_MESSAGE = "Incorrect username or password.";

    private static ControllerFactory controllerFactory;
    private static ConfigurationController configurationController;
    private static ExtensionController extensionController;
    private static UserController userController;

    private PasswordRequirements passwordRequirements;
    private DefaultUserController controller;

    @BeforeClass
    public static void setupBeforeClass() {
        controllerFactory = mock(ControllerFactory.class);
        configurationController = mock(ConfigurationController.class);
        extensionController = mock(ExtensionController.class);
        userController = mock(UserController.class);

        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);
        when(controllerFactory.createUserController()).thenReturn(userController);

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
        reset(configurationController, extensionController, userController);

        // No authorization or MFA plugin, so the controller performs authentication itself
        when(extensionController.getAuthorizationPlugin()).thenReturn(null);
        when(extensionController.getMultiFactorAuthenticationPlugin()).thenReturn(null);

        passwordRequirements = new PasswordRequirements();
        passwordRequirements.setRetryLimit(3);
        passwordRequirements.setLockoutPeriod(1);
        when(configurationController.getPasswordRequirements()).thenReturn(passwordRequirements);
        when(configurationController.getDigester()).thenReturn(mock(Digester.class));

        controller = spy(new DefaultUserController());
        doReturn(lockedUser()).when(controller).getUser(null, LOCKED_USERNAME);
        doReturn(null).when(controller).getUser(null, UNKNOWN_USERNAME);
    }

    @Test
    public void lockedAccountIsGenericByDefault() throws Exception {
        LoginStatus status = controller.authorizeUser(LOCKED_USERNAME, WRONG_PASSWORD, null);

        assertEquals(LoginStatus.Status.FAIL, status.getStatus());
        assertEquals(GENERIC_MESSAGE, status.getMessage());
    }

    @Test
    public void lockedAccountReportsDetailWhenAllowed() throws Exception {
        passwordRequirements.setAllowDetailedAuthErrors(true);

        LoginStatus status = controller.authorizeUser(LOCKED_USERNAME, WRONG_PASSWORD, null);

        assertEquals(LoginStatus.Status.FAIL_LOCKED_OUT, status.getStatus());
        assertTrue("expected the message to name the account, but was: " + status.getMessage(),
                status.getMessage().contains(LOCKED_USERNAME));
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
     * Strikes past the retry limit with the last one just now, so the lockout period has not
     * elapsed. LoginRequirementsChecker reads both values straight off the User.
     */
    private User lockedUser() {
        User user = new User();
        user.setId(1);
        user.setUsername(LOCKED_USERNAME);
        user.setStrikeCount(passwordRequirements.getRetryLimit() + 1);
        user.setLastStrikeTime(Calendar.getInstance());
        return user;
    }
}
