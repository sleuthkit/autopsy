/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 - 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.file;

import org.sleuthkit.autopsy.report.infrastructure.FileReportDataTypes;
import org.sleuthkit.autopsy.report.NoReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.report.infrastructure.FileReportModule;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A delimited text report of the files in the case.
 *
 * @author jwallace
 */
class FileReportText implements FileReportModule {

    private static final Logger logger = Logger.getLogger(FileReportText.class.getName());
    private static final String FILE_NAME = "file-report.txt"; //NON-NLS
    private static FileReportText instance;
    private String reportPath;
    private Writer out;
    private ReportFileTextConfigurationPanel configPanel;

    // Get the default implementation of this report
    public static synchronized FileReportText getDefault() {
        if (instance == null) {
            instance = new FileReportText();
        }
        return instance;
    }

    /**
     * Get default configuration for this report module.
     *
     * @return Object which contains default report module settings.
     */
    @Override
    public ReportModuleSettings getDefaultConfiguration() {
        return new FileReportModuleSettings();
    }

    /**
     * Get current configuration for this report module.
     *
     * @return Object which contains current report module settings.
     */
    @Override
    public ReportModuleSettings getConfiguration() {
        initializePanel();
        return configPanel.getConfiguration();
    }

    /**
     * Set report module configuration.
     *
     * @param settings Object which contains report module settings.
     */
    @Override
    public void setConfiguration(ReportModuleSettings settings) {
        initializePanel();
        if (settings == null || settings instanceof NoReportModuleSettings) {
            configPanel.setConfiguration((FileReportModuleSettings) getDefaultConfiguration());
            return;
        }

        if (settings instanceof FileReportModuleSettings) {
            configPanel.setConfiguration((FileReportModuleSettings) settings);
            return;
        }

        throw new IllegalArgumentException("Expected settings argument to be an instance of FileReportModuleSettings");
    }

    @Override
    public void startReport(String baseReportDir) {
        this.reportPath = baseReportDir + FILE_NAME;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.reportPath), StandardCharsets.UTF_8));
            out.write('\ufeff');
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "Failed to create report text file", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to write BOM to report text file", ex); //NON-NLS
        }
    }

    @Override
    public void endReport() {
        if (out != null) {
            try {
                out.close();
                Case.getCurrentCaseThrows().addReport(reportPath, NbBundle.getMessage(this.getClass(),
                        "FileReportText.getName.text"), "");
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not close output writer when ending report.", ex); //NON-NLS
            } catch (TskCoreException ex) {
                String errorMessage = String.format("Error adding %s to case as a report", reportPath); //NON-NLS
                logger.log(Level.SEVERE, errorMessage, ex);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            }
        }
    }

    private String getDelimitedList(List<String> list, String delimiter) {
        StringBuilder output;
        output = new StringBuilder();
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            output.append('"').append(it.next()).append('"').append((it.hasNext() ? delimiter : System.lineSeparator()));
        }
        return output.toString();
    }

    @Override
    public void startTable(List<FileReportDataTypes> headers) {
        List<String> titles = new ArrayList<>();
        for (FileReportDataTypes col : headers) {
            titles.add(col.getName());
        }
        try {
            out.write(getDelimitedList(titles, configPanel.getDelimiter()));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing headers to report file: {0}", ex); //NON-NLS
        }
    }

    @Override
    public void addRow(AbstractFile toAdd, List<FileReportDataTypes> columns) {
        List<String> cells = new ArrayList<>();
        for (FileReportDataTypes type : columns) {
            cells.add(type.getValue(toAdd));
        }
        try {
            out.write(getDelimitedList(cells, configPanel.getDelimiter()));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing row to report file: {0}", ex); //NON-NLS
        }
    }

    @Override
    public void endTable() {
        try {
            out.write(System.lineSeparator());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when closing table: {0}", ex); //NON-NLS
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "FileReportText.getName.text");
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "FileReportText.getDesc.text");
    }

    @Override
    public String getRelativeFilePath() {
        return FILE_NAME;
    }

    @Override
    public JPanel getConfigurationPanel() {
        initializePanel();
        return configPanel;
    }

    private void initializePanel() {
        if (configPanel == null) {
            configPanel = new ReportFileTextConfigurationPanel();
        }
    }
}
