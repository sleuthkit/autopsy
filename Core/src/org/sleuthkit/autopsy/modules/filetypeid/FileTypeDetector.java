/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2016 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Level;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Detects the MIME type of a file by an inspection of its contents, using both
 * user-defined type definitions and Tika.
 */
public class FileTypeDetector {

    private static final Tika tika = new Tika();
    private static final int BUFFER_SIZE = 64 * 1024;
    private final byte buffer[] = new byte[BUFFER_SIZE];
    private final List<FileType> userDefinedFileTypes;
    private static final Logger logger = Logger.getLogger(FileTypeDetector.class.getName());

    /**
     * Constructs an object that detects the MIME type of a file by an
     * inspection of its contents, using both user-defined type definitions and
     * Tika.
     *
     * @throws FileTypeDetectorInitException if an initialization error occurs,
     *                                       e.g., user-defined file type
     *                                       definitions exist but cannot be
     *                                       loaded.
     */
    public FileTypeDetector() throws FileTypeDetectorInitException {
        try {
            userDefinedFileTypes = UserDefinedFileTypesManager.getInstance().getFileTypes();
        } catch (UserDefinedFileTypesManager.UserDefinedFileTypesException ex) {
            throw new FileTypeDetectorInitException("Error loading user-defined file types", ex); //NON-NLS
        }
    }

    /**
     * Gets the names of the user-defined MIME types.
     *
     * @return A list of the user-defined MIME types.
     */
    public List<String> getUserDefinedTypes() {
        List<String> list = new ArrayList<>();
        if (userDefinedFileTypes != null) {
            for (FileType fileType : userDefinedFileTypes) {
                list.add(fileType.getMimeType());
            }
        }
        return list;
    }

    /**
     * Determines whether or not a given MIME type is detectable.
     *
     * @param mimeType The MIME type name (e.g., "text/html").
     *
     * @return True if the given MIME type is detectable.
     */
    public boolean isDetectable(String mimeType) {
        return isDetectableAsUserDefinedType(mimeType) || isDetectableByTika(mimeType);
    }

    /**
     * Determines whether or not a given MIME type is detectable as a
     * user-defined MIME type.
     *
     * @param mimeType The MIME type name (e.g., "text/html").
     *
     * @return True if the given MIME type is detectable.
     */
    private boolean isDetectableAsUserDefinedType(String mimeType) {
        for (FileType fileType : userDefinedFileTypes) {
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
     * @return True if the given MIME type is detectable.
     */
    private boolean isDetectableByTika(String mimeType) {
        String[] split = mimeType.split("/");
        if (split.length == 2) {
            String type = split[0];
            String subtype = split[1];
            MediaType mediaType = new MediaType(type, subtype);
            SortedSet<MediaType> m = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry().getTypes();
            return m.contains(mediaType);
        }
        return false;
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
     */
    @SuppressWarnings("deprecation")
    public String getFileType(AbstractFile file) throws TskCoreException {
        String mimeType = file.getMIMEType();
        if (null != mimeType) {
            return mimeType;
        }

        mimeType = detect(file);
        Case.getCurrentCase().getSleuthkitCase().setFileMIMEType(file, mimeType);

        /*
         * Add the file type attribute to the general info artifact. Note that
         * no property change is fired for this blackboard posting because
         * general info artifacts are different from other artifacts, e.g., they
         * are not displayed in the results tree.
         */
        BlackboardArtifact getInfoArt = file.getGenInfoArtifact();
        BlackboardAttribute batt = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG, FileTypeIdModuleFactory.getModuleName(), mimeType);
        getInfoArt.addAttribute(batt);

        return mimeType;
    }

    /**
     * Detects the MIME type of a file. The result is not added to the case
     * database.
     *
     * @param file The file to test.
     *
     * @return The MIME type name if detection was successful, null otherwise.
     *
     * @throws TskCoreException
     */
    public String detect(AbstractFile file) throws TskCoreException {
        /*
         * Consistently mark non-regular files (refer to
         * TskData.TSK_FS_META_TYPE_ENUM), zero-sized files, unallocated space,
         * and unused blocks (refer to TskData.TSK_DB_FILES_TYPE_ENUM) as
         * octet-stream.
         */
        if (!file.isFile() || file.getSize() <= 0
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)) {
            return MimeTypes.OCTET_STREAM;
        }

        String mimeType = detectUserDefinedType(file);
        if (null == mimeType) {
            try {
                byte buf[];
                int len = file.read(buffer, 0, BUFFER_SIZE);
                if (len < BUFFER_SIZE) {
                    buf = new byte[len];
                    System.arraycopy(buffer, 0, buf, 0, len);
                } else {
                    buf = buffer;
                }

                String tikaType = tika.detect(buf, file.getName());

                /**
                 * Strip out any Tika enhancements to the MIME type name.
                 */
                mimeType = tikaType.replace("tika-", ""); //NON-NLS

            } catch (Exception ignored) {
                /*
                 * This exception is swallowed and not logged rather than
                 * propagated because files in data sources are not always
                 * consistent with their file system metadata, making for read
                 * errors, and Tika can be a bit flaky at times, making this a
                 * best effort endeavor. Default to octet-stream.
                 */
                mimeType = MimeTypes.OCTET_STREAM;
            }
        }
        return mimeType;
    }

    /**
     * Determines whether or not the a file matches a user-defined or Autopsy
     * predefined file type. If a match is found and the file type definition
     * calls for an alert on a match, an interesting file hit artifact is posted
     * to the blackboard.
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
                if (fileType.alertOnMatch()) {
                    BlackboardArtifact artifact;
                    artifact = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                    BlackboardAttribute setNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, FileTypeIdModuleFactory.getModuleName(), fileType.getFilesSetName());
                    artifact.addAttribute(setNameAttribute);

                    /*
                     * Use the MIME type as the category, i.e., the rule that
                     * determined this file belongs to the interesting files
                     * set.
                     */
                    BlackboardAttribute ruleNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, FileTypeIdModuleFactory.getModuleName(), fileType.getMimeType());
                    artifact.addAttribute(ruleNameAttribute);

                    try {
                        // index the artifact for keyword search
                        Case.getCurrentCase().getServices().getBlackboard().indexArtifact(artifact);
                    } catch (Blackboard.BlackboardException | IllegalStateException ex) {
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

    public static class FileTypeDetectorInitException extends Exception {

        private static final long serialVersionUID = 1L;

        FileTypeDetectorInitException(String message) {
            super(message);
        }

        FileTypeDetectorInitException(String message, Throwable throwable) {
            super(message, throwable);
        }
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
    @SuppressWarnings("deprecation")
    public String detectAndPostToBlackboard(AbstractFile file) throws TskCoreException {
        return getFileType(file);
    }

}
