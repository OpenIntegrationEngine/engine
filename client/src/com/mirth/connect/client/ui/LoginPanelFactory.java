// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025-2026 Open Integration Engine Contributors

package com.mirth.connect.client.ui;

import java.util.ServiceLoader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory for obtaining the application's {@link LoginPanel} implementation.
 * Uses {@link ServiceLoader} to discover custom implementations, falling back
 * to {@link DefaultLoginPanel} if none are found.
 */
public class LoginPanelFactory {

    private static final Logger logger = LogManager.getLogger(LoginPanelFactory.class);
    private static LoginPanel loginPanel = null;

    /**
     * Returns the singleton {@link LoginPanel} instance, discovering it via
     * {@link ServiceLoader} on first call.  If multiple custom implementations
     * are found, the first one discovered is used (ordering is classloader-dependent).
     * Falls back to {@link DefaultLoginPanel} if no custom implementation is
     * registered.  {@code DefaultLoginPanel} itself should not be registered
     * as an SPI provider — it is the hardcoded fallback.
     */
    public static synchronized LoginPanel getLoginPanel() {
        if (loginPanel == null) {
            loginPanel = ServiceLoader.load(LoginPanel.class)
                    .findFirst()
                    .orElseGet(DefaultLoginPanel::new);
            logger.info("Using LoginPanel: {}", loginPanel.getClass().getName());
        }
        return loginPanel;
    }
}
