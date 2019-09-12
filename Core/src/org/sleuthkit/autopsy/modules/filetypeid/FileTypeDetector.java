/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MimeTypes;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Detects the MIME type of a file by an inspection of its contents, using
 * custom file type definitions by users, custom file type definitions by
 * Autopsy, and Tika. User file type definitions take precedence over both
 * Autopsy file type definitions and Tika, and Autopsy file type definitions
 * take precendence over Tika.
 */
public class FileTypeDetector {

    private static final Logger logger = Logger.getLogger(FileTypeDetector.class.getName());
    private static final Tika tika = new Tika();
    private static final int SLACK_FILE_THRESHOLD = 4096;
    private final List<FileType> userDefinedFileTypes;
    private final List<FileType> autopsyDefinedFileTypes;
    private static SortedSet<String> tikaDetectedTypes;

    /**
     * Gets a sorted set of the file types that can be detected: the MIME types
     * detected by Tika (without optional parameters), the custom MIME types
     * defined by Autopsy, and any custom MIME types defined by the user.
     *
     * @return A list of all detectable file types.
     *
     * @throws FileTypeDetectorInitException If an error occurs while assembling
     *                                       the list of types
     */
    public static synchronized SortedSet<String> getDetectedTypes() throws FileTypeDetectorInitException {
        TreeSet<String> detectedTypes = new TreeSet<>((String string1, String string2) -> {
            int result = String.CASE_INSENSITIVE_ORDER.compare(string1, string2);
            if (result == 0) {
                result = string1.compareTo(string2);
            }
            return result;
        });
        detectedTypes.addAll(FileTypeDetector.getTikaDetectedTypes());
        try {
            for (FileType fileType : CustomFileTypesManager.getInstance().getAutopsyDefinedFileTypes()) {
                detectedTypes.add(fileType.getMimeType());
            }
        } catch (CustomFileTypesManager.CustomFileTypesException ex) {
            throw new FileTypeDetectorInitException("Error loading Autopsy custom file types", ex);
        }
        try {
            for (FileType fileType : CustomFileTypesManager.getInstance().getUserDefinedFileTypes()) {
                detectedTypes.add(fileType.getMimeType());
            }
        } catch (CustomFileTypesManager.CustomFileTypesException ex) {
            throw new FileTypeDetectorInitException("Error loading user custom file types", ex);
        }
        return detectedTypes;
    }

    /**
     * Gets a sorted set of the MIME types detected by Tika (without optional
     * parameters).
     *
     * @return A list of all detectable non-custom file types.
     *
     */
    private static SortedSet<String> getTikaDetectedTypes() {
        if (null == tikaDetectedTypes) {
            tikaDetectedTypes = org.apache.tika.mime.MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry().getTypes()
                    .stream().filter(t -> !t.hasParameters()).map(s -> s.toString().replace("tika-", "")).collect(Collectors.toCollection(TreeSet::new));
        }
        return Collections.unmodifiableSortedSet(tikaDetectedTypes);
    }

    /**
     * Constructs an object that detects the MIME type of a file by an
     * inspection of its contents, using custom file type definitions by users,
     * custom file type definitions by Autopsy, and Tika. User file type
     * definitions take precedence over both Autopsy file type definitions and
     * Tika, and Autopsy file type definitions take precendence over Tika.
     *
     * @throws FileTypeDetectorInitException If an initialization error occurs,
     *                                       e.g., user-defined file type
     *                                       definitions exist but cannot be
     *                                       loaded.
     */
    public FileTypeDetector() throws FileTypeDetectorInitException {
        try {
            userDefinedFileTypes = CustomFileTypesManager.getInstance().getUserDefinedFileTypes();
            autopsyDefinedFileTypes = CustomFileTypesManager.getInstance().getAutopsyDefinedFileTypes();
        } catch (CustomFileTypesManager.CustomFileTypesException ex) {
            throw new FileTypeDetectorInitException("Error loading custom file types", ex); //NON-NLS
        }
    }

    /**
     * Determines whether or not a given MIME type is detectable by this
     * detector.
     *
     * @param mimeType The MIME type name (e.g., "text/html").
     *
     * @return True or false.
     */
    public boolean isDetectable(String mimeType) {
        return isDetectableAsCustomType(userDefinedFileTypes, mimeType)
                || isDetectableAsCustomType(autopsyDefinedFileTypes, mimeType)
                || isDetectableByTika(mimeType);
    }

