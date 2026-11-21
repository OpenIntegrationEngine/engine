// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2026 Tony Germano

package com.mirth.connect.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.util.messagewriter.EncryptionType;

public class ArchiveUtils {
    /**
     * The buffer size to use when copying files into an archive
     */

    private static Logger logger = LogManager.getLogger(ArchiveUtils.class);

    public static final String PASSWORD_UNSUPPORTED_MESSAGE = "Password protection is only supported for ZIP archives without a compressor";

    /**
     * Only plain ZIP archives can be password protected. Callers can use this to reject a password
     * up front, before doing the work of writing the files that would go into the archive.
     */
    public static boolean isPasswordSupported(String archiver, String compressor) {
        return isPlainZip(archiver, compressor);
    }

    private static boolean isPlainZip(String archiver, String compressor) {
        return ArchiveStreamFactory.ZIP.equals(archiver) && compressor == null;
    }

    /**
     * Create an archive file from files/subfolders in a given source folder.
     * 
     * @param sourceFolder
     *            Subfolders and files inside this folder will be copied into the archive
     * @param destinationFile
     *            The destination archive file
     * @param archiver
     *            The archiver format, see
     *            org.apache.commons.compress.archivers.ArchiveStreamFactory
     * @throws CompressException
     */
    public static void createArchive(File sourceFolder, File destinationFile, String archiver) throws CompressException {
        createArchive(sourceFolder, destinationFile, archiver, null, null, null);
    }

    /**
     * Create an archive file from files/sub-folders in a given source folder.
     * 
     * @param sourceFolder
     *            Sub-folders and files inside this folder will be copied into the archive
     * @param destinationFile
     *            The destination archive file
     * @param archiver
     *            The archiver format, see
     *            org.apache.commons.compress.archivers.ArchiveStreamFactory
     * @param compressor
     *            The compressor format, see
     *            org.apache.commons.compress.compressors.CompressorStreamFactory
     * @throws CompressException
     */
    public static void createArchive(File sourceFolder, File destinationFile, String archiver, String compressor, String password, EncryptionType encryptionType) throws CompressException {
        if (!sourceFolder.isDirectory() || !sourceFolder.exists()) {
            throw new CompressException("Invalid source folder: " + sourceFolder.getAbsolutePath());
        }

        boolean plainZip = isPlainZip(archiver, compressor);

        if (StringUtils.isNotBlank(password) && !plainZip) {
            // Only the plain ZIP path can encrypt; refuse rather than silently write an unprotected archive
            throw new CompressException(PASSWORD_UNSUPPORTED_MESSAGE);
        }

        logger.debug("Creating archive \"" + destinationFile.getAbsolutePath() + "\" from folder \"" + sourceFolder.getAbsolutePath() + "\"");
        OutputStream outputStream = null;
        OutputStream archiveOutputStream = null;

        try {
            /*
             * Plain ZIP archives are written with zip4j, the only library here that can encrypt
             * them. Every other combination, including ZIP with a compressor, goes through
             * commons-compress, which cannot encrypt (hence the password check above).
             */
            if (plainZip) {
                char[] zipPassword = StringUtils.isNotBlank(password) ? password.toCharArray() : null;
                // zip4j does not close the underlying stream when finishing the archive fails, so keep it to close below
                outputStream = new FileOutputStream(destinationFile);
                archiveOutputStream = new ZipOutputStream(outputStream, zipPassword);
            } else {
                // if not using ZIP format, use the archiver/compressor stream factories to initialize the archive output stream
                outputStream = new BufferedOutputStream(new FileOutputStream(destinationFile));

                if (compressor != null) {
                    outputStream = new CompressorStreamFactory().createCompressorOutputStream(compressor, outputStream);
                }

                archiveOutputStream = new ArchiveStreamFactory().createArchiveOutputStream(archiver, outputStream);
            }

            createFolderArchive(sourceFolder, archiveOutputStream, sourceFolder.getAbsolutePath() + IOUtils.DIR_SEPARATOR, password, encryptionType);

            /*
             * Closing the archive writes its trailer (the zip central directory, the tar end of
             * archive records, a compressor's footer) and flushes it. Do it here so a failure is
             * reported; the closeQuietly calls below only release the streams after a failure.
             */
            OutputStream finished = archiveOutputStream;
            archiveOutputStream = null;
            finished.close();
        } catch (Exception e) {
            throw new CompressException(e);
        } finally {
            IOUtils.closeQuietly(archiveOutputStream);
            IOUtils.closeQuietly(outputStream);
            logger.debug("Finished creating archive \"" + destinationFile.getAbsolutePath() + "\"");
        }
    }

