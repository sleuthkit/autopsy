/*
 * Sample module in the public domain.  Feel free to use this as a template
 * for your modules.
 * 
 *  Contact: Brian Carrier [carrier <at> sleuthkit [dot] org]
 *
 *  This is free and unencumbered software released into the public domain.
 *  
 *  Anyone is free to copy, modify, publish, use, compile, sell, or
 *  distribute this software, either in source code form or as a compiled
 *  binary, for any purpose, commercial or non-commercial, and by any
 *  means.
 *  
 *  In jurisdictions that recognize copyright laws, the author or authors
 *  of this software dedicate any and all copyright interest in the
 *  software to the public domain. We make this dedication for the benefit
 *  of the public at large and to the detriment of our heirs and
 *  successors. We intend this dedication to be an overt act of
 *  relinquishment in perpetuity of all present and future rights to this
 *  software under copyright law.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 *  IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 *  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE. 
 */
package org.sleuthkit.autopsy.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ErrorInfo;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.externalresults.ExternalResults;
import org.sleuthkit.autopsy.externalresults.ExternalResultsImporter;
import org.sleuthkit.autopsy.externalresults.ExternalResultsXMLParser;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Sample data source ingest module that doesn't do much. Demonstrates use of
 * utility classes: ExecUtils and the org.sleuthkit.autopsy.externalresults
 * package.
 */
