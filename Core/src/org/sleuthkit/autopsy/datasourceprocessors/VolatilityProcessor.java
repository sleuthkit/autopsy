/*
 * Autopsy 
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

//@NbBundle.Messages({
//    "VolatilityProcessor.PermissionsNotSufficient=Insufficient permissions accessing",
//    "VolatilityProcessor.PermissionsNotSufficientSeeReference=See 'Shared Drive Authentication' in Autopsy help.",
//    "# {0} - output directory name", "cannotCreateOutputDir.message=Unable to create output directory: {0}.",
//    "unsupportedOS.message=PhotoRec module is supported on Windows platforms only.",
//    "missingExecutable.message=Unable to locate PhotoRec executable.",
//   "cannotRunExecutable.message=Unable to execute PhotoRec."
//})

/**
 *
 */
class VolatilityProcessor implements Runnable{
    private static final String VOLATILITY_DIRECTORY = "Volatility"; //NON-NLS
    private static final String VOLATILITY_EXECUTABLE = "volatility_2.6_win64_standalone.exe"; //NON-NLS
    private final String memoryImagePath;
    private final List<String> PluginsToRun;
    private final Image dataSource;
    private static final String SEP = System.getProperty("line.separator");
    private static final Logger logger = Logger.getLogger(VolatilityProcessor.class.getName());
    private String moduleOutputPath;
    private File executableFile;
    private final IngestServices services = IngestServices.getInstance();
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private boolean isCancelled;
    private FileManager fileManager;

    public VolatilityProcessor(String ImagePath, List<String> PlugInToRuns, Image dataSource, DataSourceProcessorProgressMonitor progressMonitor) {
        this.memoryImagePath = ImagePath;
        this.PluginsToRun = PlugInToRuns;
        this.dataSource = dataSource;
        this.progressMonitor = progressMonitor;
    }
    
    @Override
    public void run() {  
        Path execName = Paths.get(VOLATILITY_DIRECTORY, VOLATILITY_EXECUTABLE);
        executableFile = locateExecutable(execName.toString());
        if (executableFile == null) {
            logger.log(Level.SEVERE, "Volatility exe not found");
            return;
        }
        final Case currentCase = Case.getCurrentCase();
        fileManager = currentCase.getServices().getFileManager();

        // make a unique folder for this image
        moduleOutputPath = currentCase.getModulesOutputDirAbsPath() + File.separator + "Volatility" + File.separator + dataSource.getId();
        File directory = new File(String.valueOf(moduleOutputPath));
        if(!directory.exists()){
            directory.mkdirs();
            progressMonitor.setProgressText("Running imageinfo");
            executeVolatility("imageinfo");
        }

        progressMonitor.setIndeterminate(false);
        for (int i = 0; i < PluginsToRun.size(); i++) {
            if (isCancelled)
                break;
            String pluginToRun = PluginsToRun.get(i);
            progressMonitor.setProgressText("Processing " + pluginToRun + " module");
            executeVolatility(pluginToRun);
            progressMonitor.setProgress(i / PluginsToRun.size() * 100);
        } 
        // @@@ NEed to report back here if there were errors
    }

