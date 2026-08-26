package com.mirth.connect.server.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class FilePermissionUtil {

    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private FilePermissionUtil() {}

    /*
     * Creates the file if it does not already exist, and restricts it so that only the user the
     * server runs as may read or write it. This is what protects files that hold key material in
     * place of a passphrase, so the file must never be created readable and then locked down
     * afterwards; on POSIX the permissions are applied as part of the create itself.
     */
    public static void createOwnerOnlyFile(File file) throws IOException {
        Path path = file.toPath();

        if (!Files.exists(path)) {
            File parent = file.getParentFile();

            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }

            if (isPosix(path)) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
                return;
            }

            Files.createFile(path);
        }

        restrictToOwner(path);
    }

    /*
     * Replaces the permissions on an existing file with owner read/write only. On Windows the
     * entire ACL is replaced with a single entry for the file's owner, which also detaches the file
     * from any permissions inherited from its parent directory.
     */
    private static void restrictToOwner(Path path) throws IOException {
        if (isPosix(path)) {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
            return;
        }

        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);

        if (aclView != null) {
            UserPrincipal owner = aclView.getOwner();
            // @formatter:off
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            // @formatter:on
            aclView.setAcl(Collections.singletonList(entry));
        }
    }

    private static boolean isPosix(Path path) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }
}
