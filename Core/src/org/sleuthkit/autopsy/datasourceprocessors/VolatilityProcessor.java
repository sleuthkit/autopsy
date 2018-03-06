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
import java.util.HashSet;
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
        moduleOutputPath = currentCase.getModulesOutputDirAbsPath() + File.separator + "Volatility" + File.separator + "1";  // @@@ TESTING ONLY
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
            
            // @@@ TESTING ONLY
            //int exitVal = ExecUtil.execute(processBuilder);
            //if (exitVal != 0) {
            //    logger.log(Level.SEVERE, "Volatility non-0 exit value for module: " + pluginToRun);
            //    return;
            //}
            
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
    
    private void lookupFiles(Set<String> fileSet, String pluginName) {
        
        Blackboard blackboard;
        try {
            blackboard = Case.getCurrentCase().getServices().getBlackboard();
        }
        catch (Exception ex) {
            // case is closed ?? 
            return;
        }

        for (String file : fileSet) {
            if (isCancelled) {
               return;
            }

            File volfile = new File(file);
            String fileName = volfile.getName().trim();
            // if there is no extension, add a wildcard to the end
            if (fileName.contains(".") == false) {
                // if there is already the same entry with ".exe" in the set, just use that one
                if (fileSet.contains(file + ".exe"))
                    continue;
                fileName = fileName + ".%";
            }

            String filePath = volfile.getParent();
            
            try {
                List<AbstractFile> resolvedFiles;
                if (filePath == null) {
                    resolvedFiles = fileManager.findFiles(fileName); //NON-NLS
                } else {
                    resolvedFiles = fileManager.findFiles(fileName, filePath); //NON-NLS
                }
                resolvedFiles.forEach((resolvedFile) -> {
                    try {
                        String MODULE_NAME = "VOLATILITY";
                        BlackboardArtifact volArtifact = resolvedFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
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
            } catch (TskCoreException ex) {
                //String msg = NbBundle.getMessage(this.getClass(), "Chrome.getHistory.errMsg.errGettingFiles");
                logger.log(Level.SEVERE, "Error in Finding FIles", ex);
                return;
            }
        }
    }
    
    private void scanOutputFile(String pluginName, File PluginOutput) {
           
        try {
            if (pluginName.matches("dlllist")) {
               Set<String> fileSet = parse_DllList(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("handles")) {
               Set<String> fileSet = Parse_Handles(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("cmdline")) { 
               Set<String> fileSet = parse_Cmdline(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("psxview")){
               Set<String> fileSet = Parse_Psxview(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("pslist")) {
               Set<String> fileSet = Parse_Pslist(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("psscan")) { 
                Set<String> fileSet = Parse_Psscan(PluginOutput);
                lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("pstree")) {
               Set<String> fileSet = Parse_Pstree(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("svcscan")) {
               Set<String> fileSet = Parse_Svcscan(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else if (pluginName.matches("filescan")) {
               Set<String> fileSet = Parse_Filescan(PluginOutput);
               lookupFiles(fileSet, pluginName);
            } else  {  
               Set<String> fileSet = Parse_Shimcache(PluginOutput);
               lookupFiles(fileSet, pluginName);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to parse files " + PluginOutput, ex); //NON-NLS
            //this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getName()));
        }
    } 

    private String normalizePath(String filePath) {
        if (filePath == null)
            return "";
        
        // strip C: and \??\C:
        if (filePath.contains(":")) {
            filePath = filePath.substring(filePath.indexOf(":") + 1);
        }
        
        // change slash direction
        filePath = filePath.replaceAll("\\\\", "/");
        filePath = filePath.toLowerCase();
        filePath = filePath.replaceAll("/systemroot/", "/windows/");

        return filePath;
    }
    
    private Set<String> Parse_Handles(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                 String TAG = " File ";
                 String file_path = null;
                 if (line.contains(TAG)) {
                    file_path = line.substring(82);
                    file_path = file_path.replaceAll("Device\\\\","");
                    file_path = file_path.replaceAll("HarddiskVolume[0-9]\\\\", "");
                    if (file_path.contains("\"")) {
                         file_path = file_path.substring(0, file_path.indexOf("\""));
                    }
                    else {
                       // ERROR
                    }
                    fileSet.add(file_path.toLowerCase());
                 }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;
    }
    
    private Set<String> parse_DllList(File PluginFile) {
        Set<String> fileSet = new HashSet<>();
        int counter = 0;
        // read the first line from the text file
        try (BufferedReader br = new BufferedReader(new FileReader(PluginFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                    
                String TAG = "Command line : ";
                if (line.startsWith(TAG)) {
                    counter = counter + 1;
                    String file_path;

                    // Command line : "C:\Program Files\VMware\VMware Tools\vmacthlp.exe"
                    // grab whats inbetween the quotes
                    if (line.charAt(TAG.length()) == '\"') {
                        file_path = line.substring(TAG.length() + 1);
                        if (file_path.contains("\"")) {
                            file_path = file_path.substring(0, file_path.indexOf("\""));
                        } else {
                            // ERROR
                        }
                    } 
                    // Command line : C:\WINDOWS\system32\csrss.exe ObjectDirectory=\Windows SharedSection=1024,3072,512
                    // grab everything before the next space - we don't want arguments
                    else {
                        file_path = line.substring(TAG.length());
                        if (file_path.contains(" ")) {
                            file_path = file_path.substring(0, file_path.indexOf(" "));
                        }
                    }
                    fileSet.add(normalizePath(file_path));
                }
                // 0x4a680000     0x5000     0xffff \??\C:\WINDOWS\system32\csrss.exe
                // 0x7c900000    0xb2000     0xffff C:\WINDOWS\system32\ntdll.dll
                else if (line.startsWith("0x") && line.length() > 33) {
                    counter = counter + 1;
                    // These lines do not have arguments
                    String file_path = line.substring(33);
                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Error opening DllList output", ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error parsing DllList output", ex);
        } 
        return fileSet;     
    }
    
   private Set<String> Parse_Filescan(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                try {
                    String file_path;
                    file_path = line.substring(41);
                    file_path = file_path.replaceAll("Device\\\\","");
                    file_path = file_path.replaceAll("HarddiskVolume[0-9]\\\\", "");
                    fileSet.add(file_path.toLowerCase());
                } catch (StringIndexOutOfBoundsException ex) {
                  // TO DO  Catch exception
                }
            }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;     
    }
    
    private Set<String> parse_Cmdline(File PluginFile) {
        Set<String> fileSet = new HashSet<>();
        // read the first line from the text file
        try (BufferedReader br = new BufferedReader(new FileReader(PluginFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() > 16) {
                    String TAG = "Command line : ";
                    if (line.startsWith(TAG)) {
                        String file_path;

                        // Command line : "C:\Program Files\VMware\VMware Tools\vmacthlp.exe"
                        // grab whats inbetween the quotes
                        if (line.charAt(TAG.length()) == '\"') {
                            file_path = line.substring(TAG.length() + 1);
                            if (file_path.contains("\"")) {
                                file_path = file_path.substring(0, file_path.indexOf("\""));
                            } else {
                                // ERROR
                            }
                        } 
                        // Command line : C:\WINDOWS\system32\csrss.exe ObjectDirectory=\Windows SharedSection=1024,3072,512
                        // grab everything before the next space - we don't want arguments
                        else {
                            file_path = line.substring(TAG.length());
                            if (file_path.contains(" ")) {
                                file_path = file_path.substring(0, file_path.indexOf(" "));
                            }
                        }
                        fileSet.add(normalizePath(file_path));
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Error opening cmdline output", ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error parsing cmdline output", ex);
        } 
        return fileSet;     
    }
    
    private Set<String> Parse_Shimcache(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                String file_path;
                if (line.length() > 36) {
                    file_path = line.substring(38);
                    if (file_path.contains("\"")) {
                        file_path = file_path.substring(0, file_path.indexOf("\""));
                    }
                    else {
                        // ERROR
                    }
                    fileSet.add(file_path.toLowerCase());
                     } 
             }
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;     
    }
    
    private Set<String> Parse_Psscan(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                String file_path;
                file_path = line.substring(19, 37);
                if (!file_path.startsWith("System")) {
                   fileSet.add(file_path.toLowerCase());
                }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;     
    }

    private Set<String> Parse_Pslist(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                String file_path;
                file_path = line.substring(19, 41);
                if (!file_path.startsWith("System")) {
                   fileSet.add(file_path.toLowerCase());
                }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;     
    }

    private Set<String> Parse_Psxview(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                String file_path;
                file_path = line.substring(19, 41);
                if (!file_path.startsWith("System")) {
                   fileSet.add(file_path.toLowerCase());
                }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;     
    }

    private Set<String> Parse_Pstree(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                String file_path;
                String TAG = ":";
                if (line.contains(TAG)) {
                    file_path = line.substring(line.indexOf(":") + 1, 52);
                    if (!file_path.startsWith("System")) {
                       fileSet.add(file_path.toLowerCase());
                    }
                }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;     
    }

    private Set<String> Parse_Svcscan(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                String file_path;
                String TAG = "Binary Path: ";
                if (line.startsWith(TAG)) {
                    file_path = line.substring(13);
                    if (line.charAt(TAG.length()) == '\"') {
                        file_path = line.substring(TAG.length()+1);
                        if (file_path.contains("\"")) {
                            file_path = file_path.substring(0, file_path.indexOf("\""));
                        }
                        else {
                            // ERROR
                        }
                    }
                    // Command line : C:\Windows\System32\svchost.exe -k LocalSystemNetworkRestricted
                    else {
                        file_path = line.substring(TAG.length());
                        if (file_path.contains(" ")) {
                            file_path = file_path.substring(0, file_path.indexOf(" "));
                        }
                    }
                    fileSet.add(file_path.toLowerCase());
                }
             }    
             br.close();
        } catch (IOException ex) { 
            //Exceptions.printStackTrace(ex);
        } 
        return fileSet;     
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