    private void executeVolatility(String pluginToRun) {
        try {        
            List<String> commandLine = new ArrayList<>();
            commandLine.add("\"" + executableFile + "\"");
            File memoryImage = new File(memoryImagePath);
            commandLine.add("--filename=" + memoryImage.getName()); //NON-NLS
            
            File imageInfoOutputFile = new File(moduleOutputPath + "\\imageinfo.txt");
            if (imageInfoOutputFile.exists()) {
               String memoryProfile = parseImageInfoOutput(imageInfoOutputFile);
               if (memoryProfile == null) {
                   // @@@ LOG THIS 
                   return;
               }
               commandLine.add("--profile=" + memoryProfile);
            }
            
            commandLine.add(pluginToRun); //NON-NLS
          
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            // Add environment variable to force Volatility to run with the same permissions Autopsy uses
            processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
            processBuilder.redirectOutput(new File(moduleOutputPath + "\\" + pluginToRun + ".txt"));
            processBuilder.redirectError(new File(moduleOutputPath + "\\Volatility_Run.err"));
            processBuilder.directory(new File(memoryImage.getParent()));
            
            int exitVal = ExecUtil.execute(processBuilder);
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Volatility non-0 exit value for module: " + pluginToRun);
                return;
            }
            
            if (isCancelled)
                return;
            
            if (pluginToRun.matches("dlllist") || pluginToRun.matches("handles") || pluginToRun.matches("cmdline") || pluginToRun.matches("psxview") ||
                pluginToRun.matches("pslist") || pluginToRun.matches("psscan") || pluginToRun.matches("pstree") || pluginToRun.matches("svcscan") ||
                pluginToRun.matches("filescan") || pluginToRun.matches("shimcache")) {  
                 scanOutputFile(pluginToRun, new File(moduleOutputPath + "\\" + pluginToRun + ".txt"));  
                }
    
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to run Volatility", ex); //NON-NLS
            //this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getName()));
        }
    }
    
        /**
     * Finds and returns the path to the executable, if able.
     *
     * @param executableToFindName The name of the executable to find
     *
     * @return A File reference or null
     */
    private static File locateExecutable(String executableToFindName) {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS()) {
           return null;
        }

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, VolatilityProcessor.class.getPackage().getName(), false);
        if (null == exeFile) {
            return null;
        }

        if (!exeFile.canExecute()) {
            return null;
        }

        return exeFile;
    }

    private String parseImageInfoOutput(File imageOutputFile) throws FileNotFoundException {
            // create a Buffered Reader object instance with a FileReader
            try (
                 BufferedReader br = new BufferedReader(new FileReader(imageOutputFile))) {
                 // read the first line from the text file
                 String fileRead = br.readLine();
                 br.close();
                 String[] profileLine = fileRead.split(":");
                 String[] memProfile = profileLine[1].split(",|\\(");
                 return memProfile[0].replaceAll("\\s+","");
            } catch (IOException ex) { 
                Exceptions.printStackTrace(ex);
                // @@@ Need to log this or rethrow it
            } 
     
        return null;
    }
    
    private void scanOutputFile(String pluginName, File PluginOutput) {
        List<String> fileNames = new ArrayList<>();
        Map<String, Map> fileName = new HashMap<String, Map>();   
        Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard();
         
          try {
            if (pluginName.matches("dlllist")) { 
               fileName = Parse_Dlllist(PluginOutput);
            } else if (pluginName.matches("handles")) {
               fileName = Parse_Handles(PluginOutput);
            } else if (pluginName.matches("cmdline")) { 
               fileName = Parse_Cmdline(PluginOutput);                
            } else if (pluginName.matches("psxview")){
               fileName = Parse_Psxview(PluginOutput);
            } else if (pluginName.matches("pslist")) {
                fileName = Parse_Pslist(PluginOutput);
            } else if (pluginName.matches("psscan")) { 
                fileName = Parse_Psscan(PluginOutput);
            } else if (pluginName.matches("pstree")) {
                fileName = Parse_Pstree(PluginOutput);
            } else if (pluginName.matches("svcscan")) {
                fileName = Parse_Svcscan(PluginOutput);            
            } else if (pluginName.matches("filescan")) {
                fileName = Parse_Filescan(PluginOutput);
            } else  {  
                fileName = Parse_Shimcache(PluginOutput);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to parse files " + PluginOutput, ex); //NON-NLS
            //this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getName()));
        }
        try {
            if (isCancelled)
                return;
                
            List<AbstractFile> volFiles = new ArrayList<>();
            String filename;
            String path;
            Map<String, String> fileMap = new HashMap<>(); 
            fileMap = dedupeFileList(fileName);
            Set<String> keySet = fileMap.keySet();
            Iterator<String> keySetIterator = keySet.iterator();   
            while (keySetIterator.hasNext()) {
                path = keySetIterator.next();
                filename = fileMap.get(path);
                try {
                    volFiles = fileManager.findFiles(filename.trim(), path); //NON-NLS
                } catch (TskCoreException ex) {
                    //String msg = NbBundle.getMessage(this.getClass(), "Chrome.getHistory.errMsg.errGettingFiles");
                    logger.log(Level.SEVERE, "Error in Finding FIles", ex);
                    return;
                }
                volFiles.forEach((volFile) -> {
                    try {
                        String MODULE_NAME = "VOLATILITY";
                        BlackboardArtifact volArtifact = volFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                        BlackboardAttribute att1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME,
                                "Volatility Plugin " + pluginName);
                        BlackboardAttribute att2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME,
                                "Volatility Plugin " + pluginName);
                        volArtifact.addAttribute(att1);
                        volArtifact.addAttribute(att2);

                        try {
                            // index the artifact for keyword search
                            blackboard.indexArtifact(volArtifact);
                        } catch (Blackboard.BlackboardException ex) {
                            logger.log(Level.SEVERE, "Unable to index blackboard artifact " + volArtifact.getArtifactID(), ex); //NON-NLS
                        }

                        // fire event to notify UI of this new artifact
                        services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT));
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "Failed to create BlackboardArtifact.", ex); // NON-NLS
                    } catch (IllegalStateException ex) {
                        logger.log(Level.SEVERE, "Failed to create BlackboardAttribute.", ex); // NON-NLS
                    }
                });
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error in processing List of FIles", ex); //NON-NLS   
        }
    } 

    private Map<String, Map> Parse_Handles(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                 Map<String, String> fileNameMap = new HashMap<>();
                 if (line.length() > 65) {
                    line_type = line.substring(64,68);
                    if (line_type.matches("File")) {
                        counter = counter + 1;
                        file_path = line.substring(82);
                        file_path = file_path.replaceAll("Device\\\\","");
                        file_path = file_path.replaceAll("HarddiskVolume[0-9]\\\\", "");
                        File volfile = new File(file_path);
                        fileName = volfile.getName();
                        filePath = volfile.getParent();
                        if (filePath != null && !filePath.isEmpty()) {
                            filePath = filePath.replaceAll("\\\\", "%");
                            filePath = "%" + filePath + "%";
                        } else {
                            filePath = "%";
                        }                    
                        fileNameMap.put(filePath, fileName);
                        fileMap.put(file_path, fileNameMap);
                    }
                 }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;
    }
    
    private Map<String, Map> Parse_Dlllist(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                 Map<String, String> fileNameMap = new HashMap<>();
                 if (line.contains("Command line : ")) {
                     counter = counter + 1;
                    file_path = line.substring(15);
                    file_path = file_path.replaceAll("SystemRoot", "");
                    File volfile = new File(file_path);
                    fileName = volfile.getName();
                    if ((fileName.lastIndexOf(".") + 3) < fileName.length()) {
                        fileName = fileName.substring(0, fileName.lastIndexOf(".")+4);
                    }
                    filePath = volfile.getParent();
                    if (filePath != null && !filePath.isEmpty()) {
                        if (filePath.contains(":")) {
                            filePath = filePath.substring(filePath.indexOf(":")+1);
                        }
                        filePath = filePath.replaceAll("\\\\", "%");
                        filePath = "%" + filePath + "%";
                    } else {
                        filePath = "%";
                    }                    
                    fileNameMap.put(filePath, fileName);
                    fileMap.put(file_path, fileNameMap);

                 } else if (line.length() > 61) {
                    counter = counter + 1;
                    file_path = line.substring(57);
                    file_path = file_path.replaceAll("SystemRoot", "");
                    File volfile = new File(file_path);
                    fileName = volfile.getName();
                    filePath = volfile.getParent();
                    if (filePath != null && !filePath.isEmpty()) {
                        if (filePath.contains(":")) {
                            filePath = filePath.substring(filePath.indexOf(":")+1);
                        }
                        filePath = filePath.replaceAll("\\\\", "%");
                        filePath = "%" + filePath + "%";
                    } else {
                        filePath = "%";
                    }                    
                    fileNameMap.put(filePath, fileName);
                    fileMap.put(file_path, fileNameMap);
                 }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;
    }

   private Map<String, Map> Parse_Filescan(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                try {
                    Map<String, String> fileNameMap = new HashMap<>();
                    counter = counter + 1;
                    file_path = line.substring(41);
                    file_path = file_path.replaceAll("Device\\\\","");
                    file_path = file_path.replaceAll("HarddiskVolume[0-9]\\\\", "");
                    File volfile = new File(file_path);
                    fileName = volfile.getName();
                    filePath = volfile.getParent();
                    if (filePath != null && !filePath.isEmpty()) {
                        filePath = filePath.replaceAll("\\\\", "%");
                        filePath = "%" + filePath + "%";
                    } else {
                        filePath = "%";
                    }                    
                    fileNameMap.put(filePath, fileName);
                    fileMap.put(file_path, fileNameMap);
                } catch (StringIndexOutOfBoundsException ex) {
                  // TO DO  Catch exception
                }
            }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }
    
    private Map<String, Map> Parse_Cmdline(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                Map<String, String> fileNameMap = new HashMap<>();
                 if (line.length() > 16) {
                    line_type = line.substring(0,15);
                    if (line_type.startsWith("Command line : ")) {
                        counter = counter + 1;
                        file_path = line.substring(15);
                        File volfile = new File(file_path);
                        fileName = volfile.getName();
                        if ((fileName.lastIndexOf(".") + 3) < fileName.length()) {
                            fileName = fileName.substring(0, fileName.lastIndexOf(".")+4);
                        }
                        filePath = volfile.getParent();
                        if (filePath != null && !filePath.isEmpty()) {
                            if (filePath.contains(":")) {
                                filePath = filePath.substring(filePath.indexOf(":")+1);
                            }
                            filePath = filePath.replaceAll("\\\\", "%");
                            filePath = "%" + filePath + "%";
                        } else {
                            filePath = "%";
                        }                    
                        fileNameMap.put(filePath, fileName);
                        fileMap.put(file_path, fileNameMap);
                    }
                 }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }
    
    private Map<String, Map> Parse_Shimcache(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                Map<String, String> fileNameMap = new HashMap<>();
                 if (line.length() > 36) {
                    counter = counter + 1;
                    file_path = line.substring(38);
                    File volfile = new File(file_path);
                    fileName = volfile.getName();
                    filePath = volfile.getParent();
                    if (filePath != null && !filePath.isEmpty()) {
                        filePath = filePath.replaceAll("\\\\", "%");
                        filePath = "%" + filePath + "%";
                    } else {
                        filePath = "%";
                    }                    
                    fileNameMap.put(filePath, fileName);
                    fileMap.put(file_path, fileNameMap);
                 }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }
    
    private Map<String, Map> Parse_Psscan(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                Map<String, String> fileNameMap = new HashMap<>();
                counter = counter + 1;
                file_path = line.substring(19, 37);
                File volfile = new File(file_path);
                fileName = volfile.getName();
                filePath = volfile.getParent();
                if (filePath != null && !filePath.isEmpty()) {
                    filePath = filePath.replaceAll("\\\\", "%");
                    filePath = "%" + filePath + "%";
                } else {
                    filePath = "%";
                }                    
                fileNameMap.put(filePath, fileName);
                fileMap.put(file_path, fileNameMap);
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }

    private Map<String, Map> Parse_Pslist(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                Map<String, String> fileNameMap = new HashMap<>();
                counter = counter + 1;
                file_path = line.substring(19, 41);
                File volfile = new File(file_path);
                fileName = volfile.getName();
                filePath = volfile.getParent();
                if (filePath != null && !filePath.isEmpty()) {
                    filePath = filePath.replaceAll("\\\\", "%");
                    filePath = "%" + filePath + "%";
                } else {
                    filePath = "%";
                }                    
                fileNameMap.put(filePath, fileName);
                fileMap.put(file_path, fileNameMap);
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }

    private Map<String, Map> Parse_Psxview(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                Map<String, String> fileNameMap = new HashMap<>();
                counter = counter + 1;
                file_path = line.substring(19, 41);
                File volfile = new File(file_path);
                fileName = volfile.getName();
                filePath = volfile.getParent();
                if (filePath != null && !filePath.isEmpty()) {
                    filePath = filePath.replaceAll("\\\\", "%");
                    filePath = "%" + filePath + "%";
                } else {
                    filePath = "%";
                }                    
                fileNameMap.put(filePath, fileName);
                fileMap.put(file_path, fileNameMap);
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }

    private Map<String, Map> Parse_Pstree(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                Map<String, String> fileNameMap = new HashMap<>();
                counter = counter + 1;
                if (line.contains(":")) {
                    file_path = line.substring(line.indexOf(":") + 1, 52);
                    File volfile = new File(file_path);
                    fileName = volfile.getName();
                    filePath = volfile.getParent();
                    if (filePath != null && !filePath.isEmpty()) {
                        filePath = filePath.replaceAll("\\\\", "%");
                        filePath = "%" + filePath + "%";
                    } else {
                        filePath = "%";
                    }                    
                    fileNameMap.put(filePath, fileName);
                    fileMap.put(file_path, fileNameMap);
                }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }

    private Map<String, Map> Parse_Svcscan(File PluginFile) {
        List<String> fileNames = new ArrayList<>();
        String line;
        String line_type;
        String file_path;
        Map<String, Map> fileMap = new HashMap<>();
        String filePath;
        String fileName;
        int counter = 0;
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                Map<String, String> fileNameMap = new HashMap<>();
                if (line.startsWith("Binary Path: ")) {
                    counter = counter + 1;
                    file_path = line.substring(13);
                    File volfile = new File(file_path);
                    fileName = volfile.getName();
                    if ((fileName.lastIndexOf(".") + 3) < fileName.length()) {
                        fileName = fileName.substring(0, fileName.lastIndexOf(".")+4);
                    }
                    filePath = volfile.getParent();
                    if (filePath != null && !filePath.isEmpty()) {
                        if (filePath.contains(":")) {
                            filePath = filePath.substring(filePath.indexOf(":")+1);
                        }
                        filePath = filePath.replaceAll("\\\\", "%");
                        filePath = "%" + filePath + "%";
                    } else {
                        filePath = "%";
                    }                    
                    fileNameMap.put(filePath, fileName);
                    fileMap.put(file_path, fileNameMap);
                 }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileMap;     
    }
    
    private Map<String, String> dedupeFileList(Map<String, Map> fileList) {
            Map<String, String> fileMap = new HashMap<>();
            Map<String, String> newFileMap = new HashMap<>();
            Set<String> keySet = fileList.keySet();
            Iterator<String> keySetIterator = keySet.iterator();   
            while (keySetIterator.hasNext()) {
                String key = keySetIterator.next();
                fileMap = fileList.get(key);
                for ( String key1 : fileMap.keySet() ) {
                    newFileMap.put(key1,fileMap.get(key1));
                }
            }
            return newFileMap;
    }

    private List<String> parsePluginOutput(File pluginFile) throws FileNotFoundException {
            // create a Buffered Reader object instance with a FileReader
            List<String> fileNames = new ArrayList<>();
            String line;
            Pattern filePathPattern = Pattern.compile("(\\\\[.-\\\\\\w\\\\s]+)+");
            Pattern fileName1Pattern = Pattern.compile("(\\s)([^!()\\,:][\\w-._]+)([^\\s()!:\\]]+)");
            Pattern fileName2Pattern = Pattern.compile("([^!()\\,:][\\w-._]+)([^\\s()!:\\]]+)");
            try {
                 BufferedReader br = new BufferedReader(new FileReader(pluginFile));
                 // read the first line from the text file
                 while ((line = br.readLine()) != null) {
                    Matcher matcher = filePathPattern.matcher(line);
                    if (matcher.find()) {
                        fileNames.add(matcher.group());
                    } else {
                        Matcher matcher1 = fileName1Pattern.matcher(line);
                        if (matcher1.find()) {
                           fileNames.add(matcher1.group());
                        } else {
                           Matcher matcher2 = fileName2Pattern.matcher(line);
                           if (matcher2.find()) {
                               fileNames.add(matcher2.group());
                           }
                        }
                    }                    
                 }
                 br.close();
            } catch (IOException ex) {
                // @@@ NEed to log or rethrow
                Exceptions.printStackTrace(ex);
            } 
     
            return fileNames;
    }

    void cancel() {
        isCancelled = true;
    }
}
