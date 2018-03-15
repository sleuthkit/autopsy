/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.experimental.autoingest.FileExportRuleSet.Rule;

/**
 * Exports the files that satisfy user-defined file export rules from a set of
 * data sources associated with a device.
 */
final class FileExporter {

    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    // File Exporter requires File Type Identification and Hash Lookup modules to run beforehand.
    private static final List<String> REQUIRED_MODULE_CANONICAL_NAME = Arrays.asList("org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory", "org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory");
    private String deviceId;
    private FileExportSettings settings;
    private Path filesDirPath;
    private Path reportsDirPath;
    private JsonFactory jsonGeneratorFactory;
    private JsonGenerator masterCatalog;
    private Map<String, JsonGenerator> ruleNamesToCatalogs;
    private List<Path> flagFilePaths;
    
    FileExporter() throws FileExportException {
        try {
            settings = FileExportSettings.load();
        } catch (FileExportSettings.PersistenceException ex) {
            throw new FileExportException("Error initializing File Exporter", ex);
        }
    }
    
    boolean isEnabled() {
        return settings.getFileExportEnabledState();
    }

    /**
     * Exports the files that satisfy user-defined file export rules from a set
     * of data sources associated with a device.
     *
     * @param deviceId    The device id.
     * @param dataSources The data sources.
     * @param cancelCheck A function used to check if the file AutoInjectJob process
     *                    should be terminated.
     * @throws FileExportException if there is an error in the export process.
     */
    void process(String deviceId, List<Content> dataSources, Supplier<Boolean> cancelCheck) throws FileExportException {
        this.deviceId = deviceId;
        try {            
            if (!isEnabled() || cancelCheck.get()) {
                return;
            }
            // File Exporter requires several ingest modules to run beforehand.
            // Verify that these modules were enabled for the current data source(s)
            if (!verifyPrerequisites(dataSources)) {
                // throw exception to pause auto ingest
                throw new FileExportException("File Exporter prerequisite ingest modules were not turned on");
            }
            
            setUp();
            for (Content dataSource : dataSources) {
                Map<Long, List<String>> fileIdsToRuleNames = evaluateRules(dataSource, cancelCheck);
                if (cancelCheck.get()) {
                    break;
                }
                exportFiles(fileIdsToRuleNames, cancelCheck);
            }
            closeCatalogs();
            writeFlagFiles();
        } catch (FileExportSettings.PersistenceException | FileExportRuleSet.ExportRulesException | TskCoreException | IOException | NoCurrentCaseException ex) {
            throw new FileExportException("Error occurred during file export", ex);
        }
    }
    
    /**
     * Verifies that all AFE prerequisite ingest modules were enabled for all
     * input data sources
     *
     * @param dataSources List of data sources to check
     *
     * @return True if all AFE prerequisite ingest modules were enabled,
     *         false otherwise
     *
     * @throws org.sleuthkit.autopsy.autoingest.FileExporter.FileExportException
     */
    private boolean verifyPrerequisites(List<Content> dataSources) throws FileExportException {
        SleuthkitCase skCase;
        try {
            skCase = Case.getOpenCase().getSleuthkitCase();
        } catch (NoCurrentCaseException ex) {
            throw new FileExportException("Exception while getting open case.", ex);
        }
        List<IngestJobInfo> ingestJobs = new ArrayList<>();
        try {
            // all ingest jobs that were processed as part of this case
            ingestJobs = skCase.getIngestJobs();
        } catch (TskCoreException ex) {
            throw new FileExportException("Failed to obtain ingest jobs", ex);
        }

        // for each data source
        for (Content dataSource : dataSources) {
            // verify that all required modules were enabled            
            for (String requiredModuleCanonicalName : REQUIRED_MODULE_CANONICAL_NAME) {
                boolean requiredModuleWasEnabled = false;
                /* NOTE: there could have been multiple ingest jobs performed for 
                each data source. We have to loop over all ingest jobs for the data
                source to check whether the required ingest module was enabled 
                in either one of them. */
                for (IngestJobInfo ingestJob : ingestJobs) {
                    if (ingestJob.getStatus() != IngestJobInfo.IngestJobStatusType.COMPLETED) {
                        continue;
                    }
                    
                    if (dataSource.getId() != ingestJob.getObjectId()) {
                        // ingest job was for a differnt data source
                        continue;
                    }

                    // loop over job's ingest module list to check whether it contains the required module
                    if (isRequiredModuleEnabled(requiredModuleCanonicalName, ingestJob)) {
                        requiredModuleWasEnabled = true;
                        break;
                    }
                }
                if (!requiredModuleWasEnabled) {
                    // required module did not run
                    return false;
                }
            }
        }
        // if we are here then all requirements were met
        return true;
    }
    
