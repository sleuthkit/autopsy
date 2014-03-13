/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this content except in compliance with the License.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extracts AbstractFiles to a location selected by the user.
 */
public final class ExtractAction extends AbstractAction {
    private Logger logger = Logger.getLogger(ExtractAction.class.getName());

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ExtractAction instance;

    public static synchronized ExtractAction getInstance() {
        if (null == instance) {
            instance = new ExtractAction();
        }
        return instance;
    }

    private ExtractAction() {
        super(NbBundle.getMessage(ExtractAction.class, "ExtractAction.title.extractFiles.text"));
    }
        
    /**
     * Asks user to choose destination, then extracts content to destination
     * (recursing on directories).
     * @param e  The action event.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
        if (selectedFiles.size() > 1) {
            extractFiles(e, selectedFiles);
        }
        else if (selectedFiles.size() == 1) {
            AbstractFile source = selectedFiles.iterator().next();
            if (source.isDir()) {
                extractFiles(e, selectedFiles);                
            }
            else {
                extractFile(e, selectedFiles.iterator().next());
            }
        }
    }
    
    private void extractFile(ActionEvent e, AbstractFile source) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(Case.getCurrentCase().getExportDirectory()));
        fileChooser.setSelectedFile(new File(source.getName()));
        if (fileChooser.showSaveDialog((Component)e.getSource()) == JFileChooser.APPROVE_OPTION) {
            ArrayList<FileExtractionTask> fileExtractionTasks = new ArrayList<>();
            fileExtractionTasks.add(new FileExtractionTask(source, fileChooser.getSelectedFile()));
            doFileExtraction(e, fileExtractionTasks);            
        }        
    }
        
    private void extractFiles(ActionEvent e, Collection<? extends AbstractFile> selectedFiles) {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setCurrentDirectory(new File(Case.getCurrentCase().getExportDirectory()));
        if (folderChooser.showSaveDialog((Component)e.getSource()) == JFileChooser.APPROVE_OPTION) {
            File destinationFolder = folderChooser.getSelectedFile();
            if (!destinationFolder.exists()) {
                try {
                    destinationFolder.mkdirs();
                }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog((Component) e.getSource(), NbBundle.getMessage(this.getClass(),
                                                                                                 "ExtractAction.extractFiles.cantCreateFolderErr.msg"));
                    logger.log(Level.INFO, "Unable to create folder(s) for user " + destinationFolder.getAbsolutePath(), ex);
                    return;
                }
            }

            ArrayList<FileExtractionTask> fileExtractionTasks = new ArrayList<>();
            for (AbstractFile source : selectedFiles) {
                fileExtractionTasks.add(new FileExtractionTask(source, new File(destinationFolder, source.getId() + "-" + source.getName())));
            }            
            doFileExtraction(e, fileExtractionTasks);            
        }
    }
        
    private void doFileExtraction(ActionEvent e, ArrayList<FileExtractionTask> fileExtractionTasks) {
        
        // verify all of the sources and destinations are OK
        for (Iterator<FileExtractionTask> it = fileExtractionTasks.iterator(); it.hasNext(); ) {
            FileExtractionTask task = it.next();
            
            if (ContentUtils.isDotDirectory(task.source)) {
                //JOptionPane.showMessageDialog((Component) e.getSource(), "Cannot extract virtual " + task.source.getName() + " directory.", "File is Virtual Directory", JOptionPane.WARNING_MESSAGE);
                it.remove();
                continue;
            }
            
            /*
             * @@@ Problems with this code:
             * - does not prevent us from having multiple files with the same target name in the task list (in which case, the first ones are overwritten)
             * Unique Id was added to set of names before calling this method to deal with that.
             */
            if (task.destination.exists()) {
                if (JOptionPane.showConfirmDialog((Component) e.getSource(),
                                                  NbBundle.getMessage(this.getClass(), "ExtractAction.confDlg.destFileExist.msg", task.destination.getAbsolutePath()),
                                                  NbBundle.getMessage(this.getClass(), "ExtractAction.confDlg.destFileExist.title"),
                                                  JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    if (!FileUtil.deleteFileDir(task.destination)) {
                        JOptionPane.showMessageDialog((Component) e.getSource(),
                                                      NbBundle.getMessage(this.getClass(), "ExtractAction.msgDlg.cantOverwriteFile.msg", task.destination.getAbsolutePath()));
                        it.remove();
                    }
                }
                else {
                    it.remove();
                }
            }
        }

