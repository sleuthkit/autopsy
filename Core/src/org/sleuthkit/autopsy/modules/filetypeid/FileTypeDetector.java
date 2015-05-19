/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2015 Basis Technology Corp.
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
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Detects the type of a file by an inspection of its contents.
 */
public class FileTypeDetector {

    private static final Tika tika = new Tika();
    private static final int BUFFER_SIZE = 64 * 1024;
    private final byte buffer[] = new byte[BUFFER_SIZE];
    private final Map<String, FileType> userDefinedFileTypes;
    private static final Logger logger = Logger.getLogger(FileTypeDetector.class.getName());

    /**
     * Constructs an object that detects the type of a file by an inspection of
     * its contents.
     *
     * @throws FileTypeDetector.FileTypeDetectorInitException if an
     * initialization error occurs.
     */
    public FileTypeDetector() throws FileTypeDetectorInitException {
        try {
            userDefinedFileTypes = UserDefinedFileTypesManager.getInstance().getFileTypes();
        } catch (UserDefinedFileTypesManager.UserDefinedFileTypesException ex) {
            throw new FileTypeDetectorInitException("Error loading user-defined file types", ex); //NON-NLS
        }
    }

    /**
     * Determines whether or not a given MIME type is detectable by this
     * detector.
     *
     * @param mimeType The MIME type name, e.g. "text/html", to look up.
     * @return True if MIME type is detectable.
     */
    public boolean isDetectable(String mimeType) {
        return isDetectableAsUserDefinedType(mimeType) || isDetectableByTika(mimeType);
    }

    /**
     * Determines whether or not a given MIME type is detectable as a
     * user-defined file type.
     *
     * @param mimeType The MIME type name, e.g. "text/html", to look up.
     * @return True if MIME type is detectable.
     */
    private boolean isDetectableAsUserDefinedType(String mimeType) {
        return userDefinedFileTypes.containsKey(mimeType);
    }

    /**
     * Determines whether or not a given MIME type is detectable by Tika.
     *
     * @param mimeType The MIME type name, e.g. "text/html", to look up.
     * @return True if MIME type is detectable.
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
     * This method returns a string representing the mimetype of the provided
     * abstractFile. Blackboard-lookup is performed to check if the mimetype has
     * been already detected. If not, mimetype is determined using Apache Tika.
     *
     * @param abstractFile the file whose mimetype is to be determined.
     * @return mimetype of the abstractFile is returned. Empty String returned
     * in case of error.
     */
    public synchronized String getFileType(AbstractFile abstractFile) {
        String identifiedFileType = "";

        // check BB
        try {
            ArrayList<BlackboardAttribute> attributes = abstractFile.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) {
                identifiedFileType = attribute.getValueString();
                break;
            }
            if (identifiedFileType != null && !identifiedFileType.isEmpty()) {
                return identifiedFileType;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error performing mimetype blackboard-lookup for " + abstractFile.getName(), ex);
        }

        try {
            // check UDF and TDF
            identifiedFileType = detectAndPostToBlackboard(abstractFile);
            if (identifiedFileType != null && !identifiedFileType.isEmpty()) {
                return identifiedFileType;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error determining the mimetype for " + abstractFile.getName(), ex); // NON-NLS
            return ""; // NON-NLS
        }

        logger.log(Level.WARNING, "Unable to determine the mimetype for {0}", abstractFile.getName()); // NON-NLS
        return ""; // NON-NLS
    }

    /**
     * Detect the MIME type of a file, posting it to the blackboard if detection
     * succeeds.
     *
     * @param file The file to test.
     * @param moduleName The name of the module posting to the blackboard.
     * @return The MIME type name id detection was successful, null otherwise.
     * @throws TskCoreException if there is an error posting to the blackboard.
     */
    public synchronized String detectAndPostToBlackboard(AbstractFile file) throws TskCoreException {
        String mimeType = detect(file);
        if (null != mimeType) {
            /**
             * Add the file type attribute to the general info artifact. Note
             * that no property change is fired for this blackboard posting
             * because general info artifacts are different from other
             * artifacts, e.g., they are not displayed in the results tree.
             */
            BlackboardArtifact getInfoArt = file.getGenInfoArtifact();
            BlackboardAttribute batt = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID(), FileTypeIdModuleFactory.getModuleName(), mimeType);
            getInfoArt.addAttribute(batt);
        }
        return mimeType;
    }

    /**
     * Detect the MIME type of a file.
     *
     * @param file The file to test.
     * @return The MIME type name id detection was successful, null otherwise.
     */
    public String detect(AbstractFile file) throws TskCoreException {
        String fileType = detectUserDefinedType(file);
        if (null == fileType) {
            try {
                byte buf[];
                int len = file.read(buffer, 0, BUFFER_SIZE);
                if (len < BUFFER_SIZE) {
                    buf = new byte[len];
                    System.arraycopy(buffer, 0, buf, 0, len);
                } else {
                    buf = buffer;
                }

                String mimetype = tika.detect(buf, file.getName());

                /**
                 * Strip out any Tika enhancements to the MIME type name.
                 */
                return mimetype.replace("tika-", ""); //NON-NLS

            } catch (Exception ignored) {
                /**
                 * This exception is swallowed rather than propagated because
                 * files in images are not always consistent with their file
                 * system meta data making for read errors, and Tika can be a
                 * bit flaky at times, making this a best effort endeavor.
                 */
            }
        }
        return fileType;
    }

    /**
     * Determines whether or not the a file matches a user-defined or Autopsy
     * predefined file type. If a match is found and the file type definition
     * calls for an alert on a match, an interesting file hit artifact is posted
     * to the blackboard.
     *
     * @param file The file to test.
     * @return The file type name string or null, if no match is detected.
     */
    private String detectUserDefinedType(AbstractFile file) throws TskCoreException {
        for (FileType fileType : userDefinedFileTypes.values()) {
            if (fileType.matches(file)) {
                if (fileType.alertOnMatch()) {
                    BlackboardArtifact artifact;
                        artifact = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                        BlackboardAttribute setNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), FileTypeIdModuleFactory.getModuleName(), fileType.getFilesSetName());
                        artifact.addAttribute(setNameAttribute);

                        /**
                         * Use the MIME type as the category, i.e., the rule
                         * that determined this file belongs to the interesting
                         * files set.
                         */
                        BlackboardAttribute ruleNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID(), FileTypeIdModuleFactory.getModuleName(), fileType.getMimeType());
                        artifact.addAttribute(ruleNameAttribute);
                }
                return fileType.getMimeType();
            }
        }
        return null;
    }

    public static class FileTypeDetectorInitException extends Exception {

        FileTypeDetectorInitException(String message) {
            super(message);
        }

        FileTypeDetectorInitException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

}