    private static void createFolderArchive(File folder, OutputStream outputStream, String rootFolder, String password, EncryptionType encryptionType) throws CompressException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                createFolderArchive(file, outputStream, rootFolder, password, encryptionType);
            } else {
                try {
                    // extract/remove the rootFolder from the file's absolute path before adding it to the archive
                    String entryName = file.getAbsolutePath();

                    if (entryName.substring(0, rootFolder.length()).equals(rootFolder)) {
                        entryName = entryName.substring(rootFolder.length());
                    }

                    try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                        logger.debug("Adding \"" + entryName + "\" to archive");

                        /*
                         * The entry is closed only after a successful copy. Closing it in a
                         * finally block would replace the original failure with an error from
                         * closing a half-written (or never opened) entry.
                         */
                        if (outputStream instanceof ArchiveOutputStream archiveOutputStream) {
                            archiveOutputStream.putArchiveEntry(archiveOutputStream.createArchiveEntry(file, entryName));
                            inputStream.transferTo(outputStream);
                            archiveOutputStream.closeArchiveEntry();
                        } else if (outputStream instanceof ZipOutputStream zipOutputStream) {
                            ZipParameters parameters = new ZipParameters();
                            // Zip entry names use forward slashes whatever the platform's separator is
                            parameters.setFileNameInZip(entryName.replace(File.separatorChar, '/'));
                            parameters.setCompressionMethod(CompressionMethod.DEFLATE);
                            parameters.setCompressionLevel(CompressionLevel.NORMAL);

                            if (StringUtils.isNotBlank(password)) {
                                parameters.setEncryptFiles(true);

                                boolean isAes = encryptionType != EncryptionType.STANDARD;
                                parameters.setEncryptionMethod(isAes ? EncryptionMethod.AES : EncryptionMethod.ZIP_STANDARD);

                                if (isAes) {
                                    parameters.setAesKeyStrength(encryptionType.getKeyStrength());
                                }
                            }

                            zipOutputStream.putNextEntry(parameters);
                            inputStream.transferTo(outputStream);
                            zipOutputStream.closeEntry();
                        }
                    }
                } catch (Exception e) {
                    throw new CompressException(e);
                }
            }
        }
    }

    /**
     * Extracts ZipInputStream to target directory.
     * 
     * @param targetDir
     * @param source
     * @return true if source contains any entries (ie we are not extracting an empty zip)
     * @throws IOException
     */
    public static List<URL> extractArchive(File targetDir, ZipInputStream source) throws IOException, ZipException {
        ZipEntry zipEntry;
        List<URL> fileUrls = new ArrayList<>();

        while ((zipEntry = source.getNextEntry()) != null) {
            File file = new File(targetDir, zipEntry.getName());
            if (!file.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                throw new ZipException("Zip file is attempting to traverse out of base directory");
            }
            fileUrls.add(file.toURI().toURL());

            if (zipEntry.isDirectory()) {
                if (!file.mkdir()) {
                    throw new IOException("Unable to create directory: " + file.toString());
                }
                source.closeEntry();
            } else {
                OutputStream outputStream = new FileOutputStream(file);
                try {
                    IOUtils.copy(source, outputStream);
                    source.closeEntry();
                } finally {
                    outputStream.close();
                }
            }
        }
        return fileUrls;
    }

    public static class CompressException extends Exception {
        public CompressException(String message) {
            super(message);
        }

        public CompressException(Throwable cause) {
            super(cause);
        }
    }
}
