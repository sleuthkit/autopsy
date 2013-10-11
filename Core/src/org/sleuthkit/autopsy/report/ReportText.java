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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author jwallace
 */
public class ReportText implements TableReportModule {
    private static final Logger logger = Logger.getLogger(ReportText.class.getName());
    private String reportPath;
    private Writer out;
    ReportText() {
        // do nothing for now
    }
    
    @Override
    public void startReport(String path) {
        this.reportPath = path + "report.txt";
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

    @Override
    public void startDataType(String title) {
        StringBuilder start = new StringBuilder();
        start.append(title.toUpperCase()).append("\n");
        try {
            out.write(start.toString());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing to report file: {0}", ex);
        }
    }

    @Override
    public void endDataType() {
        try {
            out.write("\n\n");
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing to report file: {0}", ex);
        }
    }

    @Override
    public void startSet(String setName) {
        // Do nothing for now.
    }

    @Override
    public void endSet() {
        // Do nothing for now.
    }

    @Override
    public void addSetIndex(List<String> sets) {
        // Do nothing for now.
    }

    @Override
    public void addSetElement(String elementName) {
        // Do nothing for now.
    }

    @Override
    public void startTable(List<String> titles) {
        try {
            out.write(getTabDelimitedList(titles));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing headers to report file: {0}", ex);
        }
    }

    @Override
    public void endTable() {
        try {
            out.write("\n");
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing to report file: {0}", ex);
        }
    }
    
    private String getTabDelimitedList(List<String> list) {
        StringBuilder output = new StringBuilder();
        Iterator<String> it = list.iterator();
        while(it.hasNext()) {
            output.append(it.next()).append((it.hasNext() ? "\t" : "\n"));
        }
        return output.toString();
    }

    @Override
    public void addRow(List<String> row) {
        try {
            out.write(getTabDelimitedList(row));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error when writing row to report file: {0}", ex);
        }
    }

    @Override
    public String dateToString(long date) {
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return sdf.format(new java.util.Date(date * 1000));
    }

    @Override
    public String getName() {
        return "report";
    }

    @Override
    public String getDescription() {
        return "A tab delimited report file.";
    }

    @Override
    public String getExtension() {
        return ".txt";
    }

    @Override
    public String getFilePath() {
        return this.reportPath;
    }
    
}
