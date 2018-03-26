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
package org.sleuthkit.autopsy.experimental.volatilityDSP;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;


/**
 * Runs Volatility and parses output
 */
class VolatilityProcessor {
    private static final String VOLATILITY_DIRECTORY = "Volatility"; //NON-NLS
    private static final String VOLATILITY_EXECUTABLE = "volatility_2.6_win64_standalone.exe"; //NON-NLS
    private final String memoryImagePath;
    private final List<String> pluginsToRun;
    private final Image dataSource;
    private static final String SEP = System.getProperty("line.separator");
    private static final Logger logger = Logger.getLogger(VolatilityProcessor.class.getName());
    private String moduleOutputPath;
    private File executableFile;
    private final IngestServices services = IngestServices.getInstance();
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private boolean isCancelled;
    private FileManager fileManager;
    private final List <String> errorMsgs = new ArrayList<>();

    /**
     * 
     * @param ImagePath String path to memory image file
     * @param dataSource Object for memory image that was added to case DB
     * @param plugInToRuns list of Volatility plugins to run
     * @param progressMonitor DSP progress monitor to report status
     */
    VolatilityProcessor(String ImagePath, Image dataSource, List<String> plugInToRun, DataSourceProcessorProgressMonitor progressMonitor) {
        this.memoryImagePath = ImagePath;
        this.pluginsToRun = plugInToRun;
        this.dataSource = dataSource;
        this.progressMonitor = progressMonitor;
    }
    
    
    /**
     * Run volatility and parse the outputs
     * @returns true if there was a critical error
     */
    boolean run() {  
        executableFile = locateExecutable();
        if (executableFile == null) {
            logger.log(Level.SEVERE, "Volatility exe not found");
            return true;
        }
        
        final Case currentCase = Case.getCurrentCase();
        fileManager = currentCase.getServices().getFileManager();

        // make a unique folder for this image
        moduleOutputPath = currentCase.getModulesOutputDirAbsPath() + File.separator + "Volatility" + File.separator + dataSource.getId();        File directory = new File(String.valueOf(moduleOutputPath));
        if(!directory.exists()){
            directory.mkdirs();
            progressMonitor.setProgressText("Running imageinfo");
            executeAndParseVolatility("imageinfo");
        }

        progressMonitor.setIndeterminate(false);
        progressMonitor.setProgressMax(pluginsToRun.size());
        for (int i = 0; i < pluginsToRun.size(); i++) {
            if (isCancelled)
                break;
            String pluginToRun = pluginsToRun.get(i);
            progressMonitor.setProgressText("Processing " + pluginToRun + " module");
            executeAndParseVolatility(pluginToRun);
            progressMonitor.setProgress(i);
        } 
        return false;
    }
    
    /**
     * Get list of error messages that were generated during call to run()
     * @return 
     */
    List<String> getErrorMessages() {
        return errorMsgs;
    }

