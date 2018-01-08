/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.livetriage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.InvalidPathException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import java.awt.Frame;
import javax.swing.SwingWorker;
import org.apache.commons.io.FileUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.livetriage.CreateLiveTriageDriveAction")
@ActionReference(path = "Menu/Tools", position = 1401)
@ActionRegistration(displayName = "#CTL_CreateLiveTriageDriveAction", lazy = false)
@NbBundle.Messages({"CTL_CreateLiveTriageDriveAction=Make Live Triage Drive"})
public final class CreateLiveTriageDriveAction extends CallableSystemAction {
    
    private static final String DISPLAY_NAME = Bundle.CTL_CreateLiveTriageDriveAction();
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @NbBundle.Messages({"CreateLiveTriageDriveAction.error.title=Error creating live triage disk",
        "CreateLiveTriageDriveAction.exenotfound.message=Executable could not be found",
        "CreateLiveTriageDriveAction.batchFileError.message=Error creating batch file",
        "CreateLiveTriageDriveAction.appPathError.message=Could not location application directory",
        "CreateLiveTriageDriveAction.copyError.message=Could not copy application",
        "CreateLiveTriageDriveAction.success.title=Success",
        "CreateLiveTriageDriveAction.success.message=Live triage drive created. Use RunFromUSB.bat to run the application"
    })
    @Override
    @SuppressWarnings("fallthrough")
    public void performAction() {
        
        Frame mainWindow = WindowManager.getDefault().getMainWindow();
        
        // If this is an installed version, there should be an <appName>64.exe file in the bin folder
        String appName = UserPreferences.getAppName();
        String exeName = appName + "64.exe";
        String installPath = PlatformUtil.getInstallPath();
        
        Path exePath = Paths.get(installPath, "bin", exeName);
        System.out.println("Exe expected at " + exePath);
        
        // TEMP for testing - allows this to run from Netbeans
        exePath = Paths.get("C:\\Program Files\\Autopsy-4.5.0", "bin", exeName);
        
        if(! exePath.toFile().exists()) {
            JOptionPane.showMessageDialog(mainWindow,
                                Bundle.CreateLiveTriageDriveAction_exenotfound_message(),
                                Bundle.CreateLiveTriageDriveAction_error_title(),
                                JOptionPane.ERROR_MESSAGE);
            return;
        } 
        
        Path applicationBasePath;
        try {
            applicationBasePath = exePath.getParent().getParent();
        } catch (InvalidPathException ex){
            JOptionPane.showMessageDialog(mainWindow,
                                Bundle.CreateLiveTriageDriveAction_appPathError_message(),
                                Bundle.CreateLiveTriageDriveAction_error_title(),
                                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        SelectDriveDialog driveDialog = new SelectDriveDialog(mainWindow, true);
        driveDialog.display();
        
        if(! driveDialog.getSelectedDrive().isEmpty()) {
            String drivePath = driveDialog.getSelectedDrive();
            if(drivePath.startsWith("\\\\.\\")){
                drivePath = drivePath.substring(4);
            }
            System.out.println("Destination path: " + drivePath);

            CopyFilesWorker worker = new CopyFilesWorker(applicationBasePath, drivePath, appName);
            worker.execute();
        }
        
    }
    
    private class CopyFilesWorker extends SwingWorker<Void, Void> {

        private final Path sourceFolder;
        private final String drivePath;
        private final String appName;
        private ProgressHandle progressHandle = null;
        
        CopyFilesWorker(Path sourceFolder, String drivePath, String appName){
            this.sourceFolder = sourceFolder;
            this.drivePath = drivePath;
            this.appName = appName;
        }
        
        @NbBundle.Messages({"# {0} - drivePath",
            "CopyFilesWorker.progressBar.text=Live Triage: Copying files to {0}"})
        @Override
        protected Void doInBackground() throws Exception {
            // Setup progress bar.
            String displayName = NbBundle.getMessage(this.getClass(),
                                        "CopyFilesWorker.progressBar.text",
                                        drivePath);
            
            // There's no way to stop FileUtils.copyDirectory, so don't include cancellation
            progressHandle = ProgressHandle.createHandle(displayName);
            progressHandle.start();
            progressHandle.switchToIndeterminate();
            
            copyBatchFile(drivePath, appName);
            copyApplication(sourceFolder, drivePath, appName);
           
            return null;
        }
        
        @NbBundle.Messages({"CopyFilesWorker.title=Create Live Triage Drive",
                            "CopyFilesWorker.error.text=Error copying live triage files",
                            "CopyFilesWorker.done.text=Finished creating live triage disk"})
        @Override
        protected void done() {
            try {
                super.get();
                
                MessageNotifyUtil.Notify.info(NbBundle.getMessage(CopyFilesWorker.class, "CopyFilesWorker.title"),
                    NbBundle.getMessage(CopyFilesWorker.class, "CopyFilesWorker.done.text"));
                
            } catch (Exception ex) {
                Logger.getLogger(CreateLiveTriageDriveAction.class.getName()).log(Level.SEVERE, "Fatal error during live triage drive creation", ex); //NON-NLS
                MessageNotifyUtil.Notify.info(NbBundle.getMessage(CopyFilesWorker.class, "CopyFilesWorker.title"),
                    NbBundle.getMessage(CopyFilesWorker.class, "CopyFilesWorker.error.text"));
            } finally {
                if(progressHandle != null){
                    progressHandle.finish();
                }
            }            
        }
    }
    
    private void copyApplication(Path sourceFolder, String destBaseFolder, String appName) throws IOException {
        
        // Create an appName folder in the destination 
        Path destAppFolder = Paths.get(destBaseFolder, appName);
        if(! destAppFolder.toFile().exists()) {
            if(! destAppFolder.toFile().mkdirs()){
                throw new IOException("Failed to create directory " + destAppFolder.toString());
            }
        }
        
        // Now copy the files
        FileUtils.copyDirectory(sourceFolder.toFile(), destAppFolder.toFile());
    }
    
    private void copyBatchFile(String destPath, String appName) throws IOException, InvalidPathException {
        Path batchFilePath = Paths.get(destPath, "RunFromUSB.bat");
        FileUtils.writeStringToFile(batchFilePath.toFile(), getBatchFileContents(appName), "UTF-8");
        
    }
    
    private String getBatchFileContents(String appName){        
        
        String batchFile = 
            "@echo off\n" + 
            "SET appName=\"" + appName + "\"\n" + 
            "\n" + 
            "REM Create the configData directory. Exit if it does not exist after attempting to create it\n" + 
            "if not exist configData mkdir configData\n" + 
            "if not exist configData (\n" + 
            "        echo Error creating directory configData\n" + 
            "        goto end\n" + 
            ")\n" + 
            "\n" + 
            "REM Create the userdir sub directory. Exit if it does not exist after attempting to create it\n" + 
            "if not exist configData\\userdir mkdir configData\\userdir\n" + 
            "if not exist configData\\userdir (\n" + 
            "        echo Error creating directory configData\\userdir\n" + 
            "        goto end\n" + 
            ")\n" + 
            "\n" + 
            "REM Create the cachedir sub directory. Exit if it does not exist after attempting to create it\n" + 
            "REM If it exists to start with, delete it to clear out old data\n" + 
            "if exist configData\\cachedir rd /s /q configData\\cachedir\n" + 
            "mkdir configData\\cachedir\n" + 
            "if not exist configData\\cachedir (\n" + 
            "        echo Error creating directory configData\\cachedir\n" + 
            "        goto end\n" + 
            ")\n" + 
            "\n" + 
            "REM Create the temp sub directory. Exit if it does not exist after attempting to create it\n" + 
            "REM If it exists to start with, delete it to clear out old data\n" + 
            "if exist configData\\temp rd /s /q configData\\temp\n" + 
            "mkdir configData\\temp\n" + 
            "if not exist configData\\temp (\n" + 
            "        echo Error creating directory configData\\temp\n" + 
            "        goto end\n" + 
            ")\n" + 
            "\n" + 
            "REM Create the cases directory. It's ok if this fails.\n" + 
            "if not exist cases mkdir cases\n" + 
            "\n" + 
            "if exist %appName% (\n" + 
            "        if not exist %appName%\\bin\\%appName%64.exe (\n" + 
            "                echo %appName%\\bin\\%appName%64.exe does not exist\n" + 
            "                goto end\n" + 
            "        )\n" + 
            "        %appName%\\bin\\%appName%64.exe --userdir ..\\configData\\userdir --cachedir ..\\configData\\cachedir -J-Djava.io.tmpdir=..\\configData\\temp\n" + 
            ") else (\n" + 
            "        echo Could not find %appName% directory\n" +   
            "        goto end\n" + 
            ")\n" + 
            "\n" + 
            ":end\n" +
            "\n" + 
            "REM Keep the cmd window open in case there was an error\n" + 
            "@pause\n";
        return batchFile;      
    }
    
    @Override
    public String getName() {
        return DISPLAY_NAME;
    }
    
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}
