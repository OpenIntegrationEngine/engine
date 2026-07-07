// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.model;

/** Administrative policy for a user preference */
public record AdminUserPreference(String value, boolean locked) {}
