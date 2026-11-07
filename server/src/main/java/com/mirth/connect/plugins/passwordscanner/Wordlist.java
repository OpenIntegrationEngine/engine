// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.plugins.passwordscanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * The passwords the scanner checks, held in a plugin property as a single comma-separated string.
 * Escape , and \ characters with a backslash. Leading and trailing whitespace is ignored.
 */
public class Wordlist {

    private static final char SEPARATOR = ',';
    private static final char ESCAPE = '\\';

    public static final Wordlist DEFAULT = new Wordlist(Arrays.asList(
        "admin", "password", "Password1", "changeme", "mirth", "letmein", 
        "welcome", "123456", "admin123", "password1"
    ));

    private List<String> passwords;

    public Wordlist(List<String> passwords) {
        this.passwords = Collections.unmodifiableList(new ArrayList<String>(passwords));
    }

    /** The passwords to check, in the order they were configured. */
    public List<String> getPasswords() {
        return passwords;
    }

    public boolean isEmpty() {
        return passwords.isEmpty();
    }

    public static Wordlist parse(String value) {
        List<String> passwords = new ArrayList<String>();
        StringBuilder password = new StringBuilder();
        /*
         * Index just past the last character that has to be kept. Trailing whitespace sits beyond
         * it and is dropped when the password is cut, unless it was escaped.
         */
        int end = 0;
        boolean escaped = false;

        for (char c : StringUtils.defaultString(value).toCharArray()) {
            if (escaped) {
                password.append(c);
                end = password.length();
                escaped = false;
            } else if (c == ESCAPE) {
                escaped = true;
            } else if (c == SEPARATOR) {
                addPassword(passwords, password, end);
                password.setLength(0);
                end = 0;
            } else if (Character.isWhitespace(c)) {
                // Leading whitespace never makes it into the password at all.
                if (password.length() > 0) {
                    password.append(c);
                }
            } else {
                password.append(c);
                end = password.length();
            }
        }

        if (escaped) {
            // A dangling escape at the very end can only have meant a literal backslash.
            password.append(ESCAPE);
            end = password.length();
        }

        addPassword(passwords, password, end);
        return new Wordlist(passwords);
    }

    private static void addPassword(List<String> passwords, StringBuilder password, int end) {
        if (end > 0) {
            passwords.add(password.substring(0, end));
        }
    }

    /** Renders a property value that {@link #parse} reads back as this word list. */
    @Override
    public String toString() {
        StringBuilder value = new StringBuilder();

        for (String password : passwords) {
            if (value.length() > 0) {
                value.append(SEPARATOR);
            }

            for (int i = 0; i < password.length(); i++) {
                char c = password.charAt(i);
                boolean edge = (i == 0 || i == password.length() - 1);

                // Interior whitespace survives parsing untouched, so only the edges need escaping.
                if (c == SEPARATOR || c == ESCAPE || (edge && Character.isWhitespace(c))) {
                    value.append(ESCAPE);
                }

                value.append(c);
            }
        }

        return value.toString();
    }
}
