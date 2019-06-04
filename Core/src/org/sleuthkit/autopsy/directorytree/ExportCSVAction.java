/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.openide.nodes.Node.PropertySet;
import org.openide.nodes.Node.Property;

/**
 * Exports CSV version of result nodes to a location selected by the user.
 */
public final class ExportCSVAction extends AbstractAction {

    private Logger logger = Logger.getLogger(ExportCSVAction.class.getName());

    private String userDefinedExportPath;

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ExportCSVAction instance;

    public static synchronized ExportCSVAction getInstance() {
        if (null == instance) {
            instance = new ExportCSVAction();
        }
        return instance;
    }

    /**
     * Private constructor for the action.
     */
    @NbBundle.Messages({"ExportCSV.title.text=Export to CSV"})
    private ExportCSVAction() {
        super(Bundle.ExportCSV_title_text());
    }

    /**
     * Asks user to choose destination, then extracts content to destination
     * (recursing on directories).
     *
     * @param e The action event.
     */
    @NbBundle.Messages({
        "# {0} - Output file",
        "ExportCSV.actionPerformed.fileExists=File {0} already exists",
        "ExportCSV.actionPerformed.noCurrentCase=No open case available"})
    @Override
    public void actionPerformed(ActionEvent e) {
        
        Collection<? extends Node> selectedNodes = Utilities.actionsGlobalContext().lookupAll(Node.class);
        if (selectedNodes.isEmpty()) {
            return;
        }
        
        Node parent = selectedNodes.iterator().next().getParentNode();
        if (parent != null) {
            System.out.println("HTML name: " + parent.getHtmlDisplayName());
            System.out.println("Display name: " + parent.getDisplayName());
            System.out.println("Class: " + parent.getClass().getCanonicalName());
            for (PropertySet set : parent.getPropertySets()) {
                for (Property prop : set.getProperties()) {
                    try {
                        System.out.println("  "  + prop.getDisplayName() + " : " + prop.getValue().toString());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        
        try {   
            String fileName = String.format("%1$tY%1$tm%1$te%1$tI%1$tM%1$tS_listing.csv", Calendar.getInstance());
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File(getExportDirectory(Case.getCurrentCaseThrows())));
            fileChooser.setSelectedFile(new File(fileName));
            fileChooser.setFileFilter(new FileNameExtensionFilter("csv file", "csv"));

            int returnVal = fileChooser.showSaveDialog((Component) e.getSource());
            if (returnVal == JFileChooser.APPROVE_OPTION) {

                File selectedFile = fileChooser.getSelectedFile();
                if (!selectedFile.getName().endsWith(".csv")) { // NON-NLS
                    selectedFile = new File(selectedFile.toString() + ".csv"); // NON-NLS
                }
                updateExportDirectory(selectedFile.getParent(), Case.getCurrentCaseThrows());
        
                if (selectedFile.exists()) {
                    logger.log(Level.SEVERE, "File {0} already exists", selectedFile.getAbsolutePath()); //NON-NLS
                    MessageNotifyUtil.Message.info(Bundle.ExportCSV_actionPerformed_fileExists(selectedFile));
                    return;
                }

                CSVWriter writer = new CSVWriter(selectedNodes, selectedFile);
                writer.execute();
            }
        } catch (NoCurrentCaseException ex) {
            JOptionPane.showMessageDialog((Component) e.getSource(), Bundle.ExportCSV_actionPerformed_noCurrentCase());
            logger.log(Level.INFO, "Exception while getting open case.", ex); //NON-NLS
        }
    }
    
    /**
     * Get the export directory path.
     *
     * @param openCase The current case.
     *
     * @return The export directory path.
     */
    private String getExportDirectory(Case openCase) {
        String caseExportPath = openCase.getExportDirectory();

        if (userDefinedExportPath == null) {
            return caseExportPath;
        }

        File file = new File(userDefinedExportPath);
        if (file.exists() == false || file.isDirectory() == false) {
            return caseExportPath;
        }

        return userDefinedExportPath;
    }
    
    /**
     * Update the default export directory. If the directory path matches the
     * case export directory, then the directory used will always match the
     * export directory of any given case. Otherwise, the path last used will be
     * saved.
     *
     * @param exportPath The export path.
     * @param openCase   The current case.
     */
    private void updateExportDirectory(String exportPath, Case openCase) {
        if (exportPath.equalsIgnoreCase(openCase.getExportDirectory())) {
            userDefinedExportPath = null;
        } else {
            userDefinedExportPath = exportPath;
        }
    }

    
    /**
     * Thread that does the actual extraction work
     */
    private class CSVWriter extends SwingWorker<Object, Void> {

        private final Logger logger = Logger.getLogger(CSVWriter.class.getName());
        private ProgressHandle progress;
        
        private final List<Node> nodesToExport;
        private final File outputFile;

        /**
         * Create an instance of the CSVWriter.
         *
         * @param extractionTasks List of file extraction tasks.
         */
        CSVWriter(Collection<? extends Node> selectedNodes, File outputFile) {
            this.nodesToExport = new ArrayList<>(selectedNodes);
            this.outputFile = outputFile;
        }

        @NbBundle.Messages({"CSVWriter.progress.extracting=Exporting to CSV file",
            "CSVWriter.progress.cancelling=Cancelling"})
        @Override
        protected Object doInBackground() throws Exception {
            if (nodesToExport.isEmpty()) {
                return null;
            }

            // Set up progress bar.
            final String displayName = Bundle.CSVWriter_progress_extracting();
            progress = ProgressHandle.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    if (progress != null) {
                        progress.setDisplayName(Bundle.CSVWriter_progress_cancelling());
                    }
                    return ExportCSVAction.CSVWriter.this.cancel(true);
                }
            });
            progress.start();
            progress.switchToIndeterminate();

            try (BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
                // Write BOM
                br.write('\ufeff');
                
                // Write the header
                List<String> headers = new ArrayList<>();
                PropertySet[] sets = nodesToExport.get(0).getPropertySets();
                for(PropertySet set : sets) {
                    for (Property<?> prop : set.getProperties()) {
                        headers.add(prop.getDisplayName());
                    }
                }
                br.write(listToCSV(headers));
                
                // Write each line
                for (Node node : nodesToExport) {
                    if (this.isCancelled()) {
                        break;
                    }
                    
                    List<String> values = new ArrayList<>();
                    sets = node.getPropertySets();
                    for(PropertySet set : sets) {
                        for (Property<?> prop : set.getProperties()) {
                            values.add(prop.getValue().toString());
                        }
                    }
                    br.write(listToCSV(values));
                }
            }

            return null;
        }
        
        private String listToCSV(List<String> values) {
            return "\"" + String.join("\",\"", values) + "\"\n";
        }

        @NbBundle.Messages({"CSVWriter.done.notifyMsg.error=Error exporting to CSV file",
            "# {0} - Output file",
            "CSVWriter.done.notifyMsg.success=Wrote to {0}"})
        @Override
        protected void done() {
            boolean msgDisplayed = false;
            try {
                super.get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Fatal error during file extraction", ex); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.CSVWriter_done_notifyMsg_error());
                msgDisplayed = true;
            } catch (java.util.concurrent.CancellationException ex) {
                // catch and ignore if we were cancelled
            } finally {
                progress.finish();
                if (!this.isCancelled() && !msgDisplayed) {
                    MessageNotifyUtil.Message.info(Bundle.CSVWriter_done_notifyMsg_success(outputFile));
                }
            }
        }
    }
}
