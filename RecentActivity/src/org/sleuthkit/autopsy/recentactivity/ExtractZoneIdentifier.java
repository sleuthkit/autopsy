/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extract the <i>:Zone.Indentifier<i> files and create Artifact objects for them.
 */
final class ExtractZoneIdentifier extends Extract{
    
    private static final Logger LOG = Logger.getLogger(ExtractEdge.class.getName());
    
    private final IngestServices services = IngestServices.getInstance();
    
    private static final String ZONE_IDENIFIER_FILE = "%:Zone.Identifier"; //NON-NLS
    private static final String ZONE_IDENIFIER = ":Zone.Identifier"; //NON-NLS

    @Override
    void process(Content dataSource, IngestJobContext context) {
       
        List<AbstractFile> zoneFiles = null;
        try{
            zoneFiles = findZoneFiles(dataSource);
        } catch(TskCoreException ex){
            
        }
        
        if(zoneFiles == null || zoneFiles.isEmpty())
            return;
        
        Collection<BlackboardArtifact> sourceArtifacts = new ArrayList<>();
        Collection<BlackboardArtifact> downloadArtifacts = new ArrayList<>();
        
        for(AbstractFile zoneFile: zoneFiles){
            processZoneFile(context, dataSource, zoneFile, sourceArtifacts, downloadArtifacts); 
        }
        
        if (!sourceArtifacts.isEmpty()) {
            services.fireModuleDataEvent(new ModuleDataEvent(
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE, sourceArtifacts));
        }
        
        if (!downloadArtifacts.isEmpty()) {
            services.fireModuleDataEvent(new ModuleDataEvent(
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadArtifacts));
        }
    }
    
    /**
     * Process a single Zone Identifier file.
     * 
     * @param context IngetJobContext
     * @param dataSource Content
     * @param zoneFile Zone Indentifier file
     * @param sourceArtifacts List for TSK_DOWNLOAD_SOURCE artifacts
     * @param downloadArtifacts List for TSK_WEB_DOWNLOAD aritfacts
     */
    private void processZoneFile(IngestJobContext context, Content dataSource,
            AbstractFile zoneFile, Collection<BlackboardArtifact> sourceArtifacts, 
            Collection<BlackboardArtifact> downloadArtifacts){

        File tempFile = null;
        ZoneIdentifierInfo zoneInfo = null;
        try{
            tempFile = createTemporaryZoneFile(context, zoneFile);
        } catch(IOException ex) {
            String message = String.format("Unable to create temporary File for %s", zoneFile.getName()); //NON-NLS
            LOG.log(Level.WARNING, message, ex);
        } 

        try{
            zoneInfo = new ZoneIdentifierInfo(tempFile);
        } catch(IOException ex){
            String message = String.format("Unable to parse temporary File for %s", zoneFile.getName()); //NON-NLS
            LOG.log(Level.WARNING, message, ex);
        } finally {
            if(tempFile != null){
                tempFile.delete();
            }
        }

        if(zoneInfo == null) {
            return;
        }

        AbstractFile downloadFile = null;
        try{
            downloadFile = getDownloadFile(dataSource, zoneFile);
        } catch(TskCoreException ex) {

        }

        if(downloadFile != null){
            BlackboardArtifact sourcebba = createDownloadSourceArtifact(downloadFile, zoneInfo);
            if(sourcebba != null){
                sourceArtifacts.add(sourcebba);
            }

            try{
                if(downloadFile.getArtifactsCount(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD) == 0){
                    BlackboardArtifact downloadbba = createDownloadArtifact(zoneFile, zoneInfo);
                    if(downloadbba != null){
                        downloadArtifacts.add(downloadbba);
                    }
                }
            } catch(TskCoreException ex) {

            }
        }
    }
    
    /**
     * Returns a list of all the file that end in <i>:Zone.Identifier<i>
     * 
     * @param dataSource Content
     * 
     * @return A list of zone identifier files
     * 
     * @throws TskCoreException 
     */
    private List<AbstractFile> findZoneFiles(Content dataSource) throws TskCoreException {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager
                = currentCase.getServices().getFileManager();
        
        return fileManager.findFiles(dataSource, ZONE_IDENIFIER_FILE);
    }
    
    /**
     * Finds the file that is represently by the Zone Indentifer file.
     * 
     * @param dataSource Content
     * @param zoneFile The zone identifier case file
     * 
     * @return The downloaded file or null if a file was not found
     * 
     * @throws TskCoreException 
     */
    private AbstractFile getDownloadFile(Content dataSource, AbstractFile zoneFile) throws TskCoreException {
        AbstractFile downloadFile = null;
        
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager
                = currentCase.getServices().getFileManager();

        String downloadFileName = zoneFile.getName().replace(ZONE_IDENIFIER, ""); //NON-NLS

        List<AbstractFile> fileList = fileManager.findFiles(dataSource, downloadFileName, zoneFile.getParentPath());

        if(fileList.size() == 1) {
            downloadFile =  fileList.get(0);
        }

        return downloadFile;
    }
    
