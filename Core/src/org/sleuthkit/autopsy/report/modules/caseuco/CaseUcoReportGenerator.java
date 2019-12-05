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
import java.nio.file.Path;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.google.common.base.Strings;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Writes Autopsy DataModel objects to Case UCO format.
 *
 * Clients are expected to add the Case first. Then they should add each data
 * source before adding any files for that data source.
 *
 * Here is an example, where we add everything:
 *
 * Path directory = Paths.get("C:", "Reports"); 
 * CaseUcoReportGenerator caseUco = new CaseUcoReportGenerator(directory, "my-report");
 *
 * Case caseObj = Case.getCurrentCase(); 
 * caseUco.addCase(caseObj);
 * List<Content> dataSources = caseObj.getDataSources(); 
 * for(Content dataSource : dataSources) { 
 *      caseUco.addDataSource(dataSource, caseObj);
 *      List<AbstractFile> files = getAllFilesInDataSource(dataSource);
 *      for(AbstractFile file : files) { 
 *          caseUco.addFile(file, dataSource); 
 *      } 
 * }
 *
 * Path reportOutput = caseUco.generateReport();
 * //Done. Report at - "C:\Reports\my-report.json-ld" 
 * 
 * Please note that the life cycle for this class ends with generateReport().
 * The underlying file handle to 'my-report.json-ld' will be closed.
 * Any further calls to addX() will result in an IOException.
 */
public final class CaseUcoReportGenerator {

    private static final String EXTENSION = "json-ld";

    private TimeZone timeZone;
    private final Path reportPath;
    private final JsonGenerator reportGenerator;

    /**
     * Creates a CaseUCO Report Generator that writes a report in the specified
     * directory.
     *
     * TimeZone is assumed to be GMT+0 for formatting file creation time, accessed
     * time and modified time.
     *
     * @param directory Directory to write the CaseUCO report file. Assumes the
     * calling thread has write access to the directory and that the directory
     * exists.
     * @param name Name of the CaseUCO report file.
     * @throws IOException If an I/O error occurs
     */
    public CaseUcoReportGenerator(Path directory, String reportName) throws IOException {
        this.reportPath = directory.resolve(reportName + "." + EXTENSION);

        JsonFactory jsonGeneratorFactory = new JsonFactory();
        reportGenerator = jsonGeneratorFactory.createGenerator(reportPath.toFile(), JsonEncoding.UTF8);
        // Puts a newline between each Key, Value pair for readability.
        reportGenerator.setPrettyPrinter(new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n")));

        //Start report.
        reportGenerator.writeStartObject();
        reportGenerator.writeFieldName("@graph");
        reportGenerator.writeStartArray();

        //Assume GMT+0
        this.timeZone = new SimpleTimeZone(0, "GMT");
    }

    /**
     * Adds an AbstractFile instance to the Case UCO report. This means writing
     * a selection of file attributes to a Case UCO entity.
     *
     * Attributes captured: Created time, Accessed time, Modified time,
     * Extension, Name, Path, is Directory, Size (in bytes), MIME type and MD5
     * hash.
     *
     * @param file AbstractFile instance to write
     * @param parentDataSource The parent data source for this abstract file. It 
     * is assumed that this parent has been written to the report (via addDataSource)
     * prior to this call. Otherwise, the report may be invalid.
     * @throws IOException If an I/O error occurs.
     */
    public void addFile(AbstractFile file, Content parentDataSource) throws IOException, TskCoreException {
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

        if (!Strings.isNullOrEmpty(file.getNameExtension())) {
            reportGenerator.writeStringField("extension", file.getNameExtension());
        }
        reportGenerator.writeStringField("fileName", file.getName());
        reportGenerator.writeStringField("filePath", file.getUniquePath());
        reportGenerator.writeBooleanField("isDirectory", file.isDir());
        reportGenerator.writeStringField("sizeInBytes", Long.toString(file.getSize()));
        reportGenerator.writeEndObject();

        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@type", "ContentData");
        if (!Strings.isNullOrEmpty(file.getMIMEType())) {
            reportGenerator.writeStringField("mimeType", file.getMIMEType());
        }
        if (!Strings.isNullOrEmpty(file.getMd5Hash())) {
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
        reportGenerator.writeStringField("path", file.getUniquePath());
        reportGenerator.writeEndObject();
        reportGenerator.writeEndArray();

        reportGenerator.writeEndObject();
    }

    /**
     * Creates a unique Case UCO file trace id.
     *
     * @param file File to create an id.
     * @return
     */
    private String getFileTraceId(AbstractFile file) {
        return "file-" + file.getId();
    }

    /**
     * Adds a Content instance (which is known to be a DataSource) to the Case
     * UCO report. This means writing a selection of attributes to a Case UCO
     * entity.
     *
     * Attributes captured: Path, Size (in bytes),
     *
     * @param dataSource Datasource content to write
     * @param parentCase The parent case that this data source belongs in. It is
     * assumed that this parent has been written to the report (via addCase) 
     * prior to this call. Otherwise, the report may be invalid.
     */
    public void addDataSource(Content dataSource, Case parentCase) throws IOException, TskCoreException {
        String dataSourceTraceId = this.getDataSourceTraceId(dataSource);
        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@id", dataSourceTraceId);
        reportGenerator.writeStringField("@type", "Trace");

        reportGenerator.writeFieldName("propertyBundle");
        reportGenerator.writeStartArray();

        reportGenerator.writeStartObject();
        reportGenerator.writeStringField("@type", "File");

        String dataSourcePath = "";
        if (dataSource instanceof Image) {
            String[] paths = ((Image) dataSource).getPaths();
            if (paths.length > 0) {
                //Get the first data source in the path, as this will
                //be reflected in each file's uniquePath.
                dataSourcePath = paths[0];
            }
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
     * Creates a unique Case UCO trace id for a data source.
     *
     * @param dataSource
     * @return
     */
    private String getDataSourceTraceId(Content dataSource) {
        return "data-source-" + dataSource.getId();
    }

    /**
     * Adds a Case instance to the Case UCO report. This means writing a
     * selection of Case attributes to a Case UCO entity.
     *
     * Attributes captured: Case directory.
     *
     * @param caseObj Case instance to include in the report.
     * @throws IOException If an I/O error is encountered.
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
     * Creates a unique Case UCO trace id for a Case.
     *
     * @param caseObj
     * @return
     */
    private String getCaseTraceId(Case caseObj) {
        return "case-" + caseObj.getName();
    }

    /**
     * Returns a Path to the completed Case UCO report file.
     *
     * This marks the end of the CaseUcoReportGenerator's life cycle. This
     * function will close an underlying file handles, meaning any subsequent
     * calls to addX() will result in an IOException.
     *
     * @return The Path to the finalized report.
     * @throws IOException If an I/O error occurs.
     */
    public Path generateReport() throws IOException {
        //Finalize the report.
        reportGenerator.writeEndArray();
        reportGenerator.writeEndObject();
        reportGenerator.close();

        return reportPath;
    }
}
