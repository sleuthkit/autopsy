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
package org.sleuthkit.autopsy.report.modules.caseuco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Writes AbstractFiles, DataSources, and Cases to Case UCO format.
 *
 * Clients are expected to add Case and then DataSource and then all files of
 * that DataSource.
 * 
 * Example:
 * 
 * Path directory = Paths.get("C:", "Reports");
 * CaseUcoReportGenerator caseUco = new CaseUcoReportGenerator(directory, "my-report");
 * 
 * Case caseObj = Case.getCurrentCase();
 * caseUco.addCase(caseObj);
 * List<DataSources> dataSources = getDataSourcesInCase(caseObj);
 * for(DataSource dataSource : dataSources) {
 *      caseUco.addDataSource(dataSource, caseObj);
 *      List<AbstractFile> files = getAllFilesInDataSource(dataSource);
 *      for(AbstractFile file : files) {
 *          caseUco.addFile(file, dataSource);
 *      }
 * }
 * 
 * //Done. C:\Reports\my-report.json-ld
 * Path reportOutput = caseUco.generateReport();
 * 
 */
public final class CaseUcoReportGenerator {

    private static final String EXTENSION = "json-ld";

    private TimeZone timeZone;
    private final Path reportPath;
    private final JsonGenerator reportGenerator;

