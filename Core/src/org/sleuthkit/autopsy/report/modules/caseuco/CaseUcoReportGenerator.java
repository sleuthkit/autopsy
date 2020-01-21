/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The underlying file handle to 'my-report.json-ld' will be closed. Any further
 * calls to addX() will result in an IOException.
 */
public final class CaseUcoReportGenerator {

    private static final String EXTENSION = "json-ld";

    private final TimeZone timeZone;
    private final Path reportPath;
    private final JsonGenerator reportGenerator;

    /**
     * Creates a CaseUCO Report Generator that writes a report in the specified
     * directory.
     *
     * TimeZone is assumed to be GMT+0 for formatting file creation time,
     * accessed time and modified time.
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
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        
        reportGenerator.setCodec(mapper);

        reportGenerator.writeStartObject();
        reportGenerator.writeFieldName("@graph");
        reportGenerator.writeStartArray();

        //Assume GMT+0
        this.timeZone = new SimpleTimeZone(0, "GMT");
    }

    /**
     * Adds an AbstractFile instance to the Case UCO report.
     *
     * @param file AbstractFile instance to write
     * @param parentDataSource The parent data source for this abstract file. It
     * is assumed that this parent has been written to the report (via
     * addDataSource) prior to this call. Otherwise, the report may be invalid.
     * @throws IOException If an I/O error occurs.
     * @throws TskCoreException 
     */
    public void addFile(AbstractFile file, Content parentDataSource) throws IOException, TskCoreException {
        addFile(file, parentDataSource, null);
    }
    
    /**
     * Adds an AbstractFile instance to the Case UCO report.
     * 
     * @param file AbstractFile instance to write
     * @param parentDataSource The parent data source for this abstract file. It
     * is assumed that this parent has been written to the report (via
     * addDataSource) prior to this call. Otherwise, the report may be invalid.
     * @param localPath The location of the file on secondary storage, somewhere
     * other than the case. Example: local disk. This value will be ignored if
     * it is null.
     * @throws IOException
     * @throws TskCoreException 
     */
    public void addFile(AbstractFile file, Content parentDataSource, Path localPath) throws IOException, TskCoreException {
        String fileTraceId = getFileTraceId(file);

        //Create the Trace CASE node, which will contain attributes about some evidence.
        //Trace is the standard term for evidence. For us, this means file system files.
        CASENode fileTrace = new CASENode(fileTraceId, "Trace");
        
        //The bits of evidence for each Trace node are contained within Property
        //Bundles. There are a number of Property Bundles available in the CASE ontology.

        //Build up the File Property Bundle, as the name implies - properties of
        //the file itself.
        CASEPropertyBundle filePropertyBundle = createFileBundle(file);
        fileTrace.addBundle(filePropertyBundle);

        //Build up the ContentData Property Bundle, as the name implies - properties of
        //the File data itself.
        CASEPropertyBundle contentDataPropertyBundle = createContentDataBundle(file);
        fileTrace.addBundle(contentDataPropertyBundle);
        
        if(localPath != null) {
            String urlTraceId = getURLTraceId(file);
            CASENode urlTrace = new CASENode(urlTraceId, "Trace");
            CASEPropertyBundle urlPropertyBundle = new CASEPropertyBundle("URL");
            urlPropertyBundle.addProperty("fullValue", localPath.toString());
            urlTrace.addBundle(urlPropertyBundle);

            contentDataPropertyBundle.addProperty("dataPayloadReferenceUrl", urlTraceId);
            reportGenerator.writeObject(urlTrace);
        }

        //Create the Relationship CASE node. This defines how the Trace CASE node described above
        //is related to another CASE node (in this case, the parent data source).
        String relationshipID = getRelationshipId(file);
        CASENode relationship = createRelationshipNode(relationshipID, 
                fileTraceId, getDataSourceTraceId(parentDataSource));

        //Build up the PathRelation bundle for the relationship node, 
        //as the name implies - the Path of the Trace in the data source.
        CASEPropertyBundle pathRelationPropertyBundle = new CASEPropertyBundle("PathRelation");
        pathRelationPropertyBundle.addProperty("path", file.getUniquePath());
        relationship.addBundle(pathRelationPropertyBundle);
        
        //This completes the triage, write them to JSON.
        reportGenerator.writeObject(fileTrace);
        reportGenerator.writeObject(relationship);
    }
    
