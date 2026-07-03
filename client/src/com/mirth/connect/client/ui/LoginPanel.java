// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan
// SPDX-FileCopyrightText: 2026 Tony Germano

package com.mirth.connect.client.ui;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.model.LoginStatus;

/**
 * Abstraction for the login panel so that alternative UI toolkit implementations
 * (e.g.&nbsp;JavaFX) can replace the default Swing-based panel.
 *
 * <h2>Discovery</h2>
 * Implementations are discovered at runtime via {@link java.util.ServiceLoader}.
 * To provide a custom login panel, add a
 * {@code META-INF/services/com.mirth.connect.client.ui.LoginPanel} file whose
 * single line is the fully-qualified name of the implementing class.
 * The custom class must have a public no-arg constructor (required by
 * {@link java.util.ServiceLoader}).
 * If multiple custom implementations are found, the first one discovered
 * is used (ordering depends on the classloader).
 * If no custom implementation is found on the classpath,
 * {@link DefaultLoginPanel} (the built-in Swing implementation) is used as
 * a hardcoded fallback.  {@code DefaultLoginPanel} itself is <em>not</em>
 * registered as an SPI provider and uses a package-private constructor
 * (it is instantiated directly by {@link LoginPanelFactory}).
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>The panel is instantiated once by {@link LoginPanelFactory}.</li>
 *   <li>{@link #initialize} is called each time a login screen is needed
 *       (initial startup and after logout / connection errors).
 *       If {@code user} and {@code pass} are both non-empty the implementation
 *       should attempt an automatic login.</li>
 *   <li>During the boot sequence after a successful login,
 *       {@link #setStatus} is called repeatedly to report loading progress.</li>
 *   <li>Once the main application frame is ready, {@link #setVisible(boolean) setVisible(false)}
 *       is called to hide the panel.</li>
 * </ol>
 *
 * <h2>Multi-Factor Authentication</h2>
 * Existing {@link com.mirth.connect.plugins.MultiFactorAuthenticationClientPlugin}
 * implementations expect a {@link java.awt.Window} to be passed as their parent
 * for dialog display.  A Swing-based login panel (such as {@link DefaultLoginPanel})
 * can pass itself directly.  Non-Swing implementations are free to define their
 * own MFA workflow instead of using the plugin API.
 *
 * <h2>Threading</h2>
 * All methods on this interface ({@link #initialize}, {@link #setStatus},
 * {@link #setVisible}, {@link #isVisible}, and {@link #showLoginNotification})
 * may be called from any thread.  Implementations must handle any necessary
 * thread marshalling internally (e.g.&nbsp;posting to the UI thread).
 * <p>
 * The thread from which the {@link LoginSuccessHandler} is invoked is
 * determined by the implementation.  {@link DefaultLoginPanel} calls the
 * handler from a background thread.
 * The handler (and anything it calls) must not assume it is on a UI thread.
 *
 * @see LoginPanelFactory
 * @see DefaultLoginPanel
 */
public interface LoginPanel {

    /**
     * Callback invoked by the login panel after the server confirms a
     * successful authentication.
     *
     * @see #initialize
     */
    @FunctionalInterface
    interface LoginSuccessHandler {
        /**
         * Handle a successful login.
         *
         * @param client      the authenticated {@link Client} connection
         * @param loginStatus the status returned by the server
         * @param userName    the username that was used to log in
         * @return {@code true} if the application booted successfully;
         *         {@code false} if the login flow should restart
         *         (e.g.&nbsp;the user cancelled a first-login dialog)
         * @throws ClientException if a server communication error occurs
         */
        boolean handle(Client client, LoginStatus loginStatus, String userName) throws ClientException;
    }

    /**
     * Initialize (or re-initialize) the login UI and make it visible.
     * <p>
     * If both {@code user} and {@code pass} are non-empty, the implementation
     * should start the login attempt automatically without waiting for the
     * user to click a button.
     * <p>
     * If the panel is already visible, the call should be ignored to
     * prevent duplicate windows.
     *
     * @param mirthServer the server URL to display / connect to
     * @param version     the client version string, used in the window title
     * @param user        pre-filled username (may be empty)
     * @param pass        pre-filled password (may be empty)
     * @param onSuccess   callback to invoke after a successful authentication
     */
    void initialize(String mirthServer, String version, String user, String pass, LoginSuccessHandler onSuccess);

    /**
     * Update the status text shown on the login UI.
     * <p>
     * Called repeatedly during the post-login boot sequence to report
     * loading progress (e.g.&nbsp;"Loading extensions&hellip;").
     * Implementations must be safe to call from any thread.
     *
     * @param status the progress message to display
     */
    void setStatus(String status);

    /**
     * Show or hide the login panel.
     * <p>
     * After the main application frame is ready, {@code setVisible(false)}
     * is called to dismiss the login UI.  Implementations must be safe to
     * call from any thread.
     *
     * @param visible {@code true} to show, {@code false} to hide
     */
    void setVisible(boolean visible);

    /**
     * Returns whether the login panel is currently visible.
     *
     * @return {@code true} if the panel is showing
     */
    boolean isVisible();

    /**
     * Display a modal login-notification banner and wait for the user to
     * accept or cancel.
     * <p>
     * This is called after a successful authentication when the server has
     * login notifications enabled.  If the user does not accept, the
     * login flow is aborted and restarted.
     *
     * @param title   the dialog title
     * @param message the notification body text
     * @return {@code true} if the user accepted the notification,
     *         {@code false} if they cancelled
     */
    boolean showLoginNotification(String title, String message);
}
