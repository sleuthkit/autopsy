/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.ISevenZipInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

/**
 * Set of utilities that handles archive file extraction. Uses 7zip library.
 */
final class ArchiveUtil {

    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar", "arj", "7z", "7zip", "gzip", "gz", "bzip2", "tar", "tgz",}; // NON-NLS

    
    private ArchiveUtil() {
    }
    
    /**
     * Enum of mime types which support archive extraction
     */
    private enum SupportedArchiveExtractionFormats {

        ZIP("application/zip"), //NON-NLS
        SEVENZ("application/x-7z-compressed"), //NON-NLS
        GZIP("application/gzip"), //NON-NLS
        XGZIP("application/x-gzip"), //NON-NLS
        XBZIP2("application/x-bzip2"), //NON-NLS
        XTAR("application/x-tar"), //NON-NLS
        XGTAR("application/x-gtar"),
        XRAR("application/x-rar-compressed"); //NON-NLS

        private final String mimeType;

        SupportedArchiveExtractionFormats(final String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return this.mimeType;
        }
    }

    /**
     * Exception thrown when archive handling resulted in an error
     */
    static class ArchiveExtractionException extends Exception {

        private static final long serialVersionUID = 1L;

        ArchiveExtractionException(String message) {
            super(message);
        }

        ArchiveExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * This method returns array of supported file extensions.
     * 
     * @return String array of supported file extensions.
     */
    static String[] getSupportedArchiveTypes(){
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * This method returns true if the MIME type is currently supported. Else it
     * returns false.
     *
     * @param mimeType File mime type
     *
     * @return This method returns true if the file format is currently
     *         supported. Else it returns false.
     */
    static boolean isExtractionSupportedByMimeType(String mimeType) {
        for (SupportedArchiveExtractionFormats s : SupportedArchiveExtractionFormats.values()) {
            if (s.toString().equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method returns true if the file extension is currently supported.
     * Else it returns false. Attempt extension based detection in case Apache
     * Tika based detection fails.
     *
     * @param extension File extension
     *
     * @return This method returns true if the file format is currently
     *         supported. Else it returns false.
     */
    static boolean isExtractionSupportedByFileExtension(String extension) {
        // attempt extension matching
        for (String supportedExtension : SUPPORTED_EXTENSIONS) {
            if (extension.equals(supportedExtension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of file names contained within an archive.
     *
     * @param archiveFilePath Full path to the archive file
     *
     * @return List of file names contained within archive
     *
     * @throws
     * ArchiveExtractionException
     */
    static List<String> getListOfFilesWithinArchive(String archiveFilePath) throws ArchiveExtractionException {
        if (!SevenZip.isInitializedSuccessfully() && (SevenZip.getLastInitializationException() == null)) {
            try {
                SevenZip.initSevenZipFromPlatformJAR();
            } catch (SevenZipNativeInitializationException ex) {
                throw new ArchiveExtractionException("AutoIngestDashboard_bnPause_paused", ex);
            }
        }
        List<String> files = new ArrayList<>();
        ISevenZipInArchive inArchive = null;
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(new File(archiveFilePath), "r");
            inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile));
            final ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();
            for (ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                files.add(item.getPath());
            }
        } catch (Exception ex) {
            throw new ArchiveExtractionException("Exception while reading archive contents", ex);
        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (SevenZipException ex) {
                    throw new ArchiveExtractionException("Exception while closing the archive", ex);
                }
            }
        }
        return files;
    }

    /**
     * Extracts contents of an archive file into a directory.
     *
     * @param archiveFilePath   Full path to archive.
     * @param destinationFolder Path to directory where results will be
     *                          extracted to.
     *
     * @throws
     * ArchiveExtractionException
     */
    static void unpackArchiveFile(String archiveFilePath, String destinationFolder) throws ArchiveExtractionException {
        if (!SevenZip.isInitializedSuccessfully() && (SevenZip.getLastInitializationException() == null)) {
            try {
                SevenZip.initSevenZipFromPlatformJAR();
            } catch (SevenZipNativeInitializationException ex) {
                throw new ArchiveExtractionException("Unable to initialize 7Zip libraries", ex);
            }
        }
        ISevenZipInArchive inArchive = null;
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(new File(archiveFilePath), "r");
            inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile));
            final ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();

            for (ISimpleInArchiveItem entry : simpleInArchive.getArchiveItems()) {
                String entryPathInArchive = entry.getPath();
                Path fullPath = Paths.get(destinationFolder, entryPathInArchive);
                File destFile = new File(fullPath.toString());
                File destinationParent = destFile.getParentFile();
                destinationParent.mkdirs();
                if (!entry.isFolder()) {
                    UnpackStream unpackStream = null;
                    try {
                        Long size = entry.getSize();
                        unpackStream = new UnpackStream(destFile.toString(), size);
                        entry.extractSlow(unpackStream);
                    } catch (Exception ex) {
                        throw new ArchiveExtractionException("Exception while unpacking archive contents", ex);
                    } finally {
                        if (unpackStream != null) {
                            unpackStream.close();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new ArchiveExtractionException("Exception while unpacking archive contents", ex);
        } finally {
            try {
                if (inArchive != null) {
                    inArchive.close();
                }
            } catch (SevenZipException ex) {
                throw new ArchiveExtractionException("Exception while closing the archive", ex);
            }
        }
    }

    /**
     * Stream used to unpack an archive to local file
     */
    private static class UnpackStream implements ISequentialOutStream {

        private OutputStream output;
        private String destFilePath;

        UnpackStream(String destFilePath, long size) throws ArchiveExtractionException {
            this.destFilePath = destFilePath;
            try {
                output = new FileOutputStream(destFilePath);
            } catch (IOException ex) {
                throw new ArchiveExtractionException("Exception while unpacking archive contents", ex);
            }

        }

        @Override
        public int write(byte[] bytes) throws SevenZipException {
            try {
                output.write(bytes);
            } catch (IOException ex) {
                throw new SevenZipException("Error writing unpacked file to " + destFilePath, ex);
            }
            return bytes.length;
        }

        public void close() throws ArchiveExtractionException {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException ex) {
                    throw new ArchiveExtractionException("Exception while closing the archive", ex);
                }
            }
        }
    }
}
