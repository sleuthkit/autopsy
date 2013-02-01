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
import org.openide.nodes.Node;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;

/**
 * Exports files and folders
 */
public final class ExtractAction extends AbstractAction {

    private static final InitializeContentVisitor initializeCV = new InitializeContentVisitor();
    private AbstractFile content;
    private Logger logger = Logger.getLogger(ExtractAction.class.getName());

    public ExtractAction(String title, Node contentNode) {
        super(title);
        Content tempContent = contentNode.getLookup().lookup(Content.class);

        this.content = tempContent.accept(initializeCV);
        this.setEnabled(content != null);
    }
    
     public ExtractAction(String title, Content content) {
        super(title);

        this.content = content.accept(initializeCV);
        this.setEnabled(this.content != null);
    }

    /**
     * Returns the FsContent if it is supported, otherwise null
     */
    private static class InitializeContentVisitor extends ContentVisitor.Default<AbstractFile> {

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.File f) {
            return f;
        }
        
        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.LayoutFile lf) {
            return lf;
        }
        
        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.DerivedFile df) {
            return df;
        }

        @Override
        public AbstractFile visit(Directory dir) {
            return ContentUtils.isDotDirectory(dir) ? null : dir;
        }

        @Override
        protected AbstractFile defaultVisit(Content cntnt) {
            return null;
        }
    }

    /**
     * Asks user to choose destination, then extracts content/directory to 
     * destination (recursing on directories)
     * @param e  the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        // Get content and check that it's okay to overwrite existing content
        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File(Case.getCurrentCase().getCaseDirectory()));
        fc.setSelectedFile(new File(this.content.getName()));
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
                extract.init(this.content, e, destination);
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
