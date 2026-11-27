// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.server.util;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.sun.security.auth.module.NTSystem;

/*
 * The Windows half of FilePermissionUtil, kept apart so that nothing here is loaded on a platform
 * that has no Win32 API to call.
 */
class WindowsFilePermissionUtil {

    /*
     * Full control to creator, LocalSystem and the local administrators.  Matches 
     * user profile directories.
     */
    private static final String SDDL = "D:P(A;;FA;;;{CURRENTUSERSID})(A;;FA;;;SY)(A;;FA;;;BA)";

    private static final int SDDL_REVISION_1 = 1;

    /*
     * The SDDL conversions are not part of JNA's Advapi32 mapping.
     */
    private interface Advapi32Sddl extends StdCallLibrary {
        Advapi32Sddl INSTANCE = Native.loadLibrary("Advapi32", Advapi32Sddl.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean ConvertStringSecurityDescriptorToSecurityDescriptor(String sddl, int revision, PointerByReference securityDescriptor, IntByReference size);
    }

    private WindowsFilePermissionUtil() {}

    /*
     * Creates the file with a DACL that only lets the account the server runs as and the machine's
     * administrators near it. The DACL is handed to CreateFile rather than applied afterwards, so
     * the file is never briefly readable by anyone else.
     */
    static void createRestrictedFile(Path path) throws IOException {
        Pointer securityDescriptor = buildSecurityDescriptor();

        try {
            createFile(path, securityDescriptor);
        } finally {
            Kernel32.INSTANCE.LocalFree(securityDescriptor);
        }
    }

    private static Pointer buildSecurityDescriptor() throws IOException {
        PointerByReference securityDescriptor = new PointerByReference();

        String sddl = SDDL.replace("{CURRENTUSERSID}", new NTSystem().getUserSID());
        if (!Advapi32Sddl.INSTANCE.ConvertStringSecurityDescriptorToSecurityDescriptor(sddl, SDDL_REVISION_1, securityDescriptor, null)) {
            throw lastError("Could not build a security descriptor from \"" + sddl + "\"");
        }

        return securityDescriptor.getValue();
    }

    private static void createFile(Path path, Pointer securityDescriptor) throws IOException {
        WinBase.SECURITY_ATTRIBUTES securityAttributes = new WinBase.SECURITY_ATTRIBUTES();
        securityAttributes.lpSecurityDescriptor = securityDescriptor;
        securityAttributes.bInheritHandle = false;

        HANDLE handle = Kernel32.INSTANCE.CreateFile(toExtendedLengthPath(path), WinNT.GENERIC_WRITE, 0, securityAttributes, WinNT.CREATE_NEW, WinNT.FILE_ATTRIBUTE_NORMAL, null);

        if (WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            // reported the same way as Files.createFile, so that callers need not care which is which
            if (Kernel32.INSTANCE.GetLastError() == WinError.ERROR_FILE_EXISTS) {
                throw new FileAlreadyExistsException(path.toString());
            }

            throw lastError("Could not create " + path);
        }

        Kernel32.INSTANCE.CloseHandle(handle);
    }

    /*
     * CreateFile rejects paths longer than MAX_PATH unless they are in the \\?\ extended-length
     * form, which java.nio adds for its own calls but JNA does not. That form also turns off the
     * handling of "." and "..", so the path is normalized first.
     */
    private static String toExtendedLengthPath(Path path) {
        String absolutePath = path.toAbsolutePath().normalize().toString();

        if (absolutePath.startsWith("\\\\?\\")) {
            return absolutePath;
        } else if (absolutePath.startsWith("\\\\")) {
            // a UNC path, \\server\share\...
            return "\\\\?\\UNC\\" + absolutePath.substring(2);
        } else {
            return "\\\\?\\" + absolutePath;
        }
    }

    private static IOException lastError(String message) {
        return new IOException(message + ": " + Kernel32Util.formatMessage(Kernel32.INSTANCE.GetLastError()));
    }
}
