// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.server.util;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import com.sun.jna.Platform;

public class FilePermissionUtil {

    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private FilePermissionUtil() {}

    /**
     * Creates a new file that only the account the server runs as, and whoever administers the
     * machine, may read or write. The permissions are applied as part of the create itself, so the
     * file is never briefly readable by anyone else.
     *
     * @param path The path of the file to create.
     * @throws FileAlreadyExistsException If a file of that name already exists.
     * @throws IOException If an I/O error occurs, or the permissions cannot be applied.
     */
    public static void createOwnerOnlyFile(Path path) throws FileAlreadyExistsException, IOException {
        Path parent = path.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Platform.isWindows()) {
            WindowsFilePermissionUtil.createRestrictedFile(path);
        } else {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        }
    }
}
