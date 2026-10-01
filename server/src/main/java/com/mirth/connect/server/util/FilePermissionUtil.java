package com.mirth.connect.server.util;

import java.io.File;
import java.io.IOException;
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

    /*
     * Creates a file that only the account the server runs as, and whoever administers the machine,
     * may read or write.
     */
    public static void createOwnerOnlyFile(File file) throws IOException {
        Path path = file.toPath();
        File parent = file.getParentFile();

        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        if (Platform.isWindows()) {
            WindowsFilePermissionUtil.createRestrictedFile(path);
        } else {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        }
    }
}
