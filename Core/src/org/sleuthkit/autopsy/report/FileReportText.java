/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A Tab-delimited text report of the files in the case.
 * 
 * @author jwallace
 */
 class FileReportText implements FileReportModule {
    private static final Logger logger = Logger.getLogger(FileReportText.class.getName());
    private String reportPath;
    private Writer out;
    private static final String FILE_NAME = "file-report.txt";
    
    private static FileReportText instance;
    
    // Get the default implementation of this report
    public static synchronized FileReportText getDefault() {
        if (instance == null) {
            instance = new FileReportText();
        }
        return instance;
    }
    
    @Override
    public void startReport(String path) {
        this.reportPath = path + FILE_NAME;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.reportPath)));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to create report text file", ex);
        }
    }

    @Override
    public void endReport() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not close output writer when ending report.", ex);
            }
        }
    }
    
    private String getTabDelimitedList(List<String> list) {
        StringBuilder output = new StringBuilder();
        Iterator<String> it = list.iterator();
        while(it.hasNext()) {
            output.append(it.next()).append((it.hasNext() ? "\t" : System.lineSeparator()));
        }
        return output.toString();
    }
    
    @Override
    public void startTable(List<FileReportDataTypes> headers) {
        List<String> titles = new ArrayList<>();
        for(FileReportDataTypes col : headers) {
            titles.add(col.getName());
        }
        try {
            out.write(getTabDelimitedList(titles));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing headers to report file: {0}", ex);
        }
    }

    @Override
    public void addRow(AbstractFile toAdd, List<FileReportDataTypes> columns) {
        List<String> cells = new ArrayList<>();
        for(FileReportDataTypes type : columns) {
            cells.add(type.getValue(toAdd));
        }
        try {
            out.write(getTabDelimitedList(cells));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing row to report file: {0}", ex);
        }
    }

    @Override
    public void endTable() {
        try {
            out.write(System.lineSeparator());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when closing table: {0}", ex);
        }
    }

    @Override
    public String getName() {
        return "Files - Text";
    }

    @Override
    public String getDescription() {
        return "A tab delimited text file containing information about files in the case."; 
    }

    @Override
    public String getExtension() {
        return ".txt";
    }

    @Override
    public String getFilePath() {
        return FILE_NAME;
    }
}
