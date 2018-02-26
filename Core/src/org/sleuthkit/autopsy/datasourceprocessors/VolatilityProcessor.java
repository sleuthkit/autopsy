/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import java.util.List;
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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

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
 * @author mark
 */
public class VolatilityProcessor implements Runnable{
    private static final String VOLATILITY_DIRECTORY = "Volatility"; //NON-NLS
    private static final String VOLATILITY_EXECUTABLE = "volatility_2.6_win64_standalone.exe"; //NON-NLS
    private static final String TEMP_DIR_NAME = "temp"; // NON-NLS
    private final String MemoryImage;
    private final List<String> PluginsToRun;
    private final String deviceId;
   // private final Content dataSource;
    //private final DataSourceProcessorProgressMonitor progressMonitor;
    private static final String SEP = System.getProperty("line.separator");
    private static final Logger logger = Logger.getLogger(VolatilityProcessor.class.getName());
    private static Object Bundle;
    private String moduleOutputPath;
    private File executableFile;
    private final Boolean isFile = true;
    private final IngestServices services = IngestServices.getInstance();

    public VolatilityProcessor(String ImagePath, List<String> PlugInToRuns, String deviceId) {
//    public VolatilityProcessor(String ImagePath, List<String> PlugInToRuns, String deviceId, DataSourceProcessorProgressMonitor progressMonitor) {
//    public VolatilityProcessor(String ImagePath) {
        this.MemoryImage = ImagePath;
        this.PluginsToRun = PlugInToRuns;
        this.deviceId = deviceId;
//        this.dataSource = dataSource;
        //this.progressMonitor = progressMonitor;
    }
    
    @Override
    public void run() {
        
        Path execName = Paths.get(VOLATILITY_DIRECTORY, VOLATILITY_EXECUTABLE);
        executableFile = locateExecutable(execName.toString());
        final Case currentCase = Case.getCurrentCase();
        final FileManager fileManager = currentCase.getServices().getFileManager();

        moduleOutputPath = currentCase.getModulesOutputDirAbsPath() + File.separator + "Volatility";
        
        File directory = new File(String.valueOf(moduleOutputPath));
        if(!directory.exists()){
             directory.mkdir();
             executeVolatility(executableFile, MemoryImage, "", "imageinfo", "", fileManager);
        }

        PluginsToRun.forEach((pluginToRun) -> {
            executeVolatility(executableFile, MemoryImage, "", pluginToRun, "", fileManager);
        }); 
    }

    private void executeVolatility(File VolatilityPath, String MemoryImage, String OutputPath, String PluginToRun, String MemoryProfile, FileManager fileManager) {
        try {
                       
            List<String> commandLine = new ArrayList<>();
            commandLine.add("\"" + VolatilityPath + "\"");
            File memoryImage = new File(MemoryImage);
            commandLine.add("--filename=" + memoryImage.getName()); //NON-NLS
            File memoryProfile = new File(moduleOutputPath + "\\imageinfo.txt");
            if (memoryProfile.exists()) {
               MemoryProfile = parseProfile(memoryProfile);
               commandLine.add("--profile=" + MemoryProfile);
            }
            commandLine.add(PluginToRun); //NON-NLS
          
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            // Add environment variable to force Volatility to run with the same permissions Autopsy uses
            processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
            processBuilder.redirectOutput(new File(moduleOutputPath + "\\" + PluginToRun + ".txt"));
            processBuilder.redirectError(new File(moduleOutputPath + "\\Volatility_Run.err"));
            processBuilder.directory(new File(memoryImage.getParent()));
            
            int exitVal = ExecUtil.execute(processBuilder);
//            int exitVal = 0;
            if (exitVal == 0) {
               ScanOutputFile(fileManager, PluginToRun, new File(moduleOutputPath + "\\" + PluginToRun + ".txt"));    
            } else {
            logger.log(Level.INFO, "Exit Value is ", exitVal);
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
     * @return A File reference or throws an exception
     *
     * @throws IngestModuleException
     */
//    public static File locateExecutable(String executableToFindName) throws IngestModule.IngestModuleException {
    public static File locateExecutable(String executableToFindName) {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS()) {
           // throw new IngestModule.IngestModuleException(Bundle.unsupportedOS_message());
        }

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, VolatilityProcessor.class.getPackage().getName(), false);
        if (null == exeFile) {
            //throw new IngestModule.IngestModuleException(Bundle.missingExecutable_message());
        }

        if (!exeFile.canExecute()) {
            //throw new IngestModule.IngestModuleException(Bundle.cannotRunExecutable_message());
        }

        return exeFile;
    }

    private String parseProfile(File memoryProfile) throws FileNotFoundException {
            // create a Buffered Reader object instance with a FileReader
            try (
                 BufferedReader br = new BufferedReader(new FileReader(memoryProfile))) {
                 // read the first line from the text file
                 String fileRead = br.readLine();
                 br.close();
                 String[] profileLine = fileRead.split(":");
                 String[] memProfile = profileLine[1].split(",|\\(");
                 return memProfile[0].replaceAll("\\s+","");
                } catch (IOException ex) { 
            Exceptions.printStackTrace(ex);
        } 
     
        return null;
    }
    
    private void ScanOutputFile(FileManager fileManager, String pluginName, File PluginOutput) {
        List<String> fileNames = new ArrayList<>();
        
        Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard();
         
        try {
            fileNames = parsePluginOutput(PluginOutput);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to run RegRipper", ex); //NON-NLS
            //this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getName()));
        }
        try {
            fileNames.forEach((String fileName) -> {
                List<AbstractFile> volFiles = new ArrayList<>();
                File volfile = new File(fileName);
                String filename = volfile.getName();
                String path = volfile.getParent();
                //Path path = Paths.get("/", fileName).normalize();
                //String path = fileName.substring(0, fileName.lastIndexOf("\\")+1);
//                String filename = fileName.substring(fileName.lastIndexOf("\\")+1);
                if (path != null && !path.isEmpty()) {
//                if ("".equals(path)) {
                    path = path.replaceAll("\\\\", "%");
                    path = path + "%";
//                    path = "%";
                } else {
//                  path = path.replaceAll("\\\\", "%");
//                  path = path + "%";
                    path = "%";
                  //  path = path.substring(0, path.length()-1);
                }                    
                try {
                    volFiles = fileManager.findFiles(filename, path); //NON-NLS
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
            });
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error in processing List of FIles", ex); //NON-NLS   
        }
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
                Exceptions.printStackTrace(ex);
            } 
     
            return fileNames;
    }
    
}
