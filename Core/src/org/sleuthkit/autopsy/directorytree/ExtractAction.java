/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.Cancellable;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Exports files and folders
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
        super("Extract");
    }
        
    /**
     * Asks user to choose destination, then extracts content/directory to 
     * destination (recursing on directories)
     * @param e  the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Lookup.Result<ExplorerManager.Provider> res = Utilities.actionsGlobalContext().lookupResult(ExplorerManager.Provider.class);
        for (ExplorerManager.Provider dude : res.allInstances()) {
            ExplorerManager ex = dude.getExplorerManager();
            Node[] selectedNodes = ex.getSelectedNodes();
            for (Node node : selectedNodes) {
                String name = node.getDisplayName();
            }            
        } 
        
        DataResultViewerTable resultViewer = (DataResultViewerTable)Lookup.getDefault().lookup(DataResultViewer.class);
        if (null == resultViewer) {
            Logger.getLogger(ExtractAction.class.getName()).log(Level.SEVERE, "Could not get DataResultViewerTable from Lookup");
            return;
        }
        
        Node[] selectedNodes = resultViewer.getExplorerManager().getSelectedNodes();
        if (selectedNodes.length <= 0) {
            Logger.getLogger(ExtractAction.class.getName()).log(Level.SEVERE, "Tried to perform tagging of Nodes with no Nodes selected");
            return;
        }
        
        for (Node node : selectedNodes) {
            AbstractFile file = node.getLookup().lookup(AbstractFile.class);
            if (null != file) {
                extractFile(e, file);
            }
            else {
                // RJCTODO
//                    Logger.getLogger(org.sleuthkit.autopsy.directorytree.TagAbstractFileAction.TagAbstractFileMenu.class.getName()).log(Level.SEVERE, "Node not associated with an AbstractFile object");                
            }
        }        
    }
    
    private void extractFile(ActionEvent e, AbstractFile file) {
        // Get content and check that it's okay to overwrite existing content
        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File(Case.getCurrentCase().getCaseDirectory()));
        fc.setSelectedFile(new File(file.getName()));
        int returnValue = fc.showSaveDialog((Component) e.getSource());

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File destination = fc.getSelectedFile();

            // do the check
            if (destination.exists()) {
                int choice = JOptionPane.showConfirmDialog(
                        (Component) e.getSource(),
                        "Destination file already exists, it will be overwritten.",
                        "Destination already exists!",
                        JOptionPane.OK_CANCEL_OPTION);

                if (choice != JOptionPane.OK_OPTION) {
                    return; // Just exit the function
                }

                if (!destination.delete()) {
                    JOptionPane.showMessageDialog(
                            (Component) e.getSource(),
                            "Couldn't delete existing file.");
                }
            }
        
            try {
                ExtractFileThread extract = new ExtractFileThread();    
                extract.init(file, e, destination);
                extract.execute();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Unable to start background thread.", ex);
            }
        }        
    }
    
    private class ExtractFileThread extends SwingWorker<Object,Void> {
        private Logger logger = Logger.getLogger(ExtractFileThread.class.getName());
        private ProgressHandle progress;
        private AbstractFile fsContent;
        private ActionEvent e;
        private File destination;
        
        private void init(AbstractFile fsContent, ActionEvent e, File destination) {
            this.fsContent = fsContent;
            this.e = e;
            this.destination = destination;
        }

        @Override
        protected Object doInBackground() throws Exception {
            logger.log(Level.INFO, "Starting background processing for file extraction.");
            
            // Setup progress bar
            final String displayName = "Extracting";
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    if (progress != null)
                        progress.setDisplayName(displayName + " (Cancelling...)");
                    return ExtractAction.ExtractFileThread.this.cancel(true);
                }
            });

            // Start the progress bar as indeterminate
            progress.start();
            progress.switchToIndeterminate();
            if(fsContent.isFile()) {
                // Max content size of 200GB
                //long filesize = fsContent.getSize();
                //int unit = (int) (filesize / 100);
                progress.switchToDeterminate(100);
            } else if(fsContent.isDir()) {
                // If dir base progress off number of children
                int toProcess = fsContent.getChildren().size();
                progress.switchToDeterminate(toProcess);
            }

            // Start extracting the content/directory
            ExtractFscContentVisitor.extract(fsContent, destination, progress, this);
            logger.log(Level.INFO, "Done background processing");
            return null;
        }
        
        @Override
        protected void done() {
            try {
                super.get(); //block and get all exceptions thrown while doInBackground()
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Extraction was canceled.");
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Extraction was interrupted.");
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Fatal error during file extraction.", ex);
            } finally {
                progress.finish();
                if (!this.isCancelled()) {
                    logger.log(Level.INFO, "Extracting completed without cancellation.");
                    // Alert the user extraction is over
                    if(fsContent.isDir()) {
                        MessageNotifyUtil.Message.info("Directory extracted.");
                    } else if(fsContent.isFile()){
                        MessageNotifyUtil.Message.info("File extracted.");
                    }
                } else {
                    logger.log(Level.INFO, "Attempting to delete file(s).");
                    if(FileUtil.deleteFileDir(destination)) {
                        logger.log(Level.INFO, "Finished deletion sucessfully.");
                    } else {
                        logger.log(Level.WARNING, "Deletion attempt complete; not all files were sucessfully deleted.");
                    }
                }
            }
        }
    } 
}