    private void executeAndParseVolatility(String pluginToRun) {
        try {        
            List<String> commandLine = new ArrayList<>();
            commandLine.add("\"" + executableFile + "\"");
            File memoryImage = new File(memoryImagePath);
            commandLine.add("--filename=" + memoryImage.getName()); //NON-NLS
            
            // run imginfo if we haven't run it yet
            File imageInfoOutputFile = new File(moduleOutputPath + "\\imageinfo.txt");
            if (imageInfoOutputFile.exists()) {
               String memoryProfile = parseImageInfoOutput(imageInfoOutputFile);
               if (memoryProfile == null) {
                    String msg = "Error parsing Volatility imginfo output";
                    logger.log(Level.SEVERE, msg);
                    errorMsgs.add(msg);
                    return;
               }
               commandLine.add("--profile=" + memoryProfile);
            }
            
            commandLine.add(pluginToRun); //NON-NLS
          
            String outputFile = moduleOutputPath + "\\" + pluginToRun + ".txt";
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            // Add environment variable to force Volatility to run with the same permissions Autopsy uses
            processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
            processBuilder.redirectOutput(new File(outputFile));
            processBuilder.redirectError(new File(moduleOutputPath + "\\Volatility_Run.err"));
            processBuilder.directory(new File(memoryImage.getParent()));
            
            int exitVal = ExecUtil.execute(processBuilder);
            if (exitVal != 0) {
                String msg = "Volatility non-0 exit value for module: " + pluginToRun;
                logger.log(Level.SEVERE, msg);
                errorMsgs.add(msg);
                return;
            }
            
            if (isCancelled)
                return;
            
            // add the output to the case
            final Case currentCase = Case.getCurrentCase();
            Report report = currentCase.getSleuthkitCase().addReport(outputFile, "Volatility", "Volatility " + pluginToRun + " Module");
            
            KeywordSearchService searchService = Lookup.getDefault().lookup(KeywordSearchService.class);
            if (null == searchService) {
                logger.log(Level.WARNING, "Keyword search service not found. Report will not be indexed");
            } else {
                searchService.index(report);
            }
            
            scanOutputFile(pluginToRun, new File(outputFile));  
                        
        } catch (IOException | SecurityException | TskCoreException ex) {
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
    private static File locateExecutable() {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS()) {
           return null;
        }
        
        String executableToFindName = Paths.get(VOLATILITY_DIRECTORY, VOLATILITY_EXECUTABLE).toString();

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
    
    /**
     * Lookup the set of files and add INTERESTING_ITEM artifacts for them.
     * 
     * @param fileSet
     * @param pluginName 
     */
    private void flagFiles(Set<String> fileSet, String pluginName) {
        
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
            // File does not have any data in it based on bad data
            if (fileName.length() < 1) {
                continue;
            }

            String filePath = volfile.getParent();
        
            try {
                List<AbstractFile> resolvedFiles;
                if (filePath == null) {
                    resolvedFiles = fileManager.findFiles(fileName); //NON-NLS
                } else {
                    // File changed the slashes back to \ on us...
                    filePath = filePath.replaceAll("\\\\", "/");
                    resolvedFiles = fileManager.findFiles(fileName, filePath); //NON-NLS
                }
                
                // if we didn't get anything, then try adding a wildcard for extension
                if ((resolvedFiles.isEmpty()) && (fileName.contains(".") == false)) {
                    
                    // if there is already the same entry with ".exe" in the set, just use that one
                    if (fileSet.contains(file + ".exe"))
                        continue;
                    
                    fileName = fileName + ".%";
                    if (filePath == null) {
                        resolvedFiles = fileManager.findFiles(fileName); //NON-NLS
                    } else {
                        resolvedFiles = fileManager.findFiles(fileName, filePath); //NON-NLS
                    }

                }
                
                if (resolvedFiles.isEmpty()) {
                    logger.log(Level.SEVERE, "File not found in lookup: " + filePath + "/" + fileName);
                    errorMsgs.add("File not found in lookup: " + filePath + "/" + fileName);
                    continue;
                }
                
                resolvedFiles.forEach((resolvedFile) -> {
                    if (resolvedFile.getType() == TSK_DB_FILES_TYPE_ENUM.SLACK) {
                        return; // equivalent to continue in non-lambda world
                    }
                    try {
                        String MODULE_NAME = "Volatility";
                        BlackboardArtifact volArtifact = resolvedFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                        BlackboardAttribute att1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME,
                                "Volatility Plugin " + pluginName);
                        volArtifact.addAttribute(att1);

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
                logger.log(Level.SEVERE, "Error in Finding Files", ex);
                return;
            }
        }
    }
    
    /**
     * Scan the output of Volatility and create artifacts as needed
     * 
     * @param pluginName Name of volatility module run
     * @param PluginOutput File that contains the output to parse
     */
    private void scanOutputFile(String pluginName, File PluginOutput) {
           
        if (pluginName.matches("dlllist")) {
           Set<String> fileSet = parseDllList(PluginOutput);
           flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("handles")) {
           Set<String> fileSet = parseHandles(PluginOutput);
           flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("cmdline")) { 
           Set<String> fileSet = parseCmdline(PluginOutput);
           flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("psxview")){
           Set<String> fileSet = parsePsxview(PluginOutput);
           flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("pslist")) {
           Set<String> fileSet = parsePslist(PluginOutput);
           flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("psscan")) { 
            Set<String> fileSet = parsePsscan(PluginOutput);
            flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("pstree")) {
           Set<String> fileSet = parsePstree(PluginOutput);
           flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("svcscan")) {
           Set<String> fileSet = parseSvcscan(PluginOutput);
           flagFiles(fileSet, pluginName);
        } else if (pluginName.matches("filescan")) {
            // BC: Commented out.  Too many hits to flag
           //Set<String> fileSet = ParseFilescan(PluginOutput);
           //lookupFiles(fileSet, pluginName);  
        } else if (pluginName.matches("shimcache")) { 
           Set<String> fileSet = parseShimcache(PluginOutput);
           flagFiles(fileSet, pluginName);
        }        
    } 

    /** 
     * Normalize the path we parse out of the output before
     * we look it up in the case DB
     * 
     * @param filePath Path to normalize
     * @return Normalized version
     */
    private String normalizePath(String filePath) {
        if (filePath == null)
            return "";
        
        filePath = filePath.trim();
        
        // strip C: and \??\C:
        if (filePath.contains(":")) {
            filePath = filePath.substring(filePath.indexOf(":") + 1);
        }
        
        // change slash direction
        filePath = filePath.replaceAll("\\\\", "/");
        filePath = filePath.toLowerCase();
        
        filePath = filePath.replaceAll("/systemroot/", "/windows/");
        // catches 1 type of file in cmdline
        filePath = filePath.replaceAll("%systemroot%", "/windows/");
        filePath = filePath.replaceAll("/device/","");
        // helps with finding files in handles plugin
        // example: \Device\clfs\Device\HarddiskVolume2\Users\joe\AppData\Local\Microsoft\Windows\UsrClass.dat{e15d4b01-1598-11e8-93e6-080027b5e733}.TM
        if (filePath.contains("/harddiskvolume")) {
            // 16 advances beyond harddiskvolume and the number
            filePath = filePath.substring(filePath.indexOf("/harddiskvolume") + 16);
        }
        
        // no point returning these. We won't map to them
        if (filePath.startsWith("/namedpipe/"))
            return "";

        return filePath;
    }
    
    private Set<String> parseHandles(File pluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(pluginFile));
             // Ignore the first two header lines
             br.readLine();
             br.readLine();
             while ((line = br.readLine()) != null) {
                 // 0x89ab7878      4      0x718  0x2000003 File             \Device\HarddiskVolume1\Documents and Settings\QA\Local Settings\Application 
                 if (line.startsWith("0x") == false)
                     continue;
                 
                 String TAG = " File ";
                 String file_path = null;
                 if ((line.contains(TAG)) && (line.length() > 57)) {
                    file_path = line.substring(57);
                    if (file_path.contains("\"")) {
                         file_path = file_path.substring(0, file_path.indexOf("\""));
                    }
                    // this file has a lot of device entries that are not files
                    if (file_path.startsWith("\\Device\\")) {
                        if (file_path.contains("HardDiskVolume") == false) 
                            continue;
                    }
                            
                    fileSet.add(normalizePath(file_path));
                 }
             }    
             br.close();
        } catch (IOException ex) {
            String msg = "Error parsing handles output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;
    }
    
    private Set<String> parseDllList(File pluginFile) {
        Set<String> fileSet = new HashSet<>();
        // read the first line from the text file
        try (BufferedReader br = new BufferedReader(new FileReader(pluginFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                    
                String TAG = "Command line : ";
                if (line.startsWith(TAG)) {
                    String file_path;

                    // Command line : "C:\Program Files\VMware\VMware Tools\vmacthlp.exe"
                    // grab whats inbetween the quotes
                    if (line.charAt(TAG.length()) == '\"') {
                        file_path = line.substring(TAG.length() + 1);
                        if (file_path.contains("\"")) {
                            file_path = file_path.substring(0, file_path.indexOf("\""));
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
                    // These lines do not have arguments
                    String file_path = line.substring(33);
                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (IOException ex) {
            String msg = "Error parsing dlllist output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }
    
   private Set<String> parseFilescan(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                try {
                    String file_path;
                    file_path = line.substring(41);
                    fileSet.add(normalizePath(file_path));
                } catch (StringIndexOutOfBoundsException ex) {
                  // TO DO  Catch exception
                }
            }    
             br.close();
        } catch (IOException ex) { 
            String msg = "Error parsing filescan output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }
    
    private Set<String> parseCmdline(File PluginFile) {
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
        
        } catch (IOException ex) {
            String msg = "Error parsing cmdline output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }
    
    private Set<String> parseShimcache(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // ignore the first 2 header lines
             br.readLine();
             br.readLine();
             while ((line = br.readLine()) != null) {
                String file_path;
                //1970-01-01 00:00:00 UTC+0000   2017-10-25 13:07:30 UTC+0000   C:\WINDOWS\system32\msctfime.ime
                //2017-10-23 20:47:40 UTC+0000   2017-10-23 20:48:02 UTC+0000   \??\C:\WINDOWS\CT_dba9e71b-ad55-4132-a11b-faa946b197d6.exe
                if (line.length() > 62) {
                    file_path = line.substring(62);
                    if (file_path.contains("\"")) {
                        file_path = file_path.substring(0, file_path.indexOf("\""));
                    }                   
                    fileSet.add(normalizePath(file_path));
                } 
             }
             br.close();
        } catch (IOException ex) { 
            String msg = "Error parsing shimcache output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }
    
    private Set<String> parsePsscan(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // ignore the first two header lines
             br.readLine();
             br.readLine();
             while ((line = br.readLine()) != null) {
                // 0x000000000969a020 notepad.exe        3604   3300 0x16d40340 2018-01-12 14:41:16 UTC+0000  
                if (line.startsWith("0x") == false)
                    continue;
                String file_path = line.substring(19, 37);
                file_path = normalizePath(file_path);
               
                // ignore system, it's not really a path
                if (file_path.equals("system"))
                    continue;
                fileSet.add(file_path);
             }    
             br.close();
        } catch (IOException ex) { 
            String msg = "Error parsing psscan output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }

    private Set<String> parsePslist(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                 if (line.startsWith("0x") == false)
                     continue;
                 
                // 0x89cfb998 csrss.exe               704    640     14      532      0      0 2017-12-07 14:05:34 UTC+0000
                String file_path;
                file_path = line.substring(10, 34);
                file_path = normalizePath(file_path);
               
                // ignore system, it's not really a path
                if (file_path.equals("system"))
                    continue;
                fileSet.add(file_path);
             }    
             br.close();
        } catch (IOException ex) { 
            String msg = "Error parsing pslist output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }

    private Set<String> parsePsxview(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // ignore the first two header lines
             br.readLine();
             br.readLine();
             while ((line = br.readLine()) != null) {
                // 0x09adf980 svchost.exe            1368 True   True   False    True   True  True    True
                if (line.startsWith("0x") == false)
                    continue;
                String file_path = line.substring(11, 34);
                file_path = normalizePath(file_path);
               
                // ignore system, it's not really a path
                if (file_path.equals("system"))
                    continue;
                fileSet.add(file_path);
             }    
             br.close();
        } catch (IOException ex) { 
            String msg = "Error parsing psxview output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }

    private Set<String> parsePstree(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                 //  ... 0x897e5020:services.exe                           772    728     15    287 2017-12-07 14:05:35 UTC+000
                String file_path;
                String TAG = ":";
                if (line.contains(TAG)) {
                    file_path = line.substring(line.indexOf(":") + 1, 52);
                    file_path = normalizePath(file_path);
               
                    // ignore system, it's not really a path
                    if (file_path.equals("system"))
                        continue;
                    fileSet.add(file_path);
                }
             }    
             br.close();
        } catch (IOException ex) { 
            String msg = "Error parsing pstree output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }

    private Set<String> parseSvcscan(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
             BufferedReader br = new BufferedReader(new FileReader(PluginFile));
             // read the first line from the text file
             while ((line = br.readLine()) != null) {
                String file_path;
                String TAG = "Binary Path: ";
                if (line.startsWith(TAG)) {
                    if (line.charAt(TAG.length()) == '\"') {
                        file_path = line.substring(TAG.length()+1);
                        if (file_path.contains("\"")) {
                            file_path = file_path.substring(0, file_path.indexOf("\""));
                        }
                    }
                    // Binary Path: -
                    else if (line.charAt(TAG.length()) == '-') {
                        continue;
                    }
                    // Command line : C:\Windows\System32\svchost.exe -k LocalSystemNetworkRestricted
                    else {
                        file_path = line.substring(TAG.length());
                        if (file_path.contains(" ")) {
                            file_path = file_path.substring(0, file_path.indexOf(" "));
                        }
                        // We can't do anything with driver entries
                        if (file_path.startsWith("\\Driver\\")) {
                            continue;
                        }
                        else if (file_path.startsWith("\\FileSystem\\")) {
                            continue;
                        }
                    }
                    fileSet.add(normalizePath(file_path));
                }
             }    
             br.close();
        } catch (IOException ex) { 
            String msg = "Error parsing svcscan output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        } 
        return fileSet;     
    }
    
    void cancel() {
        isCancelled = true;
    }
}