    /**
     * Creates a CaseUCO Report file at the specified directory under the name
     * 'reportName'.
     *
     * TimeZone defaults to GMT for formatting file creation time, accessed time
     * and modified time. You may change this in the setter.
     *
     * @param directory Directory to write the CaseUCO report file. Assumes the
     * calling thread has write access to the directory.
     * @param name Name of the CaseUCO report file.
     * @throws IOException
     */
    public CaseUcoReportGenerator(Path directory, String reportName) throws IOException {
        this.reportPath = directory.resolve(reportName + "." + EXTENSION);

        Files.createDirectories(reportPath);

        JsonFactory jsonGeneratorFactory = new JsonFactory();
        reportGenerator = jsonGeneratorFactory.createGenerator(reportPath.toFile(), JsonEncoding.UTF8);
        // Puts a newline between each Key, Value pair for readability.
        reportGenerator.setPrettyPrinter(new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n")));

        this.startReport(reportGenerator);

        this.setTimeZone(new SimpleTimeZone(0, "GMT"));
    }

    /**
     * Opens the initial JSON structures that will contain the Case UCO entities
     * to be added in addFile, addDataSource, addCase.
     *
     * @param reportGenerator
     * @throws IOException
     */
    private void startReport(JsonGenerator reportGenerator) throws IOException {
        reportGenerator.writeStartObject();
        reportGenerator.writeFieldName("@graph");
        reportGenerator.writeStartArray();
    }

    /**
     * Sets the time zone for file creation, accessed and modification dates.
     *
     * The default is GMT.
     *
     * @param timeZone
     */
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * Adds an AbstractFile instance to the Case UCO report. This means writing
     * a selection of file attributes to a Case UCO entity.
     *
     * Attributes captured: Created time, Accessed time, Modified time,
     * Extension, Name, Path, is Directory, Size (in bytes), MIME type and MD5 hash.
     *
     * @param file
     * @param parentDataSource
     * @throws IOException
     */
    public void addFile(AbstractFile file, DataSource parentDataSource) throws IOException {
        String fileTraceId = getFileTraceId(file);

        // create a "trace" entry for the file
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@id", fileTraceId);
        reportGenerator.writeStringField("@type", "Trace");

        reportGenerator.writeFieldName("propertyBundle");
        reportGenerator.writeStartArray();

        String createdTime = ContentUtils.getStringTimeISO8601(file.getCtime(), timeZone);
        String accessedTime = ContentUtils.getStringTimeISO8601(file.getAtime(), timeZone);
        String modifiedTime = ContentUtils.getStringTimeISO8601(file.getMtime(), timeZone);
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@type", "File");
        reportGenerator.writeStringField("createdTime", createdTime);
        reportGenerator.writeStringField("accessedTime", accessedTime);
        reportGenerator.writeStringField("modifiedTime", modifiedTime);

        if (file.getNameExtension() != null) {
            reportGenerator.writeStringField("extension", file.getNameExtension());
        }
        reportGenerator.writeStringField("fileName", file.getName());
        if (file.getParentPath() != null) {
            reportGenerator.writeStringField("filePath", file.getParentPath() + file.getName());
        }
        reportGenerator.writeBooleanField("isDirectory", file.isDir());
        reportGenerator.writeStringField("sizeInBytes", Long.toString(file.getSize()));
        reportGenerator.writeEndObject();

        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@type", "ContentData");
        if (file.getMIMEType() != null) {
            reportGenerator.writeStringField("mimeType", file.getMIMEType());
        }
        if (file.getMd5Hash() != null) {
            reportGenerator.writeFieldName("hash");
            reportGenerator.writeStartArray();
            reportGenerator.writeStartObject();
            reportGenerator.writeStringField("@type", "Hash");
            reportGenerator.writeStringField("hashMethod", "MD5");
            reportGenerator.writeStringField("hashValue", file.getMd5Hash());
            reportGenerator.writeEndObject();
            reportGenerator.writeEndArray();
        }
        reportGenerator.writeStringField("sizeInBytes", Long.toString(file.getSize()));

        reportGenerator.writeEndObject();

        reportGenerator.writeEndArray();
        reportGenerator.writeEndObject();

        // create a "relationship" entry between the file and the data source
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@id", "relationship-" + file.getId());
        reportGenerator.writeStringField("@type", "Relationship");
        reportGenerator.writeStringField("source", fileTraceId);
        reportGenerator.writeStringField("target", getDataSourceTraceId(parentDataSource));
        reportGenerator.writeStringField("kindOfRelationship", "contained-within");
        reportGenerator.writeBooleanField("isDirectional", true);

        reportGenerator.writeFieldName("propertyBundle");
        reportGenerator.writeStartArray();
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@type", "PathRelation");
        if (file.getParentPath() != null) {
            reportGenerator.writeStringField("path", file.getParentPath() + file.getName());
        } else {
            reportGenerator.writeStringField("path", file.getName());
        }
        reportGenerator.writeEndObject();
        reportGenerator.writeEndArray();

        reportGenerator.writeEndObject();
    }

    /**
     * Creates a unique Case UCO trace id.
     *
     * @param file
     * @return
     */
    private String getFileTraceId(AbstractFile file) {
        return "file-" + file.getId();
    }

    /**
     * Adds a DataSource instance to the Case UCO report. This means writing a 
     * selection of DataSource attributes to a Case UCO entity.
     * 
     * Attributes captured: Path, Size (in bytes), 
     *
     * @param dataSource
     * @param parentCase
     */
    public void addDataSource(DataSource dataSource, Case parentCase) throws IOException, TskCoreException {
        String dataSourceTraceId = this.getDataSourceTraceId(dataSource);
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@id", dataSourceTraceId);
        reportGenerator.writeStringField("@type", "Trace");

        reportGenerator.writeFieldName("propertyBundle");
        reportGenerator.writeStartArray();

        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@type", "File");

        String dataSourcePath;
        if(dataSource instanceof Image) {
            dataSourcePath = dataSource.getUniquePath();
        } else {
            dataSourcePath = dataSource.getName();
        }
        
        dataSourcePath = dataSourcePath.replaceAll("\\\\", "/");

        reportGenerator.writeStringField("filePath", dataSourcePath);
        reportGenerator.writeEndObject();

        if (dataSource.getSize() > 0) {
            reportGenerator.writeStartObject();
            reportGenerator.writeStringField("@type", "ContentData");
            reportGenerator.writeStringField("sizeInBytes", Long.toString(dataSource.getSize()));
            reportGenerator.writeEndObject();
        }

        reportGenerator.writeEndArray();
        reportGenerator.writeEndObject();

        // create a "relationship" entry between the case and the data source
        String caseTraceId = getCaseTraceId(parentCase);
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@id", "relationship-" + caseTraceId);
        reportGenerator.writeStringField("@type", "Relationship");
        reportGenerator.writeStringField("source", dataSourceTraceId);
        reportGenerator.writeStringField("target", caseTraceId);
        reportGenerator.writeStringField("kindOfRelationship", "contained-within");
        reportGenerator.writeBooleanField("isDirectional", true);

        reportGenerator.writeFieldName("propertyBundle");
        reportGenerator.writeStartArray();
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@type", "PathRelation");
        reportGenerator.writeStringField("path", dataSourcePath);
        reportGenerator.writeEndObject();
        reportGenerator.writeEndArray();

        reportGenerator.writeEndObject();
    }

    /**
     * Creates a unique Case UCO trace id.
     *
     * @param dataSource
     * @return
     */
    private String getDataSourceTraceId(DataSource dataSource) {
        return "data-source-" + dataSource.getId();
    }

    /**
     * Adds a Case instance to the Case UCO report. This means writing a
     * selection of Case attributes to a Case UCO entity.
     *
     * Attributes captured: Case directory.
     *
     * Current
     *
     * @param caseObj
     * @throws IOException
     */
    public void addCase(Case caseObj) throws IOException {
        SleuthkitCase skCase = caseObj.getSleuthkitCase();

        String caseDirPath = skCase.getDbDirPath();
        String caseTraceId = getCaseTraceId(caseObj);
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@id", caseTraceId);
        reportGenerator.writeStringField("@type", "Trace");

        reportGenerator.writeFieldName("propertyBundle");
        reportGenerator.writeStartArray();
        reportGenerator.writeStartObject();

        // replace double slashes with single ones
        caseDirPath = caseDirPath.replaceAll("\\\\", "/");

        reportGenerator.writeStringField("@type", "File");

        Case.CaseType caseType = caseObj.getCaseType();
        if (caseType.equals(CaseType.SINGLE_USER_CASE)) {
            reportGenerator.writeStringField("filePath", caseDirPath + "/" + skCase.getDatabaseName());
            reportGenerator.writeBooleanField("isDirectory", false);
        } else {
            reportGenerator.writeStringField("filePath", caseDirPath);
            reportGenerator.writeBooleanField("isDirectory", true);
        }

        reportGenerator.writeEndObject();
        reportGenerator.writeEndArray();
        reportGenerator.writeEndObject();
    }

    /**
     * Creates a unique Case UCO trace id.
     *
     * @param caseObj
     * @return
     */
    private String getCaseTraceId(Case caseObj) {
        SleuthkitCase skCase = caseObj.getSleuthkitCase();
        Case.CaseType caseType = caseObj.getCaseType();

        if (caseType.equals(CaseType.SINGLE_USER_CASE)) {
            return "case-" + caseObj.getName();
        } else {
            return "case-" + skCase.getDatabaseName();
        }
    }

    /**
     * Completes the report by closing the JSON structures opened in
     * startReport().
     *
     * @param reportGenerator
     * @throws IOException
     */
    private void completeReport(JsonGenerator reportGenerator) throws IOException {
        reportGenerator.writeEndArray();
        reportGenerator.writeEndObject();
        reportGenerator.close();
    }

    /**
     * Returns a Path to the completed Case UCO report file.
     *
     * @return
     * @throws IOException
     */
    public Path generateReport() throws IOException {
        //Finalize the report.
        this.completeReport(reportGenerator);

        return reportPath;
    }
}