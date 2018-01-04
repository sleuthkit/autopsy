/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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
                    .stream().filter(t -> !t.hasParameters()).map(s -> s.toString()).collect(Collectors.toCollection(TreeSet::new));
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
     * Detects the MIME type of a file.
     *
     * @param file The file to test.
     *
     * @return A MIME type name. If file type could not be detected or results
     *         were uncertain, octet-stream is returned.
     *
     * @throws TskCoreException if there is a problem writing the result to the
     *                          case database.
     */
    public String detect(AbstractFile file) throws TskCoreException {
        return detect(file, false);
    }

    /**
     * Detects the MIME type of a file. The result is saved to the case database
     * only if the add to case database flag is set.
     *
     * Ingest modules should not set addToCaseDb to true - the ingest process
     * handles the database save.
     *
     * @param file        The file to test.
     * @param addToCaseDb Whether the MIME type should be added to the case
     *                    database. This flag is part of a partial workaround
     *                    for a check-then-act-race condition (see notes in
     *                    comments for details).
     *
     * @return A MIME type name. If file type could not be detected or results
     *         were uncertain, octet-stream is returned.
     *
     * @throws TskCoreException If there is a problem writing the result to the
     *                          case database.
     */
    private String detect(AbstractFile file, boolean addToCaseDb) throws TskCoreException {
        /*
         * Check to see if the file has already been typed. This is the "check"
         * part of a check-then-act race condition (see note below).
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
                String tikaType = tika.detect(tikaInputStream, file.getName());

                /*
                 * Remove the Tika suffix from the MIME type name.
                 */
                mimeType = tikaType.replace("tika-", ""); //NON-NLS
                /*
                 * Remove the optional parameter from the MIME type.
                 */
                mimeType = removeOptionalParameter(mimeType);

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
         * If adding the result to the case database, do so now.
         *
         * NOTE: This condtional is a way to deal with the check-then-act race
         * condition created by the gap between querying the MIME type and
         * recording it. It is not really a problem for the mime_type column of
         * the tsk_files table, but it can lead to duplicate blackboard posts,
         * and the posts are required to maintain backward compatibility.
         * Various mitigation strategies were considered. It was decided to go
         * with the policy that only ingest modules are allowed to add file
         * types to the case database, at least until such time as file types
         * are no longer posted to the blackboard. Of course, this is not a
         * perfect solution. It's not really enforceable for community
         * contributed plug ins and it does not handle the unlikely but possible
         * scenario of multiple processes typing the same file for a multi-user
         * case.
         */
        if (addToCaseDb) {
            /*
             * Add the MIME type to the files table in the case database.
             */
            Case.getCurrentCase().getSleuthkitCase().setFileMIMEType(file, mimeType);
        }

        file.setMIMEType(mimeType);
        return mimeType;
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
     * Determines whether or not the a file matches a user-defined custom file
     * type.
     *
     * @param file The file to test.
     *
     * @return The file type name string or null, if no match is detected.
     *
     * @throws TskCoreException
     */
    private String detectUserDefinedType(AbstractFile file) throws TskCoreException {
        for (FileType fileType : userDefinedFileTypes) {
            if (fileType.matches(file)) {
                if (fileType.createInterestingFileHit()) {
                    BlackboardArtifact artifact;
                    artifact = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                    Collection<BlackboardAttribute> attributes = new ArrayList<>();
                    BlackboardAttribute setNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, FileTypeIdModuleFactory.getModuleName(), fileType.getInterestingFilesSetName());
                    attributes.add(setNameAttribute);

                    /*
                     * Use the MIME type as the category attribute, i.e., the
                     * rule that determined this file belongs to the interesting
                     * files set.
                     */
                    BlackboardAttribute ruleNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, FileTypeIdModuleFactory.getModuleName(), fileType.getMimeType());
                    attributes.add(ruleNameAttribute);

                    artifact.addAttributes(attributes);
                    /*
                     * Index the artifact for keyword search.
                     */
                    try {
                        Case.getCurrentCase().getServices().getBlackboard().indexArtifact(artifact);
                    } catch (Blackboard.BlackboardException ex) {
                        logger.log(Level.SEVERE, String.format("Unable to index blackboard artifact %d", artifact.getArtifactID()), ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(
                                NbBundle.getMessage(Blackboard.class, "Blackboard.unableToIndexArtifact.exception.msg"), artifact.getDisplayName());
                    }
                }

                return fileType.getMimeType();
            }
        }
        return null;
    }

    /**
     * Determines whether or not the a file matches a custom file type defined
     * by Autopsy.
     *
     * @param file The file to test.
     *
     * @return The file type name string or null, if no match is detected.
     *
     * @throws TskCoreException
     */
    private String detectAutopsyDefinedType(AbstractFile file) throws TskCoreException {
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
     * @deprecated Use getFileType instead and use AbstractFile.getMIMEType
     * instead of querying the blackboard.
     */
    @Deprecated
    public String detectAndPostToBlackboard(AbstractFile file) throws TskCoreException {
        return detect(file, true);
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
     * @deprecated
     */
    @Deprecated
    public String getFileType(AbstractFile file) throws TskCoreException {
        return detect(file, true);
    }

}
