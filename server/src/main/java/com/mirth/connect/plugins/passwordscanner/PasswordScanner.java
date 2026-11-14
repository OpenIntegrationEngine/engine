// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.plugins.passwordscanner;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Credentials;
import com.mirth.connect.model.User;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.UserController;
import com.mirth.connect.server.util.Pre22PasswordChecker;

/**
 * Checks each user's current password against a handful of trivially guessable passwords and logs a
 * warning for every match. The classic case is a stock admin/admin account.
 */
public class PasswordScanner {

    /** Pause between checks. Nothing here is urgent, so stay out of the way of real work. */
    private static final long PAUSE_MILLIS = 1000;

    private Logger logger = LogManager.getLogger(this.getClass());
    private UserController userController = ControllerFactory.getFactory().createUserController();
    private Wordlist wordlist;

    public PasswordScanner(Wordlist wordlist) {
        this.wordlist = wordlist;
    }

    public void scan() throws ControllerException, InterruptedException {
        for (User user : userController.getAllUsers()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            if (hasTrivialPassword(user)) {
                logger.warn("User \"{}\" has a trivially guessable password and should change it immediately.", user.getUsername());
            }
        }
    }

    private boolean hasTrivialPassword(User user) throws ControllerException, InterruptedException {
        List<Credentials> credentials = userController.getUserCredentials(user.getId());
        if (credentials.isEmpty()) {
            return false;
        }

        // Credentials are ordered newest first, so the first entry is the password in use.
        String hash = credentials.get(0).getPassword();

        // Check against the wordlist
        List<String> candidates = wordlist.getPasswords();
        for (String candidate : candidates) {
            if (matches(candidate, hash)) {
                return true;
            }
        }

        // Check against the username, unless it's already in the wordlist
        if (!candidates.contains(user.getUsername())) {
            return matches(user.getUsername(), hash);
        }
        
        return false;
    }
    
    private boolean matches(String plainPassword, String hash) {
        try {
            // Throttle to avoid heavy CPU load.  Hashing is expensive by design.
            Thread.sleep(PAUSE_MILLIS);

            if (Pre22PasswordChecker.isPre22Hash(hash)) {
                return Pre22PasswordChecker.checkPassword(plainPassword, hash);
            }

            return userController.checkPassword(plainPassword, hash);
        } catch (Exception e) {
            logger.debug("Unable to check a password against its hash.", e);
            return false;
        }
    }
}
