/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType;
import org.openide.nodes.Node;
import org.openide.nodes.Node.PropertySet;
import org.openide.nodes.Node.Property;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;

/**
 * Exports CSV version of result nodes to a location selected by the user.
 */
public final class ExportCSVAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(ExportCSVAction.class.getName());
    private final static String DEFAULT_FILENAME = "Results";
    private final static List<String> columnsToSkip = Arrays.asList(AbstractFilePropertyType.SCORE.toString(),
            AbstractFilePropertyType.COMMENT.toString(), AbstractFilePropertyType.OCCURRENCES.toString());

    private static String userDefinedExportPath;

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ExportCSVAction instance;
    
    private static final JFileChooserFactory chooserHelper = new JFileChooserFactory();

    /**
     * Get an instance of the Action. See above for why
     * the class is a singleton.
     * 
     * @return the instance
     */
    public static synchronized ExportCSVAction getInstance() {
        if (null == instance) {
            instance = new ExportCSVAction();
        }
        return instance;
    }

    /**
     * Private constructor for the action.
     */
    @NbBundle.Messages({"ExportCSV.title.text=Export Selected Rows to CSV"})
    private ExportCSVAction() {
        super(Bundle.ExportCSV_title_text());
    }
    
    /**
     * Asks user to choose destination, then extracts content to destination
     * (recursing on directories).
     *
     * @param e The action event.
     */

    @Override
    public void actionPerformed(ActionEvent e) {
        Collection<? extends Node> selectedNodes = Utilities.actionsGlobalContext().lookupAll(Node.class);
        saveNodesToCSV(selectedNodes, (Component)e.getSource());
    }

    /**
     * Save the selected nodes to a CSV file
     * 
     * @param nodesToExport the nodes to save
     * @param component
     */
    @NbBundle.Messages({
        "# {0} - Output file",
        "ExportCSV.saveNodesToCSV.fileExists=File {0} already exists",
        "ExportCSV.saveNodesToCSV.noCurrentCase=No open case available",
        "ExportCSV.saveNodesToCSV.empty=No data to export"})
    public static void saveNodesToCSV(Collection<? extends Node> nodesToExport, Component component) {

        if (nodesToExport.isEmpty()) {
            MessageNotifyUtil.Message.info(Bundle.ExportCSV_saveNodesToCSV_empty());
            return;
        }
        
        try {   
            // Set up the file chooser with a default name and either the Export
            // folder or the last used folder.
            String fileName = getDefaultOutputFileName(nodesToExport.iterator().next().getParentNode());
            JFileChooser fileChooser = chooserHelper.getChooser();
            fileChooser.setCurrentDirectory(new File(getExportDirectory(Case.getCurrentCaseThrows())));
            fileChooser.setSelectedFile(new File(fileName));
            fileChooser.setFileFilter(new FileNameExtensionFilter("csv file", "csv"));

            int returnVal = fileChooser.showSaveDialog(component);
            if (returnVal == JFileChooser.APPROVE_OPTION) {

                // Get the file name, appending .csv if necessary
                File selectedFile = fileChooser.getSelectedFile();
                if (!selectedFile.getName().endsWith(".csv")) { // NON-NLS
                    selectedFile = new File(selectedFile.toString() + ".csv"); // NON-NLS
                }
                
                // Save the directory used for next time
                updateExportDirectory(selectedFile.getParent(), Case.getCurrentCaseThrows());
        
                if (selectedFile.exists()) {
                    logger.log(Level.SEVERE, "File {0} already exists", selectedFile.getAbsolutePath()); //NON-NLS
                    MessageNotifyUtil.Message.info(Bundle.ExportCSV_saveNodesToCSV_fileExists(selectedFile));
                    return;
                }

                CSVWriter writer = new CSVWriter(nodesToExport, selectedFile);
                writer.execute();
            }
        } catch (NoCurrentCaseException ex) {
            JOptionPane.showMessageDialog(component, Bundle.ExportCSV_saveNodesToCSV_noCurrentCase());
            logger.log(Level.INFO, "Exception while getting open case.", ex); //NON-NLS
        }
    }
    
    /**
     * Create a default name for the CSV output.
     * 
     * @param parent The parent node for the selected nodes
     * 
     * @return the default name
     */
    private static String getDefaultOutputFileName(Node parent) {
        String dateStr = String.format("%1$tY%1$tm%1$te%1$tI%1$tM%1$tS", Calendar.getInstance());
        
        if (parent != null) {
            // The first value in the property set is generally a reasonable name
            for (PropertySet set : parent.getPropertySets()) {
                for (Property<?> prop : set.getProperties()) {
                    try {
                        String parentName = prop.getValue().toString();
                        
                        // Strip off the count (if present)
                        parentName = parentName.replaceAll("\\([0-9]+\\)$", "");
                        
                        // Strip out any invalid characters
                        parentName = parentName.replaceAll("[\\\\/:*?\"<>|]", "_");
                        
                        return parentName + " " + dateStr;
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        logger.log(Level.WARNING, "Failed to get property set value as string", ex);
                    }
                }
            }
        }
        return DEFAULT_FILENAME + " " + dateStr;
    }
    
    /**
     * Get the export directory path.
     *
     * @param openCase The current case.
     *
     * @return The export directory path.
     */
    private static String getExportDirectory(Case openCase) {
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
    private static void updateExportDirectory(String exportPath, Case openCase) {
        if (exportPath.equalsIgnoreCase(openCase.getExportDirectory())) {
            userDefinedExportPath = null;
        } else {
            userDefinedExportPath = exportPath;
        }
    }

    
    /**
     * Thread that does the actual extraction work
     */
    private static class CSVWriter extends SwingWorker<Object, Void> {

        private static final Logger logger = Logger.getLogger(CSVWriter.class.getName());
        private ProgressHandle progress;
        
        private final Collection<? extends Node> nodesToExport;
        private final File outputFile;

        /**
         * Create an instance of the CSVWriter.
         *
         * @param extractionTasks List of file extraction tasks.
         */
        CSVWriter(Collection<? extends Node> nodesToExport, File outputFile) {
            this.nodesToExport = nodesToExport;
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
                PropertySet[] sets = nodesToExport.iterator().next().getPropertySets();
                for(PropertySet set : sets) {
                    for (Property<?> prop : set.getProperties()) {
                        if ( ! columnsToSkip.contains(prop.getDisplayName())) {
                            headers.add(prop.getDisplayName());
                        }
                    }
                }
                br.write(listToCSV(headers));
                
                // Write each line
                Iterator<?> nodeIterator = nodesToExport.iterator();
                while (nodeIterator.hasNext()) {
                    if (this.isCancelled()) {
                        break;
                    }
                    
                    Node node = (Node)nodeIterator.next();
                    List<String> values = new ArrayList<>();
                    sets = node.getPropertySets();
                    for(PropertySet set : sets) {
                        for (Property<?> prop : set.getProperties()) {
                            if ( ! columnsToSkip.contains(prop.getDisplayName())) {
                                values.add(escapeQuotes(prop.getValue().toString()));
                            }
                        }
                    }
                    br.write(listToCSV(values));
                }
            }

            return null;
        }
        
        /**
         * Escape any quotes in the string
         * 
         * @param original
         * 
         * @return the string with quotes escaped
         */
        private String escapeQuotes(String original) {
            return original.replaceAll("\"", "\\\\\"");
        }
        
        /**
         * Convert list of values to a comma separated string.
         * 
         * @param values Values to convert
         * 
         * @return values as CSV
         */
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