    private String getURLTraceId(Content content) {
        return "url-" + content.getId(); 
    }
    
    /**
     * All relationship nodes will be the same within our context. Namely, contained-within
     * and isDirectional as true.
     */
    private CASENode createRelationshipNode(String relationshipID, String sourceID, String targetID) {
        CASENode relationship = new CASENode(relationshipID, "Relationship");
        relationship.addProperty("source", sourceID);
        relationship.addProperty("target", targetID);
        relationship.addProperty("kindOfRelationship", "contained-within");
        relationship.addProperty("isDirectional", true);
        return relationship;
    }
    
    /**
     * Creates a File Property Bundle with a selection of file attributes.
     */
    private CASEPropertyBundle createFileBundle(AbstractFile file) throws TskCoreException {
        CASEPropertyBundle filePropertyBundle = new CASEPropertyBundle("File");
        String createdTime = ContentUtils.getStringTimeISO8601(file.getCrtime(), timeZone);
        String accessedTime = ContentUtils.getStringTimeISO8601(file.getAtime(), timeZone);
        String modifiedTime = ContentUtils.getStringTimeISO8601(file.getMtime(), timeZone);
        filePropertyBundle.addProperty("createdTime", createdTime);
        filePropertyBundle.addProperty("accessedTime", accessedTime);
        filePropertyBundle.addProperty("modifiedTime", modifiedTime);
        if (!Strings.isNullOrEmpty(file.getNameExtension())) {
            filePropertyBundle.addProperty("extension", file.getNameExtension());
        }
        filePropertyBundle.addProperty("fileName", file.getName());
        filePropertyBundle.addProperty("filePath", file.getUniquePath());
        filePropertyBundle.addProperty("isDirectory", file.isDir());
        filePropertyBundle.addProperty("sizeInBytes", Long.toString(file.getSize()));
        return filePropertyBundle;
    }
    
    /**
     * Creates a Content Data Property Bundle with a selection of file attributes.
     */
    private CASEPropertyBundle createContentDataBundle(AbstractFile file) {
        CASEPropertyBundle contentDataPropertyBundle = new CASEPropertyBundle("ContentData");
        if (!Strings.isNullOrEmpty(file.getMIMEType())) {
            contentDataPropertyBundle.addProperty("mimeType", file.getMIMEType());
        }
        if (!Strings.isNullOrEmpty(file.getMd5Hash())) {
            List<CASEPropertyBundle> hashPropertyBundles = new ArrayList<>();
            CASEPropertyBundle md5HashPropertyBundle = new CASEPropertyBundle("Hash");
            md5HashPropertyBundle.addProperty("hashMethod", "MD5");
            md5HashPropertyBundle.addProperty("hashValue", file.getMd5Hash());
            hashPropertyBundles.add(md5HashPropertyBundle);
            contentDataPropertyBundle.addProperty("hash", hashPropertyBundles);
        }
        contentDataPropertyBundle.addProperty("sizeInBytes", Long.toString(file.getSize()));
        return contentDataPropertyBundle;
    }

    /**
     * Creates a unique CASE Node file trace id.
     */
    private String getFileTraceId(AbstractFile file) {
        return "file-" + file.getId();
    }

    /**
     * Creates a unique CASE Node relationship id value.
     */
    private String getRelationshipId(Content content) {
        return "relationship-" + content.getId();
    }

