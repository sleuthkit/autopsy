/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.caseuco;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TagName;

/**
 * Generates CASE-UCO report file for a data source
 */
public final class CaseUcoFormatExporter {

    private static final Logger logger = Logger.getLogger(CaseUcoFormatExporter.class.getName());

    private static final BlackboardAttribute.Type SET_NAME = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME);
    private static final BlackboardArtifact.ARTIFACT_TYPE INTERESTING_FILE_HIT = BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT;
    private static final BlackboardArtifact.ARTIFACT_TYPE INTERESTING_ARTIFACT_HIT = BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT;
    private static final String TEMP_DIR_NAME = "case_uco_tmp";

    private CaseUcoFormatExporter() {
    }

    /**
     * Generates CASE-UCO report for the selected data source.
     *
     * @param selectedDataSourceId Object ID of the data source
     * @param reportOutputPath Full path to directory where to save CASE-UCO
     * report file
     * @param progressPanel ReportProgressPanel to update progress
     */
    @NbBundle.Messages({
        "ReportCaseUco.noCaseOpen=Unable to open currect case",
        "ReportCaseUco.unableToCreateDirectories=Unable to create directory for CASE-UCO report",
        "ReportCaseUco.initializing=Creating directories...",
        "ReportCaseUco.querying=Querying files...",
        "ReportCaseUco.ingestWarning=Warning, this report will be created before ingest services completed",
        "ReportCaseUco.processing=Saving files in CASE-UCO format...",
        "ReportCaseUco.srcModuleName.text=CASE-UCO Report"
    })
    @SuppressWarnings("deprecation")
    public static void generateReport(Long selectedDataSourceId, String reportOutputPath, ReportProgressPanel progressPanel) {

        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.updateStatusLabel(Bundle.ReportCaseUco_initializing());

        // Create the JSON generator
        JsonFactory jsonGeneratorFactory = new JsonFactory();
        java.io.File reportFile = Paths.get(reportOutputPath).toFile();
        try {
            Files.createDirectories(Paths.get(reportFile.getParent()));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to create directory for CASE-UCO report", ex); //NON-NLS
            MessageNotifyUtil.Message.error(Bundle.ReportCaseUco_unableToCreateDirectories());
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR);
            return;
        }

        // Check if ingest has finished
        if (IngestManager.getInstance().isIngestRunning()) {
            MessageNotifyUtil.Message.warn(Bundle.ReportCaseUco_ingestWarning());
        }

        JsonGenerator jsonGenerator = null;
        SimpleTimeZone timeZone = new SimpleTimeZone(0, "GMT");
        try {
            jsonGenerator = jsonGeneratorFactory.createGenerator(reportFile, JsonEncoding.UTF8);
            // instert \n after each field for more readable formatting
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));

            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();

            progressPanel.updateStatusLabel(Bundle.ReportCaseUco_querying());

            // create the required CASE-UCO entries at the beginning of the output file
            initializeJsonOutputFile(jsonGenerator);

            // create CASE-UCO entry for the Autopsy case
            String caseTraceId = saveCaseInfo(skCase, jsonGenerator);

            // create CASE-UCO data source entry
            String dataSourceTraceId = saveDataSourceInfo(selectedDataSourceId, caseTraceId, skCase, jsonGenerator);

            // Run getAllFilesQuery to get all files, exclude directories
            final String getAllFilesQuery = "select obj_id, name, size, crtime, atime, mtime, md5, parent_path, mime_type, extension from tsk_files where "
                    + "data_source_obj_id = " + Long.toString(selectedDataSourceId)
                    + " AND ((meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_UNDEF.getValue()
                    + ") OR (meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue()
                    + ") OR (meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT.getValue() + "))"; //NON-NLS

            try (SleuthkitCase.CaseDbQuery queryResult = skCase.executeQuery(getAllFilesQuery)) {
                ResultSet resultSet = queryResult.getResultSet();

                progressPanel.updateStatusLabel(Bundle.ReportCaseUco_processing());

                // Loop files and write info to CASE-UCO report
                while (resultSet.next()) {

                    if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                        break;
                    }

                    Long objectId = resultSet.getLong(1);
                    String fileName = resultSet.getString(2);
                    long size = resultSet.getLong("size");
                    String crtime = ContentUtils.getStringTimeISO8601(resultSet.getLong("crtime"), timeZone);
                    String atime = ContentUtils.getStringTimeISO8601(resultSet.getLong("atime"), timeZone);
                    String mtime = ContentUtils.getStringTimeISO8601(resultSet.getLong("mtime"), timeZone);
                    String md5Hash = resultSet.getString("md5");
                    String parent_path = resultSet.getString("parent_path");
                    String mime_type = resultSet.getString("mime_type");
                    String extension = resultSet.getString("extension");

                    saveFileInCaseUcoFormat(objectId, fileName, parent_path, md5Hash, mime_type, size, crtime, atime, mtime, extension, jsonGenerator, dataSourceTraceId);
                }
            }

            // create the required CASE-UCO entries at the end of the output file
            finilizeJsonOutputFile(jsonGenerator);

            Case.getCurrentCaseThrows().addReport(reportOutputPath, Bundle.ReportCaseUco_srcModuleName_text(), "");

            progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get list of files from case database", ex); //NON-NLS
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to create JSON output for the CASE-UCO report", ex); //NON-NLS
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Unable to read result set", ex); //NON-NLS
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "No current case open", ex); //NON-NLS
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR);
        } finally {
            if (jsonGenerator != null) {
                try {
                    jsonGenerator.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Failed to close JSON output file", ex); //NON-NLS
                }
            }
        }
    }

    /**
     * Exports files that are tagged w/ the following TagNames and that belong to 
     * the following interesting file sets (set name attributes of TSK_INTERSTING_FILE_HIT
     * and TSK_INTERESTING_ARTIFACT_HIT). Artifacts that are tagged with 
     * the following TagNames also have their associated source files included.
     * 
     * Duplicate files are excluded.
     * 
     * @param tagTypes Collection of TagNames to match
     * @param interestingItemSets Collection of SET_NAMEs to match on in TSK_INTERESTING_FILE_HITs
     *                            and TSK_INTERESTING_ARTIFACT_HITs.
     * @param outputFilePath Path to the folder that the CASE-UCO report should be written into
     * @param progressPanel UI Component to be updated with current processing status
     */
    @NbBundle.Messages({
        "CaseUcoFormatExporter.startMsg=Generating CASE-UCO Report",
        "CaseUcoFormatExporter.datasourceMsg=Generating CASE-UCO Report for %s",
        "CaseUcoFormatExporter.finishMsg=Finished generating CASE-UCO Report"
    })
    public static void export(List<TagName> tagTypes, List<String> interestingItemSets,
            File caseReportFolder, ReportProgressPanel progressPanel) throws IOException, SQLException,
            NoCurrentCaseException, TskCoreException {

        progressPanel.updateStatusLabel(Bundle.CaseUcoFormatExporter_startMsg());
        //Acquire references for file discovery
        Case currentCase = Case.getCurrentCaseThrows();
        String caseTempDirectory = currentCase.getTempDirectory();
        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        TagsManager tagsManager = currentCase.getServices().getTagsManager();

        //Create temp directory to filter out duplicate files.
        Path tmpDir = Paths.get(caseTempDirectory, TEMP_DIR_NAME);
        FileUtils.deleteDirectory(tmpDir.toFile());
        Files.createDirectory(tmpDir);

        //Create our report file
        Path reportFile = Paths.get(caseReportFolder.toString(), 
                ReportCaseUco.getReportFileName());

        //Timezone for formatting file creation, modification, and accessed times
        SimpleTimeZone timeZone = new SimpleTimeZone(0, "GMT");

        try (JsonGenerator jsonGenerator = createJsonGenerator(reportFile.toFile())) {   
            initializeJsonOutputFile(jsonGenerator);
            //Make the case the first entity in the report file.
            String caseTraceId = saveCaseInfo(skCase, jsonGenerator);

            for (DataSource ds : skCase.getDataSources()) {
                progressPanel.updateStatusLabel(String.format(
                        Bundle.CaseUcoFormatExporter_datasourceMsg(), ds.getName()));
                String dataSourceTraceId = saveDataSourceInfo(ds.getId(), 
                        caseTraceId, skCase, jsonGenerator);
                for (TagName tn : tagTypes) {
                    for (ContentTag ct : tagsManager.getContentTagsByTagName(tn, ds.getId())) {
                        saveUniqueFilesToCaseUcoFormat(ct.getContent(), tmpDir,
                                jsonGenerator, timeZone, dataSourceTraceId);
                    }
                    for (BlackboardArtifactTag bat : tagsManager.getBlackboardArtifactTagsByTagName(tn, ds.getId())) {
                        saveUniqueFilesToCaseUcoFormat(bat.getContent(), tmpDir,
                                jsonGenerator, timeZone, dataSourceTraceId);
                    }
                }
                if(!interestingItemSets.isEmpty()) {
                    List<BlackboardArtifact.ARTIFACT_TYPE> typesToQuery = Lists.newArrayList(
                            INTERESTING_FILE_HIT, INTERESTING_ARTIFACT_HIT);
                    for(BlackboardArtifact.ARTIFACT_TYPE artType : typesToQuery) {
                        for(BlackboardArtifact bArt : skCase.getBlackboardArtifacts(artType)) {
                            if(bArt.getDataSource().getId() != ds.getId()) {
                                continue;
                            }
                            BlackboardAttribute setAttr = bArt.getAttribute(SET_NAME);
                            if (interestingItemSets.contains(setAttr.getValueString())) {
                                Content content = skCase.getContentById(bArt.getObjectID());
                                saveUniqueFilesToCaseUcoFormat(content, tmpDir,
                                        jsonGenerator, timeZone, dataSourceTraceId);
                            }
                        }
                    }
                }
            }
            finilizeJsonOutputFile(jsonGenerator);
            progressPanel.updateStatusLabel(Bundle.CaseUcoFormatExporter_finishMsg());
        }
    }

    /**
     * Saves only unique abstract files to the report. Uniqueness is
     * determined by object id. The tmpDir Path is used to stored object 
     * ids that have already been visited.
     *
     * @param content Abstractfile isntance
     * @param tmpDir Directory to write object ids
     * @param jsonGenerator Report generator
     * @param timeZone Time zore for ctime, atime, and mtime formatting
     * @param dataSourceTraceId TraceID number for the parent data source
     * @throws IOException
     */
    private static void saveUniqueFilesToCaseUcoFormat(Content content, Path tmpDir, JsonGenerator jsonGenerator,
            TimeZone timeZone, String dataSourceTraceId) throws IOException {
        if (content instanceof AbstractFile && !(content instanceof DataSource)) {
            AbstractFile absFile = (AbstractFile) content;
            Path filePath = tmpDir.resolve(Long.toString(absFile.getId()));
            if (!Files.exists(filePath) && !absFile.isDir()) {
                saveFileInCaseUcoFormat(
                        absFile.getId(),
                        absFile.getName(),
                        absFile.getParentPath(),
                        absFile.getMd5Hash(),
                        absFile.getMIMEType(),
                        absFile.getSize(),
                        ContentUtils.getStringTimeISO8601(absFile.getCtime(), timeZone),
                        ContentUtils.getStringTimeISO8601(absFile.getAtime(), timeZone),
                        ContentUtils.getStringTimeISO8601(absFile.getMtime(), timeZone),
                        absFile.getNameExtension(),
                        jsonGenerator,
                        dataSourceTraceId
                );
                filePath.toFile().createNewFile();
            }
        }
    }

    private static JsonGenerator createJsonGenerator(File reportFile) throws IOException {
        JsonFactory jsonGeneratorFactory = new JsonFactory();
        JsonGenerator jsonGenerator = jsonGeneratorFactory.createGenerator(reportFile, JsonEncoding.UTF8);
        // instert \n after each field for more readable formatting
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));
        return jsonGenerator;
    }

    private static void initializeJsonOutputFile(JsonGenerator catalog) throws IOException {
        catalog.writeStartObject();
        catalog.writeFieldName("@graph");
        catalog.writeStartArray();
    }

    private static void finilizeJsonOutputFile(JsonGenerator catalog) throws IOException {
        catalog.writeEndArray();
        catalog.writeEndObject();
    }

    /**
     * Save info about the Autopsy case in CASE-UCo format
     *
     * @param skCase SleuthkitCase object
     * @param catalog JsonGenerator object
     * @return CASE-UCO trace ID object for the Autopsy case entry
     * @throws TskCoreException
     * @throws SQLException
     * @throws IOException
     * @throws NoCurrentCaseException
     */
    private static String saveCaseInfo(SleuthkitCase skCase, JsonGenerator catalog) throws TskCoreException, SQLException, IOException, NoCurrentCaseException {

        // create a "trace" entry for the Autopsy case iteself
        String uniqueCaseName;
        String dbFileName;
        TskData.DbType dbType = skCase.getDatabaseType();
        if (dbType == TskData.DbType.SQLITE) {
            uniqueCaseName = Case.getCurrentCaseThrows().getName();
            dbFileName = skCase.getDatabaseName();
        } else {
            uniqueCaseName = skCase.getDatabaseName();
            dbFileName = "";
        }

        String caseDirPath = skCase.getDbDirPath();
        String caseTraceId = "case-" + uniqueCaseName;
        catalog.writeStartObject();
        catalog.writeStringField("@id", caseTraceId);
        catalog.writeStringField("@type", "Trace");

        catalog.writeFieldName("propertyBundle");
        catalog.writeStartArray();
        catalog.writeStartObject();

        // replace double slashes with single ones
        caseDirPath = caseDirPath.replaceAll("\\\\", "/");

        catalog.writeStringField("@type", "File");
        if (dbType == TskData.DbType.SQLITE) {
            catalog.writeStringField("filePath", caseDirPath + "/" + dbFileName);
            catalog.writeBooleanField("isDirectory", false);
        } else {
            catalog.writeStringField("filePath", caseDirPath);
            catalog.writeBooleanField("isDirectory", true);
        }
        catalog.writeEndObject();

        catalog.writeEndArray();
        catalog.writeEndObject();

        return caseTraceId;
    }

    /**
     * Save info about the data source in CASE-UCo format
     *
     * @param selectedDataSourceId Object ID of the data source
     * @param caseTraceId CASE-UCO trace ID object for the Autopsy case entry
     * @param skCase SleuthkitCase object
     * @param jsonGenerator JsonGenerator object
     * @return
     * @throws TskCoreException
     * @throws SQLException
     * @throws IOException
     */
    private static String saveDataSourceInfo(Long selectedDataSourceId, String caseTraceId, SleuthkitCase skCase, JsonGenerator jsonGenerator) throws TskCoreException, SQLException, IOException {

        Long imageSize = (long) 0;
        String imageName = "";
        boolean isImageDataSource = false;
        String getImageDataSourceQuery = "select size from tsk_image_info where obj_id = " + selectedDataSourceId;
        try (SleuthkitCase.CaseDbQuery queryResult = skCase.executeQuery(getImageDataSourceQuery)) {
            ResultSet resultSet = queryResult.getResultSet();
            // check if we got a result
            while (resultSet.next()) {
                // we got a result so the data source was an image data source
                imageSize = resultSet.getLong(1);
                isImageDataSource = true;
                break;
            }
        }

        if (isImageDataSource) {
            // get caseDirPath to image file
            String getPathToDataSourceQuery = "select name from tsk_image_names where obj_id = " + selectedDataSourceId;
            try (SleuthkitCase.CaseDbQuery queryResult = skCase.executeQuery(getPathToDataSourceQuery)) {
                ResultSet resultSet = queryResult.getResultSet();
                while (resultSet.next()) {
                    imageName = resultSet.getString(1);
                    break;
                }
            }
        } else {
            // logical file data source
            String getLogicalDataSourceQuery = "select name from tsk_files where obj_id = " + selectedDataSourceId;
            try (SleuthkitCase.CaseDbQuery queryResult = skCase.executeQuery(getLogicalDataSourceQuery)) {
                ResultSet resultSet = queryResult.getResultSet();
                while (resultSet.next()) {
                    imageName = resultSet.getString(1);
                    break;
                }
            }
        }

        return saveDataSourceInCaseUcoFormat(jsonGenerator, imageName, imageSize, selectedDataSourceId, caseTraceId);
    }

    private static String saveDataSourceInCaseUcoFormat(JsonGenerator catalog, String imageName, Long imageSize, Long selectedDataSourceId, String caseTraceId) throws IOException {

        // create a "trace" entry for the data source
        String dataSourceTraceId = "data-source-" + selectedDataSourceId;
        catalog.writeStartObject();
        catalog.writeStringField("@id", dataSourceTraceId);
        catalog.writeStringField("@type", "Trace");

        catalog.writeFieldName("propertyBundle");
        catalog.writeStartArray();

        catalog.writeStartObject();
        catalog.writeStringField("@type", "File");

        // replace double back slashes with single ones
        imageName = imageName.replaceAll("\\\\", "/");

        catalog.writeStringField("filePath", imageName);
        catalog.writeEndObject();

        if (imageSize > 0) {
            catalog.writeStartObject();
            catalog.writeStringField("@type", "ContentData");
            catalog.writeStringField("sizeInBytes", Long.toString(imageSize));
            catalog.writeEndObject();
        }

        catalog.writeEndArray();
        catalog.writeEndObject();

        // create a "relationship" entry between the case and the data source
        catalog.writeStartObject();
        catalog.writeStringField("@id", "relationship-" + caseTraceId);
        catalog.writeStringField("@type", "Relationship");
        catalog.writeStringField("source", dataSourceTraceId);
        catalog.writeStringField("target", caseTraceId);
        catalog.writeStringField("kindOfRelationship", "contained-within");
        catalog.writeBooleanField("isDirectional", true);

        catalog.writeFieldName("propertyBundle");
        catalog.writeStartArray();
        catalog.writeStartObject();
        catalog.writeStringField("@type", "PathRelation");
        catalog.writeStringField("path", imageName);
        catalog.writeEndObject();
        catalog.writeEndArray();

        catalog.writeEndObject();

        return dataSourceTraceId;
    }

    private static void saveFileInCaseUcoFormat(Long objectId, String fileName, String parent_path, String md5Hash, String mime_type, long size, String ctime,
            String atime, String mtime, String extension, JsonGenerator catalog, String dataSourceTraceId) throws IOException {

        String fileTraceId = "file-" + objectId;

        // create a "trace" entry for the file
        catalog.writeStartObject();
        catalog.writeStringField("@id", fileTraceId);
        catalog.writeStringField("@type", "Trace");

        catalog.writeFieldName("propertyBundle");
        catalog.writeStartArray();

        catalog.writeStartObject();
        catalog.writeStringField("@type", "File");
        catalog.writeStringField("createdTime", ctime);
        catalog.writeStringField("accessedTime", atime);
        catalog.writeStringField("modifiedTime", mtime);
        if (extension != null) {
            catalog.writeStringField("extension", extension);
        }
        catalog.writeStringField("fileName", fileName);
        if (parent_path != null) {
            catalog.writeStringField("filePath", parent_path + fileName);
        }
        catalog.writeBooleanField("isDirectory", false);
        catalog.writeStringField("sizeInBytes", Long.toString(size));
        catalog.writeEndObject();

        catalog.writeStartObject();
        catalog.writeStringField("@type", "ContentData");
        if (mime_type != null) {
            catalog.writeStringField("mimeType", mime_type);
        }
        if (md5Hash != null) {
            catalog.writeFieldName("hash");
            catalog.writeStartArray();
            catalog.writeStartObject();
            catalog.writeStringField("@type", "Hash");
            catalog.writeStringField("hashMethod", "MD5");
            catalog.writeStringField("hashValue", md5Hash);
            catalog.writeEndObject();
            catalog.writeEndArray();
        }
        catalog.writeStringField("sizeInBytes", Long.toString(size));

        catalog.writeEndObject();

        catalog.writeEndArray();
        catalog.writeEndObject();

        // create a "relationship" entry between the file and the data source
        catalog.writeStartObject();
        catalog.writeStringField("@id", "relationship-" + objectId);
        catalog.writeStringField("@type", "Relationship");
        catalog.writeStringField("source", fileTraceId);
        catalog.writeStringField("target", dataSourceTraceId);
        catalog.writeStringField("kindOfRelationship", "contained-within");
        catalog.writeBooleanField("isDirectional", true);

        catalog.writeFieldName("propertyBundle");
        catalog.writeStartArray();
        catalog.writeStartObject();
        catalog.writeStringField("@type", "PathRelation");
        if (parent_path != null) {
            catalog.writeStringField("path", parent_path + fileName);
        } else {
            catalog.writeStringField("path", fileName);
        }
        catalog.writeEndObject();
        catalog.writeEndArray();

        catalog.writeEndObject();
    }
}