        if (!fileExtractionTasks.isEmpty()) {
            try {
                FileExtracter extracter = new FileExtracter(fileExtractionTasks);    
                extracter.execute();
            } 
            catch (Exception ex) {
                logger.log(Level.WARNING, "Unable to start background file extraction thread", ex);
            }                                    
        }
        else {
            MessageNotifyUtil.Message.info(
                    NbBundle.getMessage(this.getClass(), "ExtractAction.notifyDlg.noFileToExtr.msg"));
        }
    }
        
    private class FileExtractionTask {
        AbstractFile source;
        File destination;

        FileExtractionTask(AbstractFile source, File destination) {
            this.source = source;
            this.destination = destination;
        }        
    }
        
    private class FileExtracter extends SwingWorker<Object,Void> {
        private Logger logger = Logger.getLogger(FileExtracter.class.getName());
        private ProgressHandle progress;
        private ArrayList<FileExtractionTask> extractionTasks;
        
        FileExtracter(ArrayList<FileExtractionTask> extractionTasks) {
            this.extractionTasks = extractionTasks;            
        }
        
        @Override
        protected Object doInBackground() throws Exception {
            if (extractionTasks.isEmpty()) {
                return null;
            }
            
            // Setup progress bar.
            final String displayName = NbBundle.getMessage(this.getClass(), "ExtractAction.progress.extracting");
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    if (progress != null)
                        progress.setDisplayName(
                                NbBundle.getMessage(this.getClass(), "ExtractAction.progress.cancellingExtraction", displayName));
                    return ExtractAction.FileExtracter.this.cancel(true);
                }
            });
            progress.start();
            progress.switchToIndeterminate();
            
            /* @@@ Add back in -> Causes exceptions
            int workUnits = 0;
            for (FileExtractionTask task : extractionTasks) {
                workUnits += calculateProgressBarWorkUnits(task.source);
            }
            progress.switchToDeterminate(workUnits);
            */
        
            // Do the extraction tasks.
            for (FileExtractionTask task : this.extractionTasks) {
                // @@@ Note, we are no longer passing in progress
                ExtractFscContentVisitor.extract(task.source, task.destination, null, this);            
            }
            
            return null;
        }
        
        @Override
        protected void done() {
            boolean msgDisplayed = false;
            try {
                super.get();
            } 
            catch (Exception ex) {
                logger.log(Level.SEVERE, "Fatal error during file extraction", ex);
                MessageNotifyUtil.Message.info("Error extracting files: " + ex.getMessage());
                msgDisplayed = true;
            }  
            finally {
                progress.finish();
                if (!this.isCancelled() && !msgDisplayed) {
                    MessageNotifyUtil.Message.info(
                            NbBundle.getMessage(this.getClass(), "ExtractAction.done.notifyMsg.fileExtr.text"));
                } 
            }
        }
        
        private int calculateProgressBarWorkUnits(AbstractFile file) {
            int workUnits = 0;
            if (file.isFile()) {
                workUnits += file.getSize();
            }
            else {
                try {
                    for (Content child : file.getChildren()) {
                       if (child instanceof AbstractFile) {
                        workUnits += calculateProgressBarWorkUnits((AbstractFile)child);
                       }
                    }
                }
                catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Could not get children of content", ex);                
                }
            }
            return workUnits;            
        }
    } 
}