    /**
     * Adds a Content instance (which is known to be a DataSource) to the CASE
     * report. This means writing a selection of attributes to a CASE or UCO
     * object.
     *
     * @param dataSource Datasource content to write
     * @param parentCase The parent case that this data source belongs in. It is
     * assumed that this parent has been written to the report (via addCase)
     * prior to this call. Otherwise, the report may be invalid.
     */
    public void addDataSource(Content dataSource, Case parentCase) throws IOException, TskCoreException {
        String dataSourceTraceId = this.getDataSourceTraceId(dataSource);
        
        CASENode dataSourceTrace = new CASENode(dataSourceTraceId, "Trace");
        CASEPropertyBundle filePropertyBundle = new CASEPropertyBundle("File");

        String dataSourcePath = getDataSourcePath(dataSource);
        
        filePropertyBundle.addProperty("filePath", dataSourcePath);
        dataSourceTrace.addBundle(filePropertyBundle);
        
        if (dataSource.getSize() > 0) {
            CASEPropertyBundle contentDataPropertyBundle = new CASEPropertyBundle("ContentData");
            contentDataPropertyBundle.addProperty("sizeInBytes", Long.toString(dataSource.getSize()));
            dataSourceTrace.addBundle(contentDataPropertyBundle);
        }

        // create a "relationship" entry between the case and the data source
        String caseTraceId = getCaseTraceId(parentCase);
        String relationshipTraceId = getRelationshipId(dataSource);
        CASENode relationship = createRelationshipNode(relationshipTraceId, 
                dataSourceTraceId, caseTraceId);

        CASEPropertyBundle pathRelationBundle = new CASEPropertyBundle("PathRelation");
        pathRelationBundle.addProperty("path", dataSourcePath);
        relationship.addBundle(pathRelationBundle);
        
        //This completes the triage, write them to JSON.
        reportGenerator.writeObject(dataSourceTrace);
        reportGenerator.writeObject(relationship);
    }
    
    private String getDataSourcePath(Content dataSource) {
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
        return dataSourcePath;
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
     * selection of Case attributes to a CASE/UCO object.
     *
     * @param caseObj Case instance to include in the report.
     * @throws IOException If an I/O error is encountered.
     */
    public void addCase(Case caseObj) throws IOException {
        SleuthkitCase skCase = caseObj.getSleuthkitCase();

        String caseDirPath = skCase.getDbDirPath();
        String caseTraceId = getCaseTraceId(caseObj);
        CASENode caseTrace = new CASENode(caseTraceId, "Trace");
        CASEPropertyBundle filePropertyBundle = new CASEPropertyBundle("File");

        // replace double slashes with single ones
        caseDirPath = caseDirPath.replaceAll("\\\\", "/");

        Case.CaseType caseType = caseObj.getCaseType();
        if (caseType.equals(CaseType.SINGLE_USER_CASE)) {
            filePropertyBundle.addProperty("filePath", caseDirPath + "/" + skCase.getDatabaseName());
            filePropertyBundle.addProperty("isDirectory", false);
        } else {
            filePropertyBundle.addProperty("filePath", caseDirPath);
            filePropertyBundle.addProperty("isDirectory", true);
        }

        caseTrace.addBundle(filePropertyBundle);
        reportGenerator.writeObject(caseTrace);
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

    /**
     * A CASE or UCO object. CASE objects can have properties and
     * property bundles.
     */
    private final class CASENode {
        
        private final String id;
        private final String type;
        
        //Dynamic properties added to this CASENode.
        private final Map<String, Object> properties;
        private final List<CASEPropertyBundle> propertyBundle;

        public CASENode(String id, String type) {
            this.id = id;
            this.type = type;
            properties = new LinkedHashMap<>();
            propertyBundle = new ArrayList<>();
        }
        
        @JsonProperty("@id")
        public String getId() {
            return id;
        }
        
        @JsonProperty("@type")
        public String getType() {
            return type;
        }
        
        @JsonAnyGetter
        public Map<String, Object> getProperties() {
            return properties;
        }
        
        @JsonProperty("propertyBundle")
        public List<CASEPropertyBundle> getPropertyBundle() {
            return propertyBundle;
        }
        
        public void addProperty(String key, Object val) {
            properties.put(key, val);
        }

        public void addBundle(CASEPropertyBundle bundle) {
            propertyBundle.add(bundle);
        }
    }

    /**
     * Contains CASE or UCO properties.
     */
    private final class CASEPropertyBundle {
        
        private final LinkedHashMap<String, Object> properties;

        public CASEPropertyBundle(String type) {
            properties = new LinkedHashMap<>();
            addProperty("@type", type);
        }
        
        @JsonAnyGetter
        public Map<String, Object> getProperties() {
            return properties;
        }
        
        public void addProperty(String key, Object val) {
            properties.put(key, val);
        }
    }
}
