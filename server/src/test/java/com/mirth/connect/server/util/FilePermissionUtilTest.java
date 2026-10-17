package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.jna.Platform;

public class FilePermissionUtilTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();


    @Test
    public void testPosixPermissions() throws Exception {
        assumeFalse("Test only applies on POSIX filesystems", Platform.isWindows());
        File file = new File(temporaryFolder.getRoot(), "keystore.p12");

        FilePermissionUtil.createOwnerOnlyFile(file);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())));
    }

    @Test
    public void testWindowsPermissions() throws Exception {
        assumeTrue("Test only applies on windows", Platform.isWindows());
        assumeTrue("Test assumes english locale", System.getProperty("user.language").equals("en"));
        File file = new File(temporaryFolder.getRoot(), "keystore.p12");
        String path = file.getAbsolutePath();

        FilePermissionUtil.createOwnerOnlyFile(file);
        var icaclsOutput = system("icacls", path);
        assertContains(icaclsOutput, System.getProperty("user.name") + ":(F)");
        assertContains(icaclsOutput, "BUILTIN\\Administrators:(F)");
        assertContains(icaclsOutput, "NT AUTHORITY\\SYSTEM:(F)");
        assertNotContains(icaclsOutput, "Everyone:");
        assertNotContains(icaclsOutput, "Users:");
        assertNotContains(icaclsOutput, "(I)" /* inherited permissions */);
    }

    private void assertContains(String output, String expected) {
        assertTrue("Expected output to contain '" + expected + "' but was '" + output + "'", output.contains(expected));
    }

    private void assertNotContains(String output, String expected) {
        assertFalse("Expected output to not contain '" + expected + "' but was '" + output + "'", output.contains(expected));
    }

    private String system(String command, String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<String>();
        commandLine.add(command);
        commandLine.addAll(Arrays.asList(arguments));

        Process process = new ProcessBuilder(commandLine).redirectErrorStream(true).start();
        String output = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());

        assertEquals(output, 0, process.waitFor());
        return output;
    }
}
