/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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

public class TikaFileTypeDetector {

    private static final Logger logger = Logger.getLogger(TikaFileTypeDetector.class.getName());
    private static final Tika tikaInst = new Tika();
    private static final int BUFFER_SIZE = 64 * 1024;
    private final UserDefinedFileTypeDetector userDefinedFileTypeIdentifier;
    private boolean userDefinedTypesLoaded;
    private final byte buffer[] = new byte[BUFFER_SIZE];

    public TikaFileTypeDetector() {
        userDefinedFileTypeIdentifier = new UserDefinedFileTypeDetector();
        try {
            userDefinedFileTypeIdentifier.loadFileTypes();
            userDefinedTypesLoaded = true;

        } catch (UserDefinedFileTypesManager.UserDefinedFileTypesException ex) {
            /**
             * There is an unfortunate design flaw in
             * UserDefinedFileTypeDetector. If the loadFileTypes() method
             * throws, the predefined types will still have been loaded, so this
             * exception does not mean that the UserDefinedFileTypeDetector
             * should not be used.
             */
            logger.log(Level.SEVERE, "Failed to load user-defined file types", ex); //NON-NLS
        }
    }

    /**
     * Indicates whether or not loading of user-defined file types, if any, was
     * successful.
     *
     * @return True or false.
     */
    public boolean userDefinedTypesAreLoaded() {
        return userDefinedTypesLoaded;
    }

    /**
    /**
     * Detect the MIME type of a file, posting it to the blackboard if detection succeeds.
     *
     * @param file The file to test.
     * @param moduleName The name of the module posting to the blackboard.
     * @return The MIME type name id detection was successful, null otherwise.
     * @throws TskCoreException if there is an error posting to the blackboard.
     */
    public synchronized String detectAndPostToBlackboard(AbstractFile file, String moduleName) throws TskCoreException {
        String mimeType = detect(file);
        if (mimeType != null) {
            /**
             * Add the file type attribute to the general info artifact. Note
             * that no property change is fired for this blackboard posting
             * because general info artifacts are different from other
             * artifacts, e.g., they are not displayed in the results tree.
             */
            BlackboardArtifact getInfoArt = file.getGenInfoArtifact();
            BlackboardAttribute batt = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID(), moduleName, mimeType);
            getInfoArt.addAttribute(batt);
        }
        return mimeType;    
    }    
    
    /**
     * Detect the MIME type of a file, posting it to the blackboard if detection succeeds.
     *
     * @deprecated Use detectAndPostToBlackboard instead.
     * @param file The file to test.
     * @return The MIME type name id detection was successful, null otherwise.
     * @throws TskCoreException if there is an error posting to the blackboard.
     */
    @Deprecated
    public synchronized String detectAndSave(AbstractFile file) throws TskCoreException {
        return detectAndPostToBlackboard(file, FileTypeIdModuleFactory.getModuleName());
    }

    /**
     * Detect the MIME type of a file.
     *
     * @param file The file to test.
     * @return The MIME type name id detection was successful, null otherwise.
     * @throws TskCoreException
     */
    public synchronized String detect(AbstractFile file) {
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

                String mimetype = tikaInst.detect(buf, file.getName());
                // Remove tika's name out of the general types like msoffice and ooxml
                return mimetype.replace("tika-", ""); //NON-NLS
            } catch (Exception ex) {
                //do nothing
            }
        }
        return fileType;
    }

    /**
     * Determines whether or not the a file matches a user-defined file type.
     *
     * @param file The file to test.
     * @return The file type name string or null, if no match is detected.
     */
    private String detectUserDefinedType(AbstractFile file) {
        String fileTypeName = null;
        FileType fileType = this.userDefinedFileTypeIdentifier.identify(file);
        if (null != fileType) {
            fileTypeName = fileType.getMimeType();
            if (fileType.alertOnMatch()) {
                BlackboardArtifact artifact;
                try {
                    artifact = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                    BlackboardAttribute setNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), FileTypeIdModuleFactory.getModuleName(), fileType.getFilesSetName());
                    artifact.addAttribute(setNameAttribute);

                    /**
                     * Use the MIME type as the category, i.e., the rule that
                     * determined this file belongs to the interesting files
                     * set.
                     */
                    BlackboardAttribute ruleNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID(), FileTypeIdModuleFactory.getModuleName(), fileType.getMimeType());
                    artifact.addAttribute(ruleNameAttribute);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error creating TSK_INTERESTING_FILE_HIT artifact", ex); //NON-NLS
                }
            }
        }
        return fileTypeName;
    }

    /**
     * Validate if a given mime type is in the registry. For Tika, we remove the
     * string "tika" from all MIME names, e.g. use "application/x-msoffice" NOT
     * "application/x-tika-msoffice"
     *
     * @deprecated Use TikaFileTypeDetector.mimeTypeIsDetectable instead.
     * @param mimeType Full string of mime type, e.g. "text/html"
     * @return true if detectable
     */
    @Deprecated
    public boolean isMimeTypeDetectable(String mimeType
    ) {
        return mimeTypeIsDetectable(mimeType);
    }

    /**
     * Determines whether or not a given MIME type is in the MIME type registry.
     * Note that when detection is actually attempted, the substring "tika" will
     * be removed from the returned MIME type name, e.g.
     * "application/x-msoffice" will be returned instead of
     * "application/x-tika-msoffice."
     *
     * @param mimeType The MIME type name, e.g. "text/html", to look up.
     * @return True if MIME type is detectable.
     */
    public static boolean mimeTypeIsDetectable(String mimeType) {
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
}
