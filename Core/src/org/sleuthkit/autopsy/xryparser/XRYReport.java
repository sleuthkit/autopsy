/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.xryparser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 *
 */
public class XRYReport {
    
    //Number of lines that make up the header of the report file.
    private final static int LINES_IN_HEADER = 5;
    
    //Header line number that corresponds to the report type.
    private final static int LINE_WITH_REPORT_TYPE = 3;
    
    //Encoding of the XRY Report file.
    private final static Charset REPORT_ENCODING = StandardCharsets.UTF_16LE;
    
    //Path to the physical report file
    private final Path reportPath;
    
    //XRY Report type (Calls, Messages, etc)
    private final String reportType;
    
    /**
     * 
     * @param reportPath
     * @throws IOException 
     */
    public XRYReport(Path reportPath) throws IOException {
        this.reportPath = reportPath;
        this.reportType = parseType(reportPath);
    }

    /**
     * 
     * @return 
     */
    public String getType() {
        return reportType;
    }
    
    /**
     * 
     * @return 
     */
    public Path getPath() {
        return reportPath;
    }
    
    /**
     * 
     * @return 
     */
    public Charset getEncoding() {
        return REPORT_ENCODING;
    }
    
    /**
     * 
     * @return 
     */
    public int getTotalLinesInHeader() {
        return LINES_IN_HEADER;
    }
    
    /**
     * 
     * @return 
     */
    public int getReportTypeLineNumber() {
        return LINE_WITH_REPORT_TYPE;
    }
    
    /**
     * 
     * @param report
     * @return
     * @throws IOException 
     */
    private String parseType(Path report) throws IOException {
        try {
            BufferedReader reader = Files.newBufferedReader(report, this.getEncoding());
            
            //Limit this stream to only the length of the header
            //and skip to the line just before the type information.
            Stream<String> xryReportHeader = reader.lines()
                    .limit(this.getTotalLinesInHeader())
                    .skip(this.getReportTypeLineNumber() - 1);
            
            Optional<String> type = xryReportHeader.findFirst();
            if(!type.isPresent()) {
                throw new IllegalArgumentException("Report did not have a type.");
            }
            
            if(type.get().isEmpty()) {
                throw new IllegalArgumentException("Report did not have a type.");
            }
            
            return type.get();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}