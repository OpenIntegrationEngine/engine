// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Tony Germano <tony@germano.name>
// SPDX-Contributor: Original Author Dem Pilafian (Original work licensed under WTFPL)

package com.mirth.connect.client.ui;

import java.util.Arrays;

/**
 * Utility class to open a web page from a Swing application
 * in the user's default browser.
 * <p>
 * Supports: Mac OS X, Linux, Unix, Windows
 * <p>
 * Example usage:<br>
 * <code>&nbsp;&nbsp;
 *    String url = "https://dna-engine.org/";<br>&nbsp;&nbsp;
 *    BareBonesBrowserLaunch.openURL(url);</code>
 * <p>
 * Latest Version: <a href=https://centerkey.com/java/browser>
 * https://centerkey.com/java/browser</a>
 * <p>
 * Published: October 24, 2010
 * Modified: 2025
 * <p>
 *
 * @author Dem Pilafian
 * @version 3.2
 */
public class BareBonesBrowserLaunch {

    static final String[] browsers = { "xdg-open", "x-www-browser", "google-chrome",
            "firefox", "opera", "epiphany", "konqueror", "conkeror", "midori",
            "kazehakase", "mozilla" };

    /**
     * Open the specified web page in the user's default browser
     * 
     * @param url A web address (URL) of a web page (example:
     *            <code>"https://dna-engine.org/"</code>)
     */
    public static void openURL(String url) {
        try { // attempt to use Desktop library from JDK 1.6+
            Class<?> d = Class.forName("java.awt.Desktop");
            d.getDeclaredMethod("browse",
                    new Class<?>[] { java.net.URI.class }).invoke(
                            d.getDeclaredMethod("getDesktop").invoke(null),
                            new Object[] { java.net.URI.create(url) });
        } catch (Exception ignore) { // library not available or failed
            String osName = System.getProperty("os.name");
            try {
                if (osName.startsWith("Mac OS")) {
                    Class.forName("com.apple.eio.FileManager").getDeclaredMethod(
                            "openURL", new Class<?>[] { String.class }).invoke(null,
                                    new Object[] { url });
                } else if (osName.startsWith("Windows"))
                    Runtime.getRuntime().exec(new String[] {
                            "rundll32", "url.dll,FileProtocolHandler", url });
                else { // assume Unix or Linux
                    String browser = null;
                    for (String b : browsers)
                        if (browser == null
                                && Runtime.getRuntime().exec(new String[] { "which", b }).getInputStream().read() != -1)
                            Runtime.getRuntime().exec(new String[] { browser = b, url });
                    if (browser == null)
                        throw new Exception(Arrays.toString(browsers));
                }
            } catch (Exception e) {
                PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
            }
        }
    }
}
