// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import org.junit.Assume;
import org.junit.Test;

public class Log4jMigrationsTest {

    @Test
    public void testMigrateLog4jRemovesDirLogs() throws Exception {
        File file = File.createTempFile("log4j2", ".properties");
        file.deleteOnExit();
        Files.writeString(file.toPath(), String.join(System.lineSeparator(),
                "rootLogger = ERROR,stdout,fout",
                "dir.logs   = logs",
                "property.log.dir = logs",
                "appender.rolling.fileName = ${log.dir}/mirth.log"), StandardCharsets.UTF_8);

        Log4jMigrations.migrateConfiguration(file);

        String fileContents = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertFalse(fileContents.contains("dir.logs"));
        assertTrue(fileContents.contains("property.log.dir = logs"));
        assertTrue(fileContents.contains("appender.rolling.fileName = ${log.dir}/mirth.log"));
    }

    @Test
    public void testMigrateLog4jDoesNotRewriteCleanFile() throws Exception {
        File file = File.createTempFile("log4j2-clean", ".properties");
        file.deleteOnExit();
        String contents = String.join(System.lineSeparator(),
                "rootLogger = ERROR,stdout,fout",
                "property.log.dir = logs",
                "appender.rolling.fileName = ${log.dir}/mirth.log");
        Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);

        FileTime expectedModifiedTime = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(file.toPath(), expectedModifiedTime);

        Log4jMigrations.migrateConfiguration(file);

        assertEquals(expectedModifiedTime, Files.getLastModifiedTime(file.toPath()));
        assertEquals(contents, Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void testMigrateLog4jFailsGracefullyWithReadOnlyFile() throws Exception {
        File file = File.createTempFile("log4j2-readonly", ".properties");
        file.deleteOnExit();
        String contents = String.join(System.lineSeparator(),
                "rootLogger = ERROR,stdout,fout",
                "dir.logs   = logs",
                "property.log.dir = logs");
        Path path = file.toPath();
        Files.writeString(path, contents, StandardCharsets.UTF_8);

        Assume.assumeTrue(Files.getFileStore(path).supportsFileAttributeView("posix"));

        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(path);

        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ));
            assertFalse(Files.isWritable(path));
            System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8.name()));

            Log4jMigrations.migrateConfiguration(file);
        } finally {
            Files.setPosixFilePermissions(path, originalPermissions);
            System.setErr(originalErr);
        }

        String errOutput = errBytes.toString(StandardCharsets.UTF_8.name());
        assertTrue(errOutput.contains("Failed to migrate Log4j configuration"));
        assertEquals(contents, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    public void testMigrateLog4jIgnoresMissingFile() throws Exception {
        File parentDir = Files.createTempDirectory("log4j2-missing").toFile();
        parentDir.deleteOnExit();
        File file = new File(parentDir, "missing-log4j2.properties");

        assertFalse(file.exists());

        Log4jMigrations.migrateConfiguration(file);

        assertFalse(file.exists());
    }
}