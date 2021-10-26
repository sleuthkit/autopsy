/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extract the <i>:Zone.Identifier<i> alternate data stream files. A file with a
 * <i>:Zone.Identifier<i> extension contains information about the similarly
 * named (with out zone identifier extension) downloaded file.
 */
final class ExtractZoneIdentifier extends Extract {

    private static final Logger LOG = Logger.getLogger(ExtractEdge.class.getName());

    private static final String ZONE_IDENTIFIER_FILE = "%:Zone.Identifier"; //NON-NLS
    private static final String ZONE_IDENTIFIER = ":Zone.Identifier"; //NON-NLS
    private Content dataSource;
    private final IngestJobContext context;

    @Messages({
        "ExtractZone_displayName= Zone Identifier Analyzer",
        "ExtractZone_process_errMsg_find=A failure occured while searching for :Zone.Indentifier files.",
        "ExtractZone_process_errMsg=An error occured processing ':Zone.Indentifier' files.",
        "ExtractZone_progress_Msg=Extracting :Zone.Identifer files"
    })

    ExtractZoneIdentifier(IngestJobContext context) {
        super(Bundle.ExtractZone_displayName(), context);
        this.context = context;
    }

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        progressBar.progress(Bundle.ExtractZone_progress_Msg());

        List<AbstractFile> zoneFiles = null;
        try {
            zoneFiles = currentCase.getServices().getFileManager().findFiles(dataSource, ZONE_IDENTIFIER_FILE);
        } catch (TskCoreException ex) {
            addErrorMessage(Bundle.ExtractZone_process_errMsg_find());
            LOG.log(Level.SEVERE, "Unable to find zone identifier files, exception thrown. ", ex); // NON-NLS
        }

        if (zoneFiles == null || zoneFiles.isEmpty()) {
            return;
        }

        Set<Long> knownPathIDs = null;
        try {
            knownPathIDs = getPathIDsForType(TSK_WEB_DOWNLOAD);
        } catch (TskCoreException ex) {
            addErrorMessage(Bundle.ExtractZone_process_errMsg());
            LOG.log(Level.SEVERE, "Failed to build PathIDs List for TSK_WEB_DOWNLOAD", ex); // NON-NLS
        }

        if (knownPathIDs == null) {
            return;
        }

        Collection<BlackboardArtifact> associatedObjectArtifacts = new ArrayList<>();
        Collection<BlackboardArtifact> downloadArtifacts = new ArrayList<>();

        for (AbstractFile zoneFile : zoneFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            try {
                processZoneFile(zoneFile, associatedObjectArtifacts, downloadArtifacts, knownPathIDs);
            } catch (TskCoreException ex) {
                addErrorMessage(Bundle.ExtractZone_process_errMsg());
                String message = String.format("Failed to process zone identifier file  %s", zoneFile.getName()); //NON-NLS
                LOG.log(Level.WARNING, message, ex);
            }
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(associatedObjectArtifacts);
            postArtifacts(downloadArtifacts);
        }
    }

    /**
     * Process a single Zone Identifier file.
     *
     * @param zoneFile                  Zone Identifier file
     * @param associatedObjectArtifacts List for TSK_ASSOCIATED_OBJECT artifacts
     * @param downloadArtifacts         List for TSK_WEB_DOWNLOAD artifacts
     *
     * @throws TskCoreException
     */
    private void processZoneFile(
            AbstractFile zoneFile, Collection<BlackboardArtifact> associatedObjectArtifacts,
            Collection<BlackboardArtifact> downloadArtifacts,
            Set<Long> knownPathIDs) throws TskCoreException {

        ZoneIdentifierInfo zoneInfo = null;

        try {
            zoneInfo = new ZoneIdentifierInfo(zoneFile);
        } catch (IOException ex) {
            String message = String.format("Unable to parse temporary File for %s", zoneFile.getName()); //NON-NLS
            LOG.log(Level.WARNING, message, ex);
        }

        if (zoneInfo == null) {
            return;
        }

        AbstractFile downloadFile = getDownloadFile(zoneFile);

        if (downloadFile != null) {
            // Only create a new TSK_WEB_DOWNLOAD artifact if one does not exist for downloadFile
            if (!knownPathIDs.contains(downloadFile.getId())) {
                // The zone identifier file is the parent of this artifact 
                // because it is the file we parsed to get the data
                BlackboardArtifact downloadBba = createDownloadArtifact(zoneFile, zoneInfo, downloadFile);
                downloadArtifacts.add(downloadBba);
                // create a TSK_ASSOCIATED_OBJECT for the downloaded file, associating it with the TSK_WEB_DOWNLOAD artifact.
                if (downloadFile.getArtifactsCount(TSK_ASSOCIATED_OBJECT) == 0) {
                    associatedObjectArtifacts.add(createAssociatedArtifact(downloadFile, downloadBba));
                }
            }

        }
    }

    /**
     * Find the file that the Zone.Identifier file was created alongside.
     *
     * @param zoneFile The zone identifier case file
     *
     * @return The downloaded file or null if a file was not found
     *
     * @throws TskCoreException
     */
    private AbstractFile getDownloadFile(AbstractFile zoneFile) throws TskCoreException {

        String downloadFileName = zoneFile.getName().replace(ZONE_IDENTIFIER, ""); //NON-NLS

        // The downloaded file should have been added to the database just before the
        // Zone.Identifier file, possibly with a slack file in between. We will load those files 
        // and test them first since loading files by ID will typically be much faster than
        // the fallback method of searching by file name.
        AbstractFile potentialDownloadFile = currentCase.getSleuthkitCase().getAbstractFileById(zoneFile.getId() - 1);
        if (isZoneFileMatch(zoneFile, downloadFileName, potentialDownloadFile)) {
            return potentialDownloadFile;
        }
        potentialDownloadFile = currentCase.getSleuthkitCase().getAbstractFileById(zoneFile.getId() - 2);
        if (isZoneFileMatch(zoneFile, downloadFileName, potentialDownloadFile)) {
            return potentialDownloadFile;
        }

        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> fileList = fileManager.findFilesExactName(zoneFile.getParent().getId(), downloadFileName);

        for (AbstractFile file : fileList) {
            if (isZoneFileMatch(zoneFile, downloadFileName, file)) {
                return file;
            }
        }

        return null;
    }

    /**
     * Test whether a given zoneFile is associated with another file. Criteria:
     * Metadata addresses match Names match Parent paths match
     *
     * @param zoneFile                 The zone file.
     * @param expectedDownloadFileName The expected name for the downloaded
     *                                 file.
     * @param possibleDownloadFile     The file to test against the zone file.
     *
     * @return true if possibleDownloadFile corresponds to zoneFile, false
     *         otherwise.
     */
    private boolean isZoneFileMatch(AbstractFile zoneFile, String expectedDownloadFileName, AbstractFile possibleDownloadFile) {

        if (zoneFile == null || possibleDownloadFile == null || expectedDownloadFileName == null) {
            return false;
        }

        if (zoneFile.getMetaAddr() != possibleDownloadFile.getMetaAddr()) {
            return false;
        }

        if (!expectedDownloadFileName.equals(possibleDownloadFile.getName())) {
            return false;
        }

        if (!possibleDownloadFile.getParentPath().equals(zoneFile.getParentPath())) {
            return false;
        }

        return true;
    }

    /**
     * Create a TSK_WEB_DOWNLOAD Artifact for the given zone identifier file.
     *
     * @param zoneFile     Zone identifier file
     * @param zoneInfo     ZoneIdentifierInfo file wrapper object
     * @param downloadFile The file associated with the zone identifier
     *
     * @return BlackboardArifact for the given parameters
     */
    private BlackboardArtifact createDownloadArtifact(AbstractFile zoneFile, ZoneIdentifierInfo zoneInfo, AbstractFile downloadFile) throws TskCoreException {

        String downloadFilePath = downloadFile.getParentPath() + downloadFile.getName();
        long pathID = Util.findID(dataSource, downloadFilePath);
        Collection<BlackboardAttribute> bbattributes = createDownloadAttributes(
                downloadFilePath, pathID,
                zoneInfo.getURL(), null,
                (zoneInfo.getURL() != null ? NetworkUtils.extractDomain(zoneInfo.getURL()) : ""),
                null);
        if (zoneInfo.getZoneIdAsString() != null) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT,
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    zoneInfo.getZoneIdAsString()));
        }
        return createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_DOWNLOAD, zoneFile, bbattributes);
    }

    /**
     * Creates a list of PathIDs for the given Artifact type.
     *
     * @param type BlackboardArtifact.ARTIFACT_TYPE
     *
     * @return A list of PathIDs
     *
     * @throws TskCoreException
     */
    private Set<Long> getPathIDsForType(BlackboardArtifact.ARTIFACT_TYPE type) throws TskCoreException {
        Set<Long> idList = new HashSet<>();
        for (BlackboardArtifact artifact : currentCase.getSleuthkitCase().getBlackboardArtifacts(type)) {
            BlackboardAttribute pathIDAttribute = artifact.getAttribute(new BlackboardAttribute.Type(TSK_PATH_ID));

            if (pathIDAttribute != null) {
                long contentID = pathIDAttribute.getValueLong();
                if (contentID != -1) {
                    idList.add(contentID);
                }
            }
        }
        return idList;
    }

    @Messages({
        "ExtractZone_Local_Machine=Local Machine Zone",
        "ExtractZone_Local_Intranet=Local Intranet Zone",
        "ExtractZone_Trusted=Trusted Sites Zone",
        "ExtractZone_Internet=Internet Zone",
        "ExtractZone_Restricted=Restricted Sites Zone"
    })

    /**
     * Wrapper class for information in the :ZoneIdentifier file. The
     * Zone.Identifier file has a simple format of
     * \<i\>key\<i\>=\<i\>value\<i\>. There are four known keys: ZoneId,
     * ReferrerUrl, HostUrl, and LastWriterPackageFamilyName. Not all browsers
     * will put all values in the file, in fact most will only supply the
     * ZoneId. Only Edge supplies the LastWriterPackageFamilyName.
     */
    private final static class ZoneIdentifierInfo {

        private static final String ZONE_ID = "ZoneId"; //NON-NLS
        private static final String REFERRER_URL = "ReferrerUrl"; //NON-NLS
        private static final String HOST_URL = "HostUrl"; //NON-NLS
        private static final String FAMILY_NAME = "LastWriterPackageFamilyName"; //NON-NLS
        private static String fileName;

        private final Properties properties = new Properties(null);

        /**
         * Opens the zone file, reading for the key\value pairs and puts them
         * into a HashMap.
         *
         * @param zoneFile The ZoneIdentifier file
         *
         * @throws FileNotFoundException
         * @throws IOException
         */
        ZoneIdentifierInfo(AbstractFile zoneFile) throws IOException {
            fileName = zoneFile.getName();
            // properties.load will throw IllegalArgument if unicode characters are found in the zone file.
            try {
                properties.load(new ReadContentInputStream(zoneFile));
            } catch (IllegalArgumentException ex) {
                String message = String.format("Unable to parse Zone Id for File %s", fileName); //NON-NLS
                LOG.log(Level.WARNING, message);
            }
        }

        /**
         * Get the integer zone id
         *
         * @return interger zone id or -1 if unknown
         */
        private int getZoneId() {
            int zoneValue = -1;
            String value = properties.getProperty(ZONE_ID);
            try {
                if (value != null) {
                    zoneValue = Integer.parseInt(value);
                }
            } catch (NumberFormatException ex) {
                String message = String.format("Unable to parse Zone Id for File %s", fileName); //NON-NLS
                LOG.log(Level.WARNING, message);
            }

            return zoneValue;
        }

        /**
         * Get the string description of the zone id.
         *
         * @return String description or null if a zone id was not found
         */
        private String getZoneIdAsString() {
            switch (getZoneId()) {
                case 0:
                    return Bundle.ExtractZone_Local_Machine();
                case 1:
                    return Bundle.ExtractZone_Local_Intranet();
                case 2:
                    return Bundle.ExtractZone_Trusted();
                case 3:
                    return Bundle.ExtractZone_Internet();
                case 4:
                    return Bundle.ExtractZone_Restricted();
                default:
                    return null;
            }
        }

        /**
         * Get the URL from which the file was downloaded.
         *
         * @return String url or null if a host url was not found
         */
        private String getURL() {
            return properties.getProperty(HOST_URL);
        }

        /**
         * Get the referrer url.
         *
         * @return String url or null if a host url was not found
         */
        private String getReferrer() {
            return properties.getProperty(REFERRER_URL);
        }

        /**
         * Gets the string value for the key LastWriterPackageFamilyName.
         *
         * @return String value or null if the value was not found
         */
        private String getFamilyName() {
            return properties.getProperty(FAMILY_NAME);
        }
    }

}