    /**
     * Determines whether or not a given MIME type is detectable as a
     * user-defined MIME type by this detector.
     *
     * @param customTypes
     * @param mimeType    The MIME type name (e.g., "text/html").
     *
     * @return True or false.
     */
    private boolean isDetectableAsCustomType(List<FileType> customTypes, String mimeType) {
        for (FileType fileType : customTypes) {
            if (fileType.getMimeType().equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether or not a given MIME type is detectable by Tika.
     *
     * @param mimeType The MIME type name (e.g., "text/html").
     *
     * @return True or false.
     */
    private boolean isDetectableByTika(String mimeType) {
        return FileTypeDetector.getTikaDetectedTypes().contains(removeOptionalParameter(mimeType));
    }

    /**
     * Detects the MIME type of a file, then writes it the AbstractFile object
     * representing the file and returns the detected type.
     *
     * @param file The file to test.
     *
     * @return A MIME type name. If file type could not be detected, or results
     *         were uncertain, octet-stream is returned.
     *
     *
     */
    public String getMIMEType(AbstractFile file) {
        /*
         * Check to see if the file has already been typed.
         */
        String mimeType = file.getMIMEType();
        if (null != mimeType) {
            // We remove the optional parameter to allow this method to work
            // with legacy databases that may contain MIME types with the
            // optional parameter attached.
            return removeOptionalParameter(mimeType);
        }

        /*
         * Mark non-regular files (refer to TskData.TSK_FS_META_TYPE_ENUM),
         * zero-sized files, unallocated space, and unused blocks (refer to
         * TskData.TSK_DB_FILES_TYPE_ENUM) as octet-stream.
         */
        if (!file.isFile() || file.getSize() <= 0
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)
                || ((file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.SLACK) && file.getSize() < SLACK_FILE_THRESHOLD)) {
            mimeType = MimeTypes.OCTET_STREAM;
        }

        /*
         * If the file is a regular file, give precedence to user-defined custom
         * file types.
         */
        if (null == mimeType) {
            mimeType = detectUserDefinedType(file);
        }

        /*
         * If the file does not match a user-defined type, give precedence to
         * custom file types defined by Autopsy.
         */
        if (null == mimeType) {
            mimeType = detectAutopsyDefinedType(file);
        }

        /*
         * If the file does not match a user-defined type, send the initial
         * bytes to Tika.
         */
        if (null == mimeType) {
            ReadContentInputStream stream = new ReadContentInputStream(file);

            try (TikaInputStream tikaInputStream = TikaInputStream.get(stream)) {
                String tikaType = tika.detect(tikaInputStream);

                /*
                 * Remove the Tika suffix from the MIME type name.
                 */
                mimeType = tikaType.replace("tika-", ""); //NON-NLS
                /*
                 * Remove the optional parameter from the MIME type.
                 */
                mimeType = removeOptionalParameter(mimeType);
                
                /*
                 * If Tika recognizes the file signature, then use the file 
                 * name to refine the type. In short, this is to exclude the 
                 * mime types that are determined solely by file extension.
                 * More details in JIRA-4871.
                 */
                if (!mimeType.equals(MimeTypes.OCTET_STREAM)) {
                    ReadContentInputStream secondPassStream = new ReadContentInputStream(file);
                    try (TikaInputStream secondPassTikaStream = TikaInputStream.get(secondPassStream)) {
                        tikaType = tika.detect(secondPassTikaStream, file.getName());
                        mimeType = tikaType.replace("tika-", ""); //NON-NLS
                        mimeType = removeOptionalParameter(mimeType);
                    }
                } else {
                    /*
                     * If the file was marked as an octet stream and the extension is .txt, try to detect a text
                     * encoding with Decodetect.
                     */
                    if (file.getNameExtension().equals("txt")) {
                        Charset detectedCharset = TextExtractor.getEncoding(file);
                        if (detectedCharset != TextExtractor.UNKNOWN_CHARSET) {
                            mimeType = MimeTypes.PLAIN_TEXT;
                        }
                    }
                }

                /**
                 * We cannot trust Tika's audio/mpeg mimetype. Lets verify the
                 * first two bytes and confirm it is not 0xffff. Details in
                 * JIRA-4659
                 */
                if (mimeType.contains("audio/mpeg")) {
                    try {
                        byte[] header = getNBytes(file, 0, 2);
                        if (byteIs0xFF(header[0]) && byteIs0xFF(header[1])) {
                            mimeType = MimeTypes.OCTET_STREAM;
                        }
                    } catch (TskCoreException ex) {
                        //Oh well, the mimetype is what it is.
                        logger.log(Level.WARNING, String.format("Could not verify audio/mpeg mimetype for file %s with id=%d", file.getName(), file.getId()), ex);
                    }
                }
            } catch (Exception ignored) {
                /*
                 * This exception is swallowed and not logged rather than
                 * propagated because files in data sources are not always
                 * consistent with their file system metadata, making for read
                 * errors. Also, Tika can be a bit flaky at times, making this a
                 * best effort endeavor. Default to octet-stream.
                 */
                mimeType = MimeTypes.OCTET_STREAM;
            }
        }

        /*
         * Documented side effect: write the result to the AbstractFile object.
         */
        file.setMIMEType(mimeType);

        return mimeType;
    }

    /**
     * Determine if the byte is 255 (0xFF) by examining the last 4 bits and the
     * first 4 bits.
     *
     * @param x byte
     *
     * @return Flag indicating the byte if 0xFF
     */
    private boolean byteIs0xFF(byte x) {
        return (x & 0x0F) == 0x0F && (x & 0xF0) == 0xF0;
    }

    /**
     * Retrieves the first N bytes from a file.
     *
     * @param file   Abstract file to read
     * @param offset Offset to begin reading
     * @param n      Number of bytes to read
     *
     * @return Byte array of size n
     *
     * @throws TskCoreException
     */
    private byte[] getNBytes(AbstractFile file, int offset, int n) throws TskCoreException {
        byte[] headerCache = new byte[n];
        file.read(headerCache, offset, n);
        return headerCache;
    }

    /**
     * Removes the optional parameter from a MIME type string
     *
     * @param mimeType
     *
     * @return MIME type without the optional parameter
     */
    private String removeOptionalParameter(String mimeType) {
        int indexOfSemicolon = mimeType.indexOf(';');
        if (indexOfSemicolon != -1) {
            return mimeType.substring(0, indexOfSemicolon).trim();
        } else {
            return mimeType;
        }
    }

    /**
     * Determines whether or not a file matches a user-defined custom file type.
     *
     * @param file The file to test.
     *
     * @return The MIME type as a string if a match is found; otherwise null.
     */
    private String detectUserDefinedType(AbstractFile file) {
        String retValue = null;

        for (FileType fileType : userDefinedFileTypes) {
            if (fileType.matches(file)) {
                retValue = fileType.getMimeType();
                break;
            }
        }
        return retValue;
    }

    /**
     * Determines whether or not a file matches a custom file type defined by
     * Autopsy.
     *
     * @param file The file to test.
     *
     * @return The MIME type as a string if a match is found; otherwise null.
     */
    private String detectAutopsyDefinedType(AbstractFile file) {
        for (FileType fileType : autopsyDefinedFileTypes) {
            if (fileType.matches(file)) {
                return fileType.getMimeType();
            }
        }
        return null;
    }

    /*
     * Exception thrown if an initialization error occurs, e.g., user-defined
     * file type definitions exist but cannot be loaded.
     */
    public static class FileTypeDetectorInitException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to throw if an initialization error occurs,
         * e.g., user-defined file type definitions exist but cannot be loaded.
         *
         * @param message The exception message,
         */
        FileTypeDetectorInitException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to throw if an initialization error occurs,
         * e.g., user-defined file type definitions exist but cannot be loaded.
         *
         * @param message   The exception message,
         * @param throwable The underlying cause of the exception.
         */
        FileTypeDetectorInitException(String message, Throwable throwable) {
            super(message, throwable);
        }

    }

    /**
     * Gets the names of the custom file types defined by the user or by
     * Autopsy.
     *
     * @return A list of the user-defined MIME types.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public List<String> getUserDefinedTypes() {
        List<String> customFileTypes = new ArrayList<>();
        userDefinedFileTypes.forEach((fileType) -> {
            customFileTypes.add(fileType.getMimeType());
        });
        autopsyDefinedFileTypes.forEach((fileType) -> {
            customFileTypes.add(fileType.getMimeType());
        });
        return customFileTypes;
    }

    /**
     * Gets the MIME type of a file, detecting it if it is not already known. If
     * detection is necessary, the result is added to the case database.
     *
     * @param file The file.
     *
     * @return A MIME type name.
     *
     * @throws TskCoreException if detection is required and there is a problem
     *                          writing the result to the case database.
     * @deprecated Use getMIMEType instead, and call AbstractFile.setMIMEType
     * and AbstractFile.save to save the result to the file object and the
     * database.
     */
    @Deprecated
    public String detectAndPostToBlackboard(AbstractFile file) throws TskCoreException {
        String fileType = getMIMEType(file);
        file.setMIMEType(fileType);
        file.save();
        return fileType;
    }

    /**
     * Gets the MIME type of a file, detecting it if it is not already known. If
     * detection is necessary, the result is added to the case database.
     *
     * @param file The file.
     *
     * @return A MIME type name. If file type could not be detected or results
     *         were uncertain, octet-stream is returned.
     *
     * @throws TskCoreException if detection is required and there is a problem
     *                          writing the result to the case database.
     *
     * @deprecated Use getMIMEType instead, and call AbstractFile.setMIMEType
     * and AbstractFile.save to save the result to the file object and the
     * database.
     */
    @Deprecated
    public String getFileType(AbstractFile file) throws TskCoreException {
        String fileType = getMIMEType(file);
        file.setMIMEType(fileType);
        file.save();
        return fileType;
    }

    /**
     * Detects the MIME type of a file. The result is not added to the case
     * database.
     *
     * @param file The file to test.
     *
     * @return A MIME type name. If file type could not be detected or results
     *         were uncertain, octet-stream is returned.
     *
     * @throws TskCoreException
     * @deprecated Use getMIMEType instead.
     */
    @Deprecated
    public String detect(AbstractFile file) throws TskCoreException {
        String fileType = getMIMEType(file);
        return fileType;
    }

}
