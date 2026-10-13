// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinNT;
import com.sun.security.auth.module.NTSystem;

public class FilePermissionUtilTest {

    // well-known SIDs, which unlike account names are the same in every Windows language
    private static final String LOCAL_SYSTEM_SID = "S-1-5-18";
    private static final String ADMINISTRATORS_SID = "S-1-5-32-544";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testCreatesMissingParentDirectories() throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve("nested/keystore.p12");

        FilePermissionUtil.createOwnerOnlyFile(path);

        assertTrue(Files.exists(path));
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testThrowsIfFileExists() throws Exception {
        Path path = temporaryFolder.newFile("keystore.p12").toPath();

        FilePermissionUtil.createOwnerOnlyFile(path);
    }

    @Test
    public void testPosixPermissions() throws Exception {
        assumeFalse("Test only applies on POSIX filesystems", Platform.isWindows());
        Path path = temporaryFolder.getRoot().toPath().resolve("keystore.p12");

        FilePermissionUtil.createOwnerOnlyFile(path);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
    }

    @Test
    public void testWindowsPermissions() throws Exception {
        assumeTrue("Test only applies on windows", Platform.isWindows());
        Path path = temporaryFolder.getRoot().toPath().resolve("keystore.p12");

        FilePermissionUtil.createOwnerOnlyFile(path);

        Map<String, Integer> grants = new HashMap<String, Integer>();
        for (WinNT.ACCESS_ACEStructure ace : Advapi32Util.getFileSecurity(path.toAbsolutePath().toString(), false)) {
            assertEquals("ACE type for " + ace.getSidString(), WinNT.ACCESS_ALLOWED_ACE_TYPE, ace.AceType);
            assertEquals("ACE for " + ace.getSidString() + " is inherited", 0, ace.AceFlags & WinNT.INHERITED_ACE);
            grants.put(ace.getSidString(), ace.Mask);
        }

        // when running as LocalSystem, the current user and LocalSystem are the same entry
        Map<String, Integer> expected = new HashMap<String, Integer>();
        expected.put(new NTSystem().getUserSID(), WinNT.FILE_ALL_ACCESS);
        expected.put(LOCAL_SYSTEM_SID, WinNT.FILE_ALL_ACCESS);
        expected.put(ADMINISTRATORS_SID, WinNT.FILE_ALL_ACCESS);
        assertEquals(expected, grants);
    }
}
