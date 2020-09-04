/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.ileappanalyser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import static java.util.Locale.US;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.getCurrentCase;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Data source ingest module that runs Plaso against the image.
 */
public class ILeappAnalyserIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(ILeappAnalyserIngestModule.class.getName());
    private static final String MODULE_NAME = ILeappAnalyserModuleFactory.getModuleName();

    private static final String ILEAPP = "iLeapp"; //NON-NLS
    private static final String ILEAPP_EXECUTABLE = "ileapp.exe";//NON-NLS
    private static final String XMLFILE = "ileap-artifact-attribute-reference.xml"; //NON-NLS

    private File iLeappExecutable;
    private final HashMap<String, String> tsvFiles = new HashMap<>();
    private final HashMap<String, String> tsvFileArtifacts = new HashMap<>();
    private final HashMap<String, String> tsvFileArtifactComments = new HashMap<>();
    private final HashMap<String, List<List<String>>> tsvFileAttributes;

    private IngestJobContext context;
    private Case currentCase;
    private FileManager fileManager;

    ILeappAnalyserIngestModule() {
        this.tsvFileAttributes = new HashMap<>();
        
    }

    @NbBundle.Messages({
        "ILeappAnalyserIngestModule.executable.not.found=iLeapp Executable Not Found.",
        "ILeappAnalyserIngestModule.requires.windows=iLeapp module requires windows."})
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        if (false == PlatformUtil.isWindowsOS()) {
            throw new IngestModuleException(Bundle.ILeappAnalyserIngestModule_requires_windows());
        }

        configExtractor();
        
        try {
            iLeappExecutable = locateExecutable(ILEAPP_EXECUTABLE);
        } catch (FileNotFoundException exception) {
            logger.log(Level.WARNING, "iLeapp executable not found.", exception); //NON-NLS
            throw new IngestModuleException(Bundle.ILeappAnalyserIngestModule_executable_not_found(), exception);
        }

    }

    @NbBundle.Messages({
        "ILeappAnalyserIngestModule.error.running.iLeapp=Error running iLeapp, see log file.",
        "ILeappAnalyserIngestModule.error.creating.output.dir=Error creating iLeapp module output directory.",
        "ILeappAnalyserIngestModule.starting.iLeapp=Starting iLeapp",
        "ILeappAnalyserIngestModule.running.iLeapp=Running iLeapp",
        "ILeappAnalyserIngestModule.has.run=iLeapp",
        "ILeappAnalyserIngestModule.iLeapp.cancelled=iLeapp run was canceled",
        "ILeappAnalyserIngestModule.completed=iLeapp Processing Completed"})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {

        statusHelper.progress(Bundle.ILeappAnalyserIngestModule_starting_iLeapp(), 0);
        currentCase = Case.getCurrentCase();
        fileManager = currentCase.getServices().getFileManager();

        List<AbstractFile> iLeappFilesToProcess = findiLeappFilesToProcess(dataSource);
        
        statusHelper.switchToDeterminate(iLeappFilesToProcess.size());
        
        try {
            loadConfigFile();
        } catch (IngestModuleException ex) {
            logger.log(Level.SEVERE, String.format("Error loading config file %s", XMLFILE), ex);
            return ProcessResult.ERROR;            
        }
        
        Integer filesProcessedCount = 0;
        
        if (!iLeappFilesToProcess.isEmpty()) {
            // Run iLeapp
            for (AbstractFile iLeappFile: iLeappFilesToProcess) {

                String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z", Locale.US).format(System.currentTimeMillis());//NON-NLS
                Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), ILEAPP, currentTime);
                try {
                    Files.createDirectories(moduleOutputPath);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, String.format("Error creating iLeapp output directory %s", moduleOutputPath.toString()), ex);
                    return ProcessResult.ERROR;
                }

                statusHelper.progress(NbBundle.getMessage(this.getClass(), "ILeappAnalyserIngestModule.processing.file", iLeappFile.getName()), filesProcessedCount);
                ProcessBuilder iLeappCommand = buildiLeappCommand(moduleOutputPath, iLeappFile.getLocalAbsPath(), iLeappFile.getNameExtension());
                try {
                    int result = ExecUtil.execute(iLeappCommand, new DataSourceIngestModuleProcessTerminator(context));
                    if (result != 0) {
                        logger.log(Level.SEVERE, String.format("Error running iLeapp, error code returned %d", result)); //NON-NLS
                        return ProcessResult.ERROR;
                    } 
                } catch (IOException ex) {
                     logger.log(Level.SEVERE, String.format("Error when trying to execute iLeapp program against file %s", iLeappFile.getLocalAbsPath()), ex);
                     return ProcessResult.ERROR;
                }

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "ILeapp Analyser ingest module run was canceled"); //NON-NLS
                    return ProcessResult.OK;
                }
                
                try {
                    List<String> iLeappTsvOutputFiles = findTsvFiles(moduleOutputPath);
                    if (!iLeappTsvOutputFiles.isEmpty()) {
                        processiLeappFiles(iLeappTsvOutputFiles, iLeappFile, statusHelper);
                    }
                } catch (IOException | IngestModuleException ex) {
                  logger.log(Level.SEVERE, String.format("Error trying to process iLeapp output files in directory %s. ", moduleOutputPath.toString()), ex); //NON-NLS
                  return ProcessResult.ERROR;
                }
                
                filesProcessedCount++;
            }
        
        }
        
        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                Bundle.ILeappAnalyserIngestModule_has_run(),
                Bundle.ILeappAnalyserIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    /**
     * Find the files to process that will be processed by the iLeapp program
     * 
     * @param dataSource
     * @return List of abstract files to process.
     */
    private List<AbstractFile> findiLeappFilesToProcess(Content dataSource) {
        
        List<AbstractFile> iLeappFiles = new ArrayList<>();
        
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        // findFiles use the SQL wildcard # in the file name
        try {
            iLeappFiles = fileManager.findFiles(dataSource, "%", "/"); //NON-NLS
        } catch (TskCoreException ex) {
            //Change this
           logger.log(Level.WARNING, "No files found to process"); //NON-NLS
           return iLeappFiles;
        }
        
        List<AbstractFile> iLeappFilesToProcess = new ArrayList<>();
        for (AbstractFile iLeappFile: iLeappFiles) {
            if ((iLeappFile.getName().toLowerCase().contains(".zip") || (iLeappFile.getName().toLowerCase().contains(".tar")) 
                 || iLeappFile.getName().toLowerCase().contains(".tgz"))) {
                iLeappFilesToProcess.add(iLeappFile);
            }
        }
            
        return iLeappFilesToProcess;
    }
        
    private ProcessBuilder buildiLeappCommand(Path moduleOutputPath, String sourceFilePath, String iLeappFileSystemType) {

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + iLeappExecutable + "\"", //NON-NLS
                "-t", iLeappFileSystemType, //NON-NLS
                "-i", sourceFilePath, //NON-NLS
                "-o", moduleOutputPath.toString()
        );
        processBuilder.redirectError(moduleOutputPath.resolve("iLeapp_err.txt").toFile());  //NON-NLS
        processBuilder.redirectOutput(moduleOutputPath.resolve("iLeapp_out.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force log2timeline/psort to run with
         * the same permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        return processBuilder;
    }

    private static File locateExecutable(String executableName) throws FileNotFoundException {
        String executableToFindName = Paths.get(ILEAPP, executableName).toString();

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, ILeappAnalyserIngestModule.class.getPackage().getName(), false);
        if (null == exeFile || exeFile.canExecute() == false) {
            throw new FileNotFoundException(executableName + " executable not found.");
        }
        return exeFile;
    }
    
        @NbBundle.Messages({
        "ILeappAnalyserIngestModule.error.reading.iLeapp.directory=Error reading iLeapp Output directory."})
   
    /**
     * Find the tsv files in the iLeapp output directory and match them to files we know we want to process
     * and return the list to process those files.
     */
    private List<String> findTsvFiles(Path iLeapOutputDir) throws IngestModuleException {
        List<String> allTsvFiles = new ArrayList<>();
        List<String> foundTsvFiles = new ArrayList<>();
        
        try (Stream<Path> walk = Files.walk(iLeapOutputDir)) {

	    allTsvFiles = walk.map(x -> x.toString())
			.filter(f -> f.endsWith(".tsv")).collect(Collectors.toList());

            for (String tsvFile : allTsvFiles) {
                if (tsvFiles.containsKey(FilenameUtils.getName(tsvFile))) {
                    foundTsvFiles.add(tsvFile);
                }
            }
            
        } catch (IOException e) {
            throw new IngestModuleException(Bundle.ILeappAnalyserIngestModule_error_reading_iLeapp_directory() + iLeapOutputDir.toString(), e);
        } 
        
        return foundTsvFiles;
        
    }
    
    /**
     * Process the iLeapp files that were found that match the xml mapping file
     * @param iLeappFilesToProcess List of files to process
     * @param iLeappImageFile Abstract file to create artifact for
     * @param statusHelper progress bar update
     * @throws FileNotFoundException
     * @throws IOException 
     */
    private void processiLeappFiles(List<String> iLeappFilesToProcess, AbstractFile iLeappImageFile, DataSourceIngestModuleProgress statusHelper) throws FileNotFoundException, IOException, IngestModuleException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
       
        for (String iLeappFileName : iLeappFilesToProcess) { 
            String fileName = FilenameUtils.getName(iLeappFileName);
            statusHelper.progress(NbBundle.getMessage(this.getClass(), "ILeappAnalyserIngestModule.parsing.file", fileName));
            File iLeappFile = new File(iLeappFileName);
//            List<List<String>> attrList = new ArrayList<>();
            if (tsvFileAttributes.containsKey(fileName)) {
                List<List<String>> attrList = tsvFileAttributes.get(fileName);
                try {
                    BlackboardArtifact.Type artifactType = Case.getCurrentCase().getSleuthkitCase().getArtifactType(tsvFileArtifacts.get(fileName));
                
                    try (BufferedReader reader = new BufferedReader(new FileReader(iLeappFile))) {
                        String line = reader.readLine();
                        // Check first line, if it is null then no heading so nothing to match to, close and go to next file.
                        if (line != null) {
                            HashMap<Integer, String> columnNumberToProcess = findColumnsToProcess(line, attrList);
                            line = reader.readLine();
                            while (line != null) {
//                                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                                Collection<BlackboardAttribute> bbattributes = processReadLine(line, columnNumberToProcess, fileName);
                                if (!bbattributes.isEmpty()) {
                                    BlackboardArtifact bbartifact = createArtifactWithAttributes(artifactType.getTypeID(), iLeappImageFile, bbattributes);
                                    if (bbartifact != null) {
                                        bbartifacts.add(bbartifact);
                                    }
                                }
                                line = reader.readLine();
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                   // check this
                   throw new IngestModuleException(String.format("Error getting Blackboard Artifact Type for %s", tsvFileArtifacts.get(fileName)), ex); 
                }
            }
            
        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
        
    }
    
    /**
     * Process the line read and create the necessary attributes for it
     * @param line a tsv line to process that was read
     * @param columnNumberToProcess Which columns to process in the tsv line
     * @return 
     */
    private Collection<BlackboardAttribute> processReadLine(String line, HashMap<Integer, String> columnNumberToProcess, String fileName) throws IngestModuleException {
        String[] columnValues = line.split("\\t");
        
        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
        
        for (Map.Entry<Integer, String> columnToProcess: columnNumberToProcess.entrySet()) {
            Integer columnNumber = columnToProcess.getKey();
            String attributeName = columnToProcess.getValue();
            
            try {
                BlackboardAttribute.Type attributeType = Case.getCurrentCase().getSleuthkitCase().getAttributeType(attributeName.toUpperCase());
                if (attributeType == null) {
                    break;
                }
                String attrType = attributeType.getValueType().getLabel().toUpperCase();
                if (attrType.matches("STRING")) {
                    bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, columnValues[columnNumber]));
                } else if (attrType.matches("INTEGER")) {
                    bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Integer.valueOf(columnValues[columnNumber])));                   
                } else if (attrType.matches("LONG")) {
                    bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Long.valueOf(columnValues[columnNumber])));                   
                } else if (attrType.matches("DOUBLE")) {
                    bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Double.valueOf(columnValues[columnNumber])));                   
                } else if (attrType.matches("BYTE")) {
                    bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Byte.valueOf(columnValues[columnNumber])));                   
                } else if (attrType.matches("DATETIME")) {
                    // format of data should be the same in all the data and the format is 2020-03-28 01:00:17
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-d HH:mm:ss", US);
                    Long dateLong = Long.valueOf(0);
                    try {
                        Date newDate = dateFormat.parse(columnValues[columnNumber]);
                        dateLong = newDate.getTime()/1000;
                        bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, dateLong));                   
                    } catch (ParseException ex) {
                        // catching error and displaying date that could not be parsed
                        // we set the timestamp to 0 and continue on processing
                        logger.log(Level.WARNING, String.format("Failed to parse date/time %s for attribute.", columnValues[columnNumber]), ex); //NON-NLS
                    }
                } else if (attrType.matches("JSON")) {
                    
                    bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, columnValues[columnNumber]));                   
                } else {
                    // Log this and continue on with processing
                    logger.log(Level.WARNING, String.format("Attribute Type %s not defined.", attrType)); //NON-NLS                   
                }
   
            } catch (TskCoreException ex) {
                throw new IngestModuleException(String.format("Error getting Attribute type for Attribute Name %s", attributeName), ex); //NON-NLS
            }
        }   

        if (tsvFileArtifactComments.containsKey(fileName)) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, tsvFileArtifactComments.get(fileName)));    
        }
        
        return bbattributes;    

    }
    
    /**
     * Process the first line of the tsv file which has the headings.  Match the headings to the columns in the XML
     * mapping file so we know which columns to process.
     * @param line a tsv heading line of the columns in the file
     * @param attrList the list of headings we want to process
     * @return the numbered column(s) and attribute(s) we want to use for the column(s)
     */
    private HashMap<Integer, String> findColumnsToProcess(String line, List<List<String>> attrList) {
        String[] columnNames = line.split("\\t");
        HashMap<Integer, String> columnsToProcess = new HashMap<>();
        
        Integer columnPosition = 0;
        for (String columnName : columnNames) {
            // for some reason the first column of the line has unprintable characters so removing them
            String cleanColumnName = columnName.replaceAll("[^\\n\\r\\t\\p{Print}]", "");
            for (List<String> atList : attrList) {
                if (atList.contains(cleanColumnName.toLowerCase())) {
                    columnsToProcess.put(columnPosition, atList.get(0));
                    break;
                }
            }
            columnPosition++;
        }
        
        return columnsToProcess;
    }

        @NbBundle.Messages({
        "ILeappAnalyserIngestModule.cannot.load.artifact.xml=Cannor load xml artifact file.",
        "ILeappAnalyserIngestModule.cannotBuildXmlParser=Cannot buld an XML parser.",
        "ILeappAnalyserIngestModule_cannotParseXml=Cannot Parse XML file.",
        "ILeappAnalyserIngestModule.postartifacts_error=Error posting Blackboard Artifact",
        "ILeappAnalyserIngestModule.error.creating.new.artifacts=Error creating new artifacts."
        })

    /**
     * Read the XML config file and load the mappings into maps
     */    
    private void loadConfigFile() throws IngestModuleException {
        Document xmlinput;
        try {
            String path = PlatformUtil.getUserConfigDirectory() + File.separator + XMLFILE;
            File f = new File(path);
            logger.log(Level.INFO, "Load successful"); //NON-NLS
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            xmlinput = db.parse(f);

        } catch (IOException e) {
            throw new IngestModuleException(Bundle.ILeappAnalyserIngestModule_cannot_load_artifact_xml() + e.getLocalizedMessage(), e); //NON-NLS
        } catch (ParserConfigurationException pce) {
            throw new IngestModuleException(Bundle.ILeappAnalyserIngestModule_cannotBuildXmlParser() + pce.getLocalizedMessage(), pce); //NON-NLS
        } catch (SAXException sxe) {
            throw new IngestModuleException(Bundle.ILeappAnalyserIngestModule_cannotParseXml() + sxe.getLocalizedMessage(), sxe); //NON-NLS
        }

        NodeList nlist = xmlinput.getElementsByTagName("FileName"); //NON-NLS

        for (int i = 0; i < nlist.getLength(); i++) {
            NamedNodeMap nnm = nlist.item(i).getAttributes();
            tsvFiles.put(nnm.getNamedItem("filename").getNodeValue(), nnm.getNamedItem("description").getNodeValue());
        
        }
        
        NodeList artifactNlist = xmlinput.getElementsByTagName("ArtifactName"); //NON-NLS
        for (int k = 0; k < artifactNlist.getLength(); k++) {
           NamedNodeMap nnm = artifactNlist.item(k).getAttributes();
           String artifactName = nnm.getNamedItem("artifactname").getNodeValue();
           String comment = nnm.getNamedItem("comment").getNodeValue();
           String parentName = artifactNlist.item(k).getParentNode().getAttributes().getNamedItem("filename").getNodeValue();
           
           tsvFileArtifacts.put(parentName, artifactName);
           
           if (!comment.toLowerCase().matches("null")) {
               tsvFileArtifactComments.put(parentName, comment);
           }
        }
        
        NodeList attributeNlist = xmlinput.getElementsByTagName("AttributeName"); //NON-NLS
        for (int k = 0; k < attributeNlist.getLength(); k++) {
           List<String> attributeList = new ArrayList<>();
           NamedNodeMap nnm = attributeNlist.item(k).getAttributes();
           String attributeName = nnm.getNamedItem("attributename").getNodeValue();
           if (!attributeName.toLowerCase().matches("null")) {
               String columnName = nnm.getNamedItem("columnName").getNodeValue();
               String required = nnm.getNamedItem("required").getNodeValue();
               String parentName = attributeNlist.item(k).getParentNode().getParentNode().getAttributes().getNamedItem("filename").getNodeValue();
           
               attributeList.add(attributeName.toLowerCase());
               attributeList.add(columnName.toLowerCase());
               attributeList.add(required.toLowerCase());
           
               if (tsvFileAttributes.containsKey(parentName)) {
                    List<List<String>> attrList = tsvFileAttributes.get(parentName);
                    attrList.add(attributeList);
                    tsvFileAttributes.replace(parentName, attrList);
               } else {
                    List<List<String>> attrList = new ArrayList<>();
                    attrList.add(attributeList);
                    tsvFileAttributes.put(parentName, attrList);
               }
           }
        }
        
    }

    /**
     * Generic method for creating a blackboard artifact with attributes
     *
     * @param type         is a blackboard.artifact_type enum to determine which
     *                     type the artifact should be
     * @param content      is the Content object that needs to have the
     *                     artifact added for it
     * @param bbattributes is the collection of blackboard attributes that need
     *                     to be added to the artifact after the artifact has
     *                     been created
     * @return The newly-created artifact, or null on error
     */
    protected BlackboardArtifact createArtifactWithAttributes(int type, AbstractFile abstractFile, Collection<BlackboardAttribute> bbattributes) {
        try {
            BlackboardArtifact bbart = abstractFile.newArtifact(type);
            bbart.addAttributes(bbattributes);
            return bbart;
        } catch (TskException ex) {
            logger.log(Level.WARNING, Bundle.ILeappAnalyserIngestModule_error_creating_new_artifacts(), ex); //NON-NLS
        }
        return null;
    }

    /**
     * Method to post a list of BlackboardArtifacts to the blackboard.
     * 
     * @param artifacts A list of artifacts.  IF list is empty or null, the function will return.
     */
    void postArtifacts(Collection<BlackboardArtifact> artifacts) {
        if(artifacts == null || artifacts.isEmpty()) {
            return;
        }
        
        try{
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifacts(artifacts, MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, Bundle.ILeappAnalyserIngestModule_postartifacts_error(), ex); //NON-NLS
        }
    }
    
    /**
     * Extract the iLeapp config xml file to the user directory to process
     * 
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
     */
    void configExtractor() throws IngestModuleException {
        try {
            PlatformUtil.extractResourceToUserConfigDir(ILeappAnalyserIngestModule.class, XMLFILE, true);
        } catch (IOException e) {
            String message = NbBundle.getMessage(this.getClass(), "ILeappAnalyserIngestModule.init.exception_msg", XMLFILE);
            logger.log(Level.SEVERE, message, e);
            throw new IngestModuleException(message, e);
        }

    }
    
    
}