    /**
     * Creates a Download Source Artifact for the given ZoneIdentifierInfo object.
     * 
     * @param downloadFile AbstractFile representing the file downloaded, not the zone indentifier file.
     * @param zoneInfo Zone Indentifer file wrapper object
     * 
     * @return TSK_DOWNLOAD_SOURCE object for given parameters
     */
    private BlackboardArtifact createDownloadSourceArtifact(AbstractFile downloadFile, ZoneIdentifierInfo zoneInfo){
        
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
        RecentActivityExtracterModuleFactory.getModuleName(),
        (zoneInfo.getURL() != null) ? zoneInfo.getURL() : "")); //NON-NLS
        
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
        RecentActivityExtracterModuleFactory.getModuleName(),
        (zoneInfo.getURL() != null) ? NetworkUtils.extractDomain(zoneInfo.getURL()) : "")); //NON-NLS
        
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION,
        RecentActivityExtracterModuleFactory.getModuleName(),
        (zoneInfo.getZoneIdAsString() != null) ? zoneInfo.getZoneIdAsString() : "")); //NON-NLS
        
        return addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE, downloadFile, bbattributes);
    }
    
    /**
     * Create a TSK_WEB_DOWNLOAD Artifact for the given zone indentifier file.
     * 
     * @param zoneFile Zone identifier file
     * @param zoneInfo ZoneIdentifierInfo file wrapper object
     * 
     * @return BlackboardArifact for the given parameters
     */
    private BlackboardArtifact createDownloadArtifact(AbstractFile zoneFile, ZoneIdentifierInfo zoneInfo){
        
        Collection<BlackboardAttribute> bbattributes = createDownloadAttributes(
                null, null, 
                zoneInfo.getURL(), null, 
                (zoneInfo.getURL() != null ? NetworkUtils.extractDomain(zoneInfo.getURL()) : ""), 
                null);
        return addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, zoneFile, bbattributes);
    }
    
    /**
     * Creates a copy of the given zoneFile.
     * 
     * @param context IngestJobContext
     * @param file zoneFile from case
     * 
     * @return File object representing the newly created file
     * 
     * @throws IOException 
     */
    private File createTemporaryZoneFile(IngestJobContext context, AbstractFile zoneFile) throws IOException{
        // Replace the invalid character in the file name.
        String fileName = zoneFile.getName().replace(":", "_"); //NON-NLS
        
        Path tempFilePath = Paths.get(RAImageIngestModule.getRATempPath(
          getCurrentCase(), getName()), fileName + zoneFile.getId());
        java.io.File tempFile = tempFilePath.toFile();
        
        try {
            ContentUtils.writeToFile(zoneFile, tempFile, context::dataSourceIngestIsCancelled);
        } catch (IOException ex) {
            throw new IOException("Error writingToFile: " + zoneFile, ex); //NON-NLS
        }
         
        return tempFile;
    }
   
    @Messages({
        "LOCAL_MACHINE_ZONE=Local Machine Zone",
        "LOCAL_INTRANET_ZONE=Local Intranet Zone",
        "TRUSTED_ZONE=Trusted Sites Zone",
        "INTENET_ZONE=Internet Zone",
        "RESTRICTED_ZONE=Restricted Sites Zone"
    })
    
    /**
     * Wrapper class for information in the :ZoneIdentifier file.  The ZoneIdentifier
     * file has a simple format of <i>key<i>=<i>value<i>. There are four known
     * keys: ZoneId, ReferrerUrl, HostUrl, and LastWriterPackageFamilyName.  Not
     * all browsers will put all values in the file, in fact most will only supply
     * the ZoneId.  Only Edge supplies the LastWriterPackageFamilyName.
     */
    private final class ZoneIdentifierInfo{
        private static final String ZONE_ID = "zoneid"; //NON-NLS
        private static final String REFERRER_URL = "referrerurl"; //NON-NLS
        private static final String HOST_URL = "hosturl"; //NON-NLS
        private static final String FAMILY_NAME = "lastwriterpackagefamilyname"; //NON-NLS
        
        private final HashMap<String, String> dataMap = new HashMap<>();
        
        /**
         * Opens the zone file, reading for the key\value pairs and puts them
         * into a HashMap.
         * 
         * @param zoneFile The ZoneIdentifier file
         * 
         * @throws FileNotFoundException
         * @throws IOException 
         */
        ZoneIdentifierInfo(File zoneFile) throws FileNotFoundException, IOException{
            String line;
            try(BufferedReader reader = new BufferedReader(new FileReader(zoneFile))) {
                while((line = reader.readLine()) != null){
                    String[] tokens = line.split("=");
                    
                    if(tokens.length < 2){
                        continue; //Nothing interesting in this line
                    }
                    
                    dataMap.put(tokens[0].trim().toLowerCase(), tokens[1].trim());
                }
            }  
        }
        
        /**
         * Returns the integer zone id
         * 
         * @return interger zone id or -1 if unknown
         */
        int getZoneId(){
            int zoneValue = -1;
            String value = dataMap.get(ZONE_ID);
            if(value != null){
                zoneValue = Integer.parseInt(value);
            }
            
            return zoneValue;
        }
        
        /**
         * Get the string description of the zone id.
         * 
         * @return String description or null if a zone id was not found
         */
        String getZoneIdAsString(){
            switch(getZoneId()){
                case 0:
                    return Bundle.LOCAL_MACHINE_ZONE();
                case 1:
                    return Bundle.LOCAL_INTRANET_ZONE();
                case 2:
                    return Bundle.TRUSTED_ZONE();
                case 3:
                    return Bundle.INTENET_ZONE();
                case 4:
                    return Bundle.RESTRICTED_ZONE();
                default:
                    return null;
            }
        }
        
        /**
         * Get the URL from which the file was downloaded.
         * 
         * @return String url or null if a host url was not found
         */
        String getURL(){
            return dataMap.get(HOST_URL);
        }
        
        /**
         * Get the referrer url.
         * 
         * @return String url or null if a host url was not found
         */
        String getReferrer(){
           return dataMap.get(REFERRER_URL);
        }
        
        /**
         * Gets the string value for the key LastWriterPackageFamilyName.
         * 
         * @return String value or null if the value was not found
         */
        String getFamilyName(){
            return dataMap.get(FAMILY_NAME);
        }
    }
    
}