public class SampleExecutableDataSourceIngestModule implements DataSourceIngestModule {

    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final String moduleName = SampleExecutableIngestModuleFactory.getModuleName();
    private final String fileInCaseDatabase = "/WINDOWS/system32/ntmsapi.dll"; // Probably  
    private IngestJobContext context;
    private String outputDirPath;
    private String derivedFileInCaseDatabase;

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        if (refCounter.incrementAndGet(context.getJobId()) == 1) {
            // Create an output directory for this job.
            outputDirPath = Case.getCurrentCase().getModulesOutputDirAbsPath() + File.separator + moduleName; //NON-NLS
            File outputDir = new File(outputDirPath);
            if (outputDir.exists() == false) {
                outputDir.mkdirs();
            }
        }
    }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        if (refCounter.get(context.getJobId()) == 1) {
            try {
                // There will be two tasks: data source analysis and import of 
                // the results of the analysis.
                progressBar.switchToDeterminate(2);

                // Do the analysis. The following sample code could be used to 
                // run an executable. In this case the executable would take 
                // two command line arguments, the path to the data source to be 
                // analyzed and the path to a results file to be generated. The 
                // results file would be an an XML file (see org.sleuthkit.autopsy.externalresults.autopsy_external_results.xsd)
                // with instructions for the import of blackboard artifacts, 
                // derived files, and reports generated by the analysis. In this 
                // sample ingest module, the generation of the analysis results is
                // simulated. 
                String resultsFilePath = outputDirPath + File.separator + String.format("job_%d_results.xml", context.getJobId());
                boolean haveRealExecutable = false;
                if (haveRealExecutable) {
                    if (dataSource instanceof Image) {
                        Image image = (Image)dataSource;
                        String dataSourcePath = image.getPaths()[0];
                        List<String> commandLine = new ArrayList<>();
                        commandLine.add("some.exe");
                        commandLine.add(dataSourcePath);
                        commandLine.add(resultsFilePath);
                        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
                        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
                    }
                    // not a disk image
                    else {
                        return ProcessResult.OK;
                    }
                } else {
                    generateSimulatedResults(resultsFilePath);
                }
                progressBar.progress(1);

                // Import the results of the analysis.
                ExternalResultsXMLParser resultsParser = new ExternalResultsXMLParser(dataSource, resultsFilePath);
                ExternalResults results = resultsParser.parse();
                List<ErrorInfo> errors = resultsParser.getErrorInfo();
                ExternalResultsImporter importer = new ExternalResultsImporter();
                errors.addAll(importer.importResults(results));
                for (ErrorInfo errorInfo : errors) {
                    IngestServices.getInstance().postMessage(IngestMessage.createErrorMessage(moduleName, "External Results Import Error", errorInfo.getMessage()));
                }
                progressBar.progress(2);
            } catch (ParserConfigurationException | TransformerException | IOException ex) {
                Logger logger = IngestServices.getInstance().getLogger(moduleName);
                logger.log(Level.SEVERE, "Failed to simulate analysis and results import", ex);  //NON-NLS
                return ProcessResult.ERROR;
            }
        }
        return ProcessResult.OK;
    }

    private void generateSimulatedResults(String resultsFilePath) throws ParserConfigurationException, IOException, TransformerConfigurationException, TransformerException {
        List<String> derivedFilePaths = generateSimulatedDerivedFiles();
        List<String> reportFilePaths = generateSimulatedReports();
        generateSimulatedResultsFile(derivedFilePaths, reportFilePaths, resultsFilePath);
    }

    private List<String> generateSimulatedDerivedFiles() throws IOException {
        List<String> filePaths = new ArrayList<>();
        String fileContents = "This is a simulated derived file.";
        for (int i = 0; i < 2; ++i) {
            String fileName = String.format("job_%d_derived_file_%d.txt", context.getJobId(), i);
            filePaths.add(generateFile(fileName, fileContents.getBytes()));
            if (i == 0) {
                this.derivedFileInCaseDatabase = this.fileInCaseDatabase + "/" + fileName;
            }
        }
        return filePaths;
    }

    private List<String> generateSimulatedReports() throws IOException {
        List<String> filePaths = new ArrayList<>();
        String fileContents = "This is a simulated report.";
        for (int i = 0; i < 2; ++i) {
            String fileName = String.format("job_%d_report_%d.txt", context.getJobId(), i);
            filePaths.add(generateFile(fileName, fileContents.getBytes()));
        }
        return filePaths;
    }

    private String generateFile(String fileName, byte[] fileContents) throws IOException {
        String filePath = outputDirPath + File.separator + fileName;
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
        try (FileOutputStream fileStream = new FileOutputStream(file)) {
            fileStream.write(fileContents);
            fileStream.flush();
        }
        return filePath;
    }

    private void generateSimulatedResultsFile(List<String> derivedFilePaths, List<String> reportPaths, String resultsFilePath) throws ParserConfigurationException, TransformerConfigurationException, TransformerException {
        // SAMPLE GENERATED BY THE CODE BELOW:
        //
        // <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        // <autopsy_results>
        //    <derived_files>
        //       <derived_file>
        //          <local_path>C:\cases\Small\ModuleOutput\Sample Executable Ingest Module\job_1_derived_file_0.txt</local_path>
        //          <parent_file>/WINDOWS/system32/ntmsapi.dll</parent_file>
        //       </derived_file>
        //       <derived_file>
        //          <local_path>C:\cases\Small\ModuleOutput\Sample Executable Ingest Module\job_1_derived_file_1.txt</local_path>
        //          <parent_file>/WINDOWS/system32/ntmsapi.dll/job_1_derived_file_0.txt</parent_file>
        //       </derived_file>
        //    </derived_files>
        //    <artifacts>
        //       <artifact type="TSK_INTERESTING_FILE_HIT">
        //          <source_file>/WINDOWS/system32/ntmsapi.dll</source_file>
        //          <attribute type="TSK_SET_NAME">
        //             <value>SampleInterestingFilesSet</value>
        //             <source_module>Sample Executable Ingest Module</source_module>
        //          </attribute>
        //       </artifact>
        //       <artifact type="SampleArtifactType">
        //          <source_file>/WINDOWS/system32/ntmsapi.dll/job_1_derived_file_0.txt</source_file>
        //          <attribute type="SampleArtifactAttributeType">
        //             <value type="text">One</value>
        //          </attribute>
        //          <attribute type="SampleArtifactAttributeType">
        //             <value type="int32">2</value>
        //          </attribute>
        //          <attribute type="SampleArtifactAttributeType">
        //             <value type="int64">3</value>
        //          </attribute>
        //          <attribute type="SampleArtifactAttributeType">
        //             <value type="double">4.0</value>
        //          </attribute>
        //       </artifact>
        //    </artifacts>
        //    <reports>
        //       <report>
        //          <local_path>C:\cases\Small\ModuleOutput\Sample Executable Ingest Module\job_1_report_0.txt</local_path>
        //          <source_module>Sample Executable Ingest Module</source_module>
        //          <report_name>Sample Report</report_name>
        //       </report>
        //       <report>
        //          <local_path>C:\cases\Small\ModuleOutput\Sample Executable Ingest Module\job_1_report_1.txt</local_path>
        //          <source_module>Sample Executable Ingest Module</source_module>
        //       </report>
        //    </reports>
        // </autopsy_results>     

        // Create the XML DOM document and the root element.
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement(ExternalResultsXMLParser.TagNames.ROOT_ELEM.toString());
        doc.appendChild(rootElement);

        // Add a derived files list element to the root element.
        Element derivedFilesListElement = doc.createElement(ExternalResultsXMLParser.TagNames.DERIVED_FILES_LIST_ELEM.toString());
        rootElement.appendChild(derivedFilesListElement);

        // Add derived file elements to the derived files list element. Each 
        // file element gets required local path and parent file child elements.
        // Note that the local path of the derived file must be to a location in
        // the case directory or a subdirectory of the case directory and the 
        // parent file must be specified using the path format used in the case 
        // database, e.g., /WINDOWS/system32/ntmsapi.dll, where volume, file
        // system, etc. are not in the path.
        for (int i = 0; i < derivedFilePaths.size(); ++i) {
            String filePath = derivedFilePaths.get(i);
            Element derivedFileElement = doc.createElement(ExternalResultsXMLParser.TagNames.DERIVED_FILE_ELEM.toString());
            derivedFilesListElement.appendChild(derivedFileElement);
            Element localPathElement = doc.createElement(ExternalResultsXMLParser.TagNames.LOCAL_PATH_ELEM.toString());
            localPathElement.setTextContent(filePath);
            derivedFileElement.appendChild(localPathElement);
            Element parentPathElement = doc.createElement(ExternalResultsXMLParser.TagNames.PARENT_FILE_ELEM.toString());
            if (i == 0) {
                parentPathElement.setTextContent(this.fileInCaseDatabase);
            } else {
                parentPathElement.setTextContent(this.derivedFileInCaseDatabase);
            }
            derivedFileElement.appendChild(parentPathElement);
        }

        // Add an artifacts list element to the root element.
        Element artifactsListElement = doc.createElement(ExternalResultsXMLParser.TagNames.ARTIFACTS_LIST_ELEM.toString());
        rootElement.appendChild(artifactsListElement);

        // Add an artifact element to the artifacts list element with the required
        // artifact type attribute. A standard artifact type is used as the type 
        // attribute of this artifact element.
        Element artifactElement = doc.createElement(ExternalResultsXMLParser.TagNames.ARTIFACT_ELEM.toString());
        artifactElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getLabel());
        artifactsListElement.appendChild(artifactElement);

        // Add the required source file element to the artifact element. Note 
        // that source file must be either the local path of a derived file or a 
        // file in the case database.
        Element fileElement = doc.createElement(ExternalResultsXMLParser.TagNames.SOURCE_FILE_ELEM.toString());
        fileElement.setTextContent(this.fileInCaseDatabase);
        artifactElement.appendChild(fileElement);

        // Add an artifact attribute element to the artifact element. A standard 
        // artifact attribute type is used as the required type XML attribute of 
        // the artifact attribute element.
        Element artifactAttrElement = doc.createElement(ExternalResultsXMLParser.TagNames.ATTRIBUTE_ELEM.toString());
        artifactAttrElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), ATTRIBUTE_TYPE.TSK_SET_NAME.getLabel());
        artifactElement.appendChild(artifactAttrElement);

        // Add the required value element to the artifact attribute element, 
        // with an optional type XML attribute of ExternalXML.VALUE_TYPE_TEXT, 
        // which is the default.        
        Element artifactAttributeValueElement = doc.createElement(ExternalResultsXMLParser.TagNames.VALUE_ELEM.toString());
        artifactAttributeValueElement.setTextContent("SampleInterestingFilesSet");
        artifactAttrElement.appendChild(artifactAttributeValueElement);

        // Add an optional source module element to the artifact attribute 
        // element.
        Element artifactAttrSourceElement = doc.createElement(ExternalResultsXMLParser.TagNames.SOURCE_MODULE_ELEM.toString());
        artifactAttrSourceElement.setTextContent(moduleName);
        artifactAttrElement.appendChild(artifactAttrSourceElement);

        // Add an artifact element with a user-defined type.
        artifactElement = doc.createElement(ExternalResultsXMLParser.TagNames.ARTIFACT_ELEM.toString());
        artifactElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), "SampleArtifactType");
        artifactsListElement.appendChild(artifactElement);

        // Add the required source file element.
        fileElement = doc.createElement(ExternalResultsXMLParser.TagNames.SOURCE_FILE_ELEM.toString());
        fileElement.setTextContent(this.derivedFileInCaseDatabase);
        artifactElement.appendChild(fileElement);

        // Add artifact attribute elements with user-defined types to the 
        // artifact element, adding value elements of assorted types.
        for (int i = 0; i < 4; ++i) {
            artifactAttrElement = doc.createElement(ExternalResultsXMLParser.TagNames.ATTRIBUTE_ELEM.toString());
            artifactAttrElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), "SampleArtifactAttributeType");
            artifactElement.appendChild(artifactAttrElement);
            artifactAttributeValueElement = doc.createElement(ExternalResultsXMLParser.TagNames.VALUE_ELEM.toString());
            switch (i) {
                case 0:
                    artifactAttributeValueElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), ExternalResultsXMLParser.AttributeValues.VALUE_TYPE_TEXT.toString());
                    artifactAttributeValueElement.setTextContent("One");
                    break;
                case 1:
                    artifactAttributeValueElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), ExternalResultsXMLParser.AttributeValues.VALUE_TYPE_INT32.toString());
                    artifactAttributeValueElement.setTextContent("2");
                    break;
                case 2:
                    artifactAttributeValueElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), ExternalResultsXMLParser.AttributeValues.VALUE_TYPE_INT64.toString());
                    artifactAttributeValueElement.setTextContent("3");
                    break;
                case 3:
                    artifactAttributeValueElement.setAttribute(ExternalResultsXMLParser.AttributeNames.TYPE_ATTR.toString(), ExternalResultsXMLParser.AttributeValues.VALUE_TYPE_DOUBLE.toString());
                    artifactAttributeValueElement.setTextContent("4.0");
                    break;
            }
            artifactAttrElement.appendChild(artifactAttributeValueElement);
        }

        // Add a reports list element to the root element.
        Element reportsListElement = doc.createElement(ExternalResultsXMLParser.TagNames.REPORTS_LIST_ELEM.toString());
        rootElement.appendChild(reportsListElement);

        // Add report elements to the reports list element. Each report element 
        // gets required local path and source module child elements. There is
        // also an optional report name element. Note that the local path of the 
        // report must be to a location in the case directory or a subdirectory 
        // of the case directory and the parent file must be specified using the 
        // path format used in the case database, e.g., /WINDOWS/system32/ntmsapi.dll, 
        // where volume, file system, etc. are not in the path.
        for (int i = 0; i < reportPaths.size(); ++i) {
            String reportPath = reportPaths.get(i);
            Element reportElement = doc.createElement(ExternalResultsXMLParser.TagNames.REPORT_ELEM.toString());
            reportsListElement.appendChild(reportElement);
            Element reportPathElement = doc.createElement(ExternalResultsXMLParser.TagNames.LOCAL_PATH_ELEM.toString());
            reportPathElement.setTextContent(reportPath);
            reportElement.appendChild(reportPathElement);
            Element reportSourceModuleElement = doc.createElement(ExternalResultsXMLParser.TagNames.SOURCE_MODULE_ELEM.toString());
            reportSourceModuleElement.setTextContent(moduleName);
            reportElement.appendChild(reportSourceModuleElement);
            if (i == 0) {
                Element reportNameElement = doc.createElement(ExternalResultsXMLParser.TagNames.REPORT_NAME_ELEM.toString());
                reportNameElement.setTextContent("Sample Report");
                reportElement.appendChild(reportNameElement);
            }
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(resultsFilePath));
        transformer.transform(source, result);
    }
}
