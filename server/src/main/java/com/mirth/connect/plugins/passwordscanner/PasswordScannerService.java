// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.plugins.passwordscanner;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;

/**
 * Periodically runs the {@link PasswordScanner} on a background thread.
 */
public class PasswordScannerService implements ServicePlugin {

    public static final String PLUGINPOINT = "Password Scanner";

    private static final String INTERVAL_SECONDS = "intervalSeconds";
    private static final int DEFAULT_INTERVAL_SECONDS = (int) TimeUnit.DAYS.toSeconds(1);
    private static final String PASSWORDS = "passwords";

    /** Delay the first scan so it doesn't compete with the rest of server startup. */
    private static final long STARTUP_DELAY_SECONDS = 30;
    private static final long MIN_INTERVAL_SECONDS = 300;

    private Logger logger = LogManager.getLogger(this.getClass());
    private ScheduledExecutorService executor;
    private int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    private volatile Wordlist wordlist = Wordlist.DEFAULT;

    @Override
    public String getPluginPointName() {
        return PLUGINPOINT;
    }

    @Override
    public void init(Properties properties) {
        readProperties(properties);
    }

    @Override
    public synchronized void update(Properties properties) {
        stop();
        readProperties(properties);
        start();
    }

    @Override
    public synchronized void start() {
        if (executor != null) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, PLUGINPOINT);
                thread.setDaemon(true);
                // Scanning passwords is housekeeping; it should always lose to real work.
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });

        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                scan();
            }
        }, STARTUP_DELAY_SECONDS, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public Properties getDefaultProperties() {
        Properties properties = new Properties();
        properties.put(INTERVAL_SECONDS, Integer.toString(DEFAULT_INTERVAL_SECONDS));
        properties.put(PASSWORDS, Wordlist.DEFAULT.toString());
        return properties;
    }

    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        return new ExtensionPermission[0];
    }

    private synchronized void readProperties(Properties properties) {
        int seconds = NumberUtils.toInt(properties.getProperty(INTERVAL_SECONDS), DEFAULT_INTERVAL_SECONDS);
        if (seconds < MIN_INTERVAL_SECONDS) {
            logger.warn("Invalid {} value \"{}\".", INTERVAL_SECONDS, properties.getProperty(INTERVAL_SECONDS));
        } else {
            this.intervalSeconds = seconds;
        }

        Wordlist list = Wordlist.parse(properties.getProperty(PASSWORDS));
        if (!list.isEmpty()) {
            this.wordlist = list;
        }
    }

    /**
     * A scheduled task that throws is never run again, so nothing may escape here.
     */
    private void scan() {
        try {
            new PasswordScanner(wordlist).scan();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Password scan interrupted.");
        } catch (Throwable t) {
            logger.error("Error scanning for trivial user passwords.", t);
        }
    }
}