    /**
     * Loop over ingest job's ingest module list to check whether it contains
     * the required module
     *
     * @param requiredModuleCanonicalName Canonical name of the ingest module to
     *                                    look for
     * @param ingestJob                   Ingest job object
     *
     * @return True if ingest job contained required ingest module, false
     *         otherwise
     */
    private boolean isRequiredModuleEnabled(String requiredModuleCanonicalName, IngestJobInfo ingestJob) {
        for (IngestModuleInfo ingestModuleInfo : ingestJob.getIngestModuleInfo()) {
            String canonicalName = ingestModuleInfo.getUniqueName().split("-")[0];
            if (canonicalName.equals(requiredModuleCanonicalName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets up the export process by loading user settings, creating output
     * directories, creating the JSON generator for the master files catalog,
     * and determining the flag file paths.
     *
     * @throws FileExportSettings.PersistenceException If there is a problem
     *                                                 loading the settings.
     * @throws IOException                             If there is a problem
     *                                                 creating the export
     *                                                 directories or the JSON
     *                                                 generator.
     */
    private void setUp() throws FileExportSettings.PersistenceException, IOException {
        /*
         * Create the file export and report root directory paths.
         */
        filesDirPath = settings.getFilesRootDirectory();
        filesDirPath = filesDirPath.resolve(deviceId);
        reportsDirPath = settings.getReportsRootDirectory();
        reportsDirPath = reportsDirPath.resolve(deviceId);

        /*
         * Delete the results of a previous run, if any. Results deletion cleans
         * up for a crashed auto ingest job.
         */
        FileUtil.deleteDir(reportsDirPath.toFile());
        FileUtil.deleteDir(filesDirPath.toFile());

        /*
         * Create the file export and report root directories.
         */
        Files.createDirectories(filesDirPath);
        Files.createDirectories(reportsDirPath);

        /*
         * Create the JSON generator for the master catalog of exported files
         * and a map to hold the JSON generators for the rule catalogs.
         */
        jsonGeneratorFactory = new JsonFactory();
        jsonGeneratorFactory.setRootValueSeparator("\r\n");
        String catalogName = settings.getMasterCatalogName();
        String catalogDir = catalogName.substring(0, 1).toUpperCase() + catalogName.substring(1);
        Path catalogPath = Paths.get(reportsDirPath.toString(), catalogDir, catalogName + ".json");
        Files.createDirectories(catalogPath.getParent());
        File catalogFile = catalogPath.toFile();
        masterCatalog = jsonGeneratorFactory.createGenerator(catalogFile, JsonEncoding.UTF8);
        ruleNamesToCatalogs = new HashMap<>();

        /*
         * Set up the paths for the flag files that are written to signal export
         * is complete for thsi device.
         */
        flagFilePaths = new ArrayList<>();
        flagFilePaths.add(Paths.get(filesDirPath.toString(), settings.getExportCompletedFlagFileName()));
        flagFilePaths.add(Paths.get(reportsDirPath.toString(), catalogDir, settings.getExportCompletedFlagFileName()));
        flagFilePaths.add(Paths.get(reportsDirPath.toString(), settings.getRulesEvaluatedFlagFileName()));
    }

    /**
     * Evaluates each file export rule to produce a map that associates the file
     * id of each file to be exported with a list of the names of the rules
     * satisfied by the file.
     *
     * @return The map of file ids to rule name lists.
     */
    /**
     * Evaluates each file export rule for a data source to produce a map that
     * associates the file id of each file to be exported with a list of the
     * names of the rules satisfied by the file.
     *
     * @param dataSource The data source.
     * @param cancelCheck A function used to check if the file AutoInjectJob process
     *                    should be terminated.
     *
     * @return The map of file ids to rule name lists.
     *
     * @throws FileExportRuleSet.ExportRulesException If there is a problem
     *                                                evaluating a rule.
     */
    private Map<Long, List<String>> evaluateRules(Content dataSource, Supplier<Boolean> cancelCheck) throws FileExportRuleSet.ExportRulesException {
        TreeMap<String, FileExportRuleSet> ruleSets = settings.getRuleSets();
        Map<Long, List<String>> fileIdsToRuleNames = new HashMap<>();
        for (FileExportRuleSet ruleSet : ruleSets.values()) {
            for (Rule rule : ruleSet.getRules().values()) {
                if (cancelCheck.get()) {
                    return fileIdsToRuleNames;
                }
                List<Long> fileIds = rule.evaluate(dataSource.getId());
                for (Long fileId : fileIds) {
                    List<String> ruleList;
                    if (!fileIdsToRuleNames.containsKey(fileId)) {
                        ruleList = new ArrayList<>();
                        fileIdsToRuleNames.put(fileId, ruleList);
                    } else {
                        ruleList = fileIdsToRuleNames.get(fileId);
                    }
                    ruleList.add(rule.getName());
                }
            }
        }
        return fileIdsToRuleNames;
    }

    /**
     * Writes each file to be exported to secondary storage and makes entries
     * for the file in the master catalog and the catalogs of the export rules
     * the file satisfied.
     *
     * @param fileIdsToRuleNames The map of file ids to rule name lists.
     * @param cancelCheck A function used to check if the file write process
     *                    should be terminated.
     *
     * @throws TskCoreException If there is a problem querying file metadata or
     *                          getting file content.
     * @throws IOException      If there is a problem writing a file to
     *                          secondary storage.
     */
    private void exportFiles(Map<Long, List<String>> fileIdsToRuleNames, Supplier<Boolean> cancelCheck) throws TskCoreException, IOException, NoCurrentCaseException {
        for (Map.Entry<Long, List<String>> entry : fileIdsToRuleNames.entrySet()) {
           if (cancelCheck.get()) {
              return;
           }
           exportFile(entry.getKey(), entry.getValue(), cancelCheck);
        }
    }

    /**
     * Writes a file to secondary storage and makes entries for the file in the
     * master catalog and the catalogs of the export rules the file satisfied.
     *
     * @param fileId    The id of the file to export.
     * @param ruleNames The names of the export rules the file satisfied.
     * @param progress  The progress reporter for this module.
     * @param cancelCheck A function used to check if the file write process
     *                    should be terminated.
     *
     * @throws TskCoreException If there is a problem querying file metadata or
     *                          getting file content.
     * @throws IOException      If there is a problem writing the file to
     *                          storage.
     */
    private void exportFile(Long fileId, List<String> ruleNames, Supplier<Boolean> cancelCheck) throws TskCoreException, IOException, NoCurrentCaseException {
        AbstractFile file = Case.getOpenCase().getSleuthkitCase().getAbstractFileById(fileId);
        if (!shouldExportFile(file)) {
            return;
        }
        Map<BlackboardArtifact, List<BlackboardAttribute>> artifactsToAttributes = new HashMap<>();
        List<BlackboardArtifact> artifacts = file.getAllArtifacts();
        for (BlackboardArtifact artifact : artifacts) {
            artifactsToAttributes.put(artifact, artifact.getAttributes());
        }
        Path filePath = exportFileToSecondaryStorage(file, cancelCheck);
        if (filePath == null) {
            return;
        }
        addFileToCatalog(file, artifactsToAttributes, filePath, masterCatalog);
        for (String ruleName : ruleNames) {
            JsonGenerator ruleCatalog = this.ruleNamesToCatalogs.get(ruleName);
            if (null == ruleCatalog) {
                Path catalogPath = Paths.get(reportsDirPath.toString(), ruleName, "catalog.json");
                Files.createDirectories(catalogPath.getParent());
                File catalogFile = catalogPath.toFile();
                ruleCatalog = jsonGeneratorFactory.createGenerator(catalogFile, JsonEncoding.UTF8);
                ruleNamesToCatalogs.put(ruleName, ruleCatalog);
            }
            addFileToCatalog(file, artifactsToAttributes, filePath, ruleCatalog);
        }
    }

    /**
     * Determines whether or not a file should be exported, even though it
     * satisfies a file export rule. Unallocated space files, pseudo-files, and
     * special NTFS or FAT file system files are not eligible for export.
     *
     * @param candidateFile The file.
     *
     * @return True or false.
     *
     * @throws TskCoreException If there is a problem querying file metadata.
     */
    private static boolean shouldExportFile(AbstractFile candidateFile) throws TskCoreException {
        if (candidateFile instanceof org.sleuthkit.datamodel.File) {
            /*
             * Is the file in an NTFS or FAT file system?
             */
            org.sleuthkit.datamodel.File file = (org.sleuthkit.datamodel.File) candidateFile;
            TskData.TSK_FS_TYPE_ENUM fileSystemType = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_UNSUPP;
            FileSystem fileSystem = file.getFileSystem();
            if (null != fileSystem) {
                fileSystemType = fileSystem.getFsType();
            }
            if ((fileSystemType.getValue() & FAT_NTFS_FLAGS) == 0) {
                return true;
            }

            /*
             * Is the NTFS or FAT file in the root directory?
             */
            AbstractFile parent = file.getParentDirectory();
            boolean isInRootDir = parent.isRoot();

            /*
             * Check its meta-address and check its name for the '$' character
             * and a ':' character (not a default attribute).
             */
            if (isInRootDir && file.getMetaAddr() < 32) {
                String name = file.getName();
                if (name.length() > 0 && name.charAt(0) == '$' && name.contains(":")) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Writes a file to a "hash tree" in secondary storage. For example, a file
     * with MD5 hash d131dd02c5e6eec46e4bc422aef54eb4 and MIME type text/html
     * would be written to the following location:
     *
     * outputDir/text-html/D1/31/DD/02/D131DD02C5E6EEC4
     *
     * @param file The file to export.
     * @param cancelCheck A function used to check if the file write process
     *                    should be terminated.
     *
     * @return The path to the exported file.
     *
     * @throws TskCoreException If there is a problem reading from the case
     *                          database.
     * @throws IOException      If the file cannot be written.
     */
    private Path exportFileToSecondaryStorage(AbstractFile file, Supplier<Boolean> cancelCheck) throws TskCoreException, IOException {
        /*
         * Get the MIME type of the file to be used as a path component.
         */
        String mimeType = file.getMIMEType();

        /*
         * Get the MD5 hash of the file to be used for path components.
         */
        String md5 = file.getMd5Hash().toUpperCase();

        /*
         * Construct the export path and export the file.
         */
        Path exportFilePath = Paths.get(this.filesDirPath.toString(),
                mimeType.replace('/', '-'),
                md5.substring(0, 2),
                md5.substring(2, 4),
                md5.substring(4, 6),
                md5.substring(6, 8),
                md5);
        File exportFile = exportFilePath.toFile();
        if (!exportFile.exists()) {
            Files.createDirectories(exportFilePath.getParent());
            ContentUtils.writeToFile(file, exportFile, cancelCheck);
            if (cancelCheck.get()) {
                Files.delete(exportFilePath);
                return null;
            }
        }

        return exportFilePath;
    }

    /**
     * Adds an exported file to a catalog.
     *
     * @param file                  The file.
     * @param artifactsToAttributes The artifacts associated with the file.
     * @param filePath              The path to the exported file.
     * @param catalog               The catalog.
     *
     * @throws IOException       If there is an error while writing to the
     *                           catalog.
     * @throws TskCoreExceptiion If there is a problem querying the case
     *                           database.
     */
    private void addFileToCatalog(AbstractFile file, Map<BlackboardArtifact, List<BlackboardAttribute>> artifactsToAttributes, Path filePath, JsonGenerator catalog) throws IOException, TskCoreException {
        catalog.writeStartObject();
        String md5 = file.getMd5Hash().toUpperCase();
        catalog.writeFieldName(md5);
        catalog.writeStartObject();
        catalog.writeStringField("Filename", filePath.toString());
        catalog.writeStringField("Type", file.getMIMEType());
        catalog.writeStringField("MD5", md5);
        catalog.writeFieldName("File data");
        catalog.writeStartObject();

        catalog.writeFieldName("Modified");
        catalog.writeStartArray();
        catalog.writeString(file.getMtimeAsDate());
        catalog.writeEndArray();

        catalog.writeFieldName("Changed");
        catalog.writeStartArray();
        catalog.writeString(file.getCtimeAsDate());
        catalog.writeEndArray();

        catalog.writeFieldName("Accessed");
        catalog.writeStartArray();
        catalog.writeString(file.getAtimeAsDate());
        catalog.writeEndArray();

        catalog.writeFieldName("Created");
        catalog.writeStartArray();
        catalog.writeString(file.getCrtimeAsDate());
        catalog.writeEndArray();

        catalog.writeFieldName("Extension");
        catalog.writeStartArray();
        catalog.writeString(file.getNameExtension());
        catalog.writeEndArray();

        catalog.writeFieldName("Filename");
        catalog.writeStartArray();
        catalog.writeString(file.getName());
        catalog.writeEndArray();

        catalog.writeFieldName("Size");
        catalog.writeStartArray();
        catalog.writeString(Long.toString(file.getSize()));
        catalog.writeEndArray();

        catalog.writeFieldName("Source Path");
        catalog.writeStartArray();
        catalog.writeString(file.getParentPath() + "/" + file.getName());
        catalog.writeEndArray();

        catalog.writeFieldName("Flags (Dir)");
        catalog.writeStartArray();
        catalog.writeString(file.getDirFlagAsString());
        catalog.writeEndArray();

        catalog.writeFieldName("Flags (Meta)");
        catalog.writeStartArray();
        catalog.writeString(file.getMetaFlagsAsString());
        catalog.writeEndArray();

        catalog.writeFieldName("Mode");
        catalog.writeStartArray();
        catalog.writeString(file.getModesAsString());
        catalog.writeEndArray();

        catalog.writeFieldName("User ID");
        catalog.writeStartArray();
        catalog.writeString(Integer.toString(file.getUid()));
        catalog.writeEndArray();

        catalog.writeFieldName("Group ID");
        catalog.writeStartArray();
        catalog.writeString(Integer.toString(file.getGid()));
        catalog.writeEndArray();

        catalog.writeFieldName("Meta Addr");
        catalog.writeStartArray();
        catalog.writeString(Long.toString(file.getMetaAddr()));
        catalog.writeEndArray();

        catalog.writeFieldName("Attr Addr");
        catalog.writeStartArray();
        catalog.writeString(Long.toString(file.getAttrType().getValue()) + "-" + file.getAttributeId());
        catalog.writeEndArray();

        catalog.writeFieldName("Dir Type");
        catalog.writeStartArray();
        catalog.writeString(file.getDirType().getLabel());
        catalog.writeEndArray();

        catalog.writeFieldName("Meta Type");
        catalog.writeStartArray();
        catalog.writeString(file.getMetaType().toString());
        catalog.writeEndArray();

        catalog.writeFieldName("Known");
        catalog.writeStartArray();
        catalog.writeString(file.getKnown().getName());
        catalog.writeEndArray();

        catalog.writeEndObject();

        for (Map.Entry<BlackboardArtifact, List<BlackboardAttribute>> entry : artifactsToAttributes.entrySet()) {
            for (BlackboardAttribute attr : entry.getValue()) {
                catalog.writeFieldName(entry.getKey().getArtifactTypeName());
                catalog.writeStartObject();
                catalog.writeFieldName(attr.getAttributeType().getTypeName());
                catalog.writeStartArray();
                catalog.writeString(attr.getDisplayString());
                catalog.writeEndArray();
                catalog.writeEndObject();
            }
        }

        catalog.writeEndObject();
        catalog.writeEndObject();
    }

    /**
     * Closes all catalogs opened during export.
     *
     * @throws IOException If there is a problem closing a catalog.
     */
    private void closeCatalogs() throws IOException {
        masterCatalog.close();
        for (JsonGenerator catalog : this.ruleNamesToCatalogs.values()) {
            catalog.close();
        }

    }

    /**
     * Writes the flag files that signal export is completed.
     *
     * @throws IOException If there is a problem writing a flag file.
     */
    private void writeFlagFiles() throws IOException {
        for (Path flagFile : flagFilePaths) {
            Files.createFile(flagFile);
        }
    }

    /**
     * Exception thrown to clients if there is a problem exporting files.
     */
    final static class FileExportException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception.
         *
         * @param message The exception message.
         */
        private FileExportException(String message) {
            super(message);
        }

        /**
         * Constructs an exception.
         *
         * @param message The exception message.
         * @param cause   The exception cause.
         */
        private FileExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
