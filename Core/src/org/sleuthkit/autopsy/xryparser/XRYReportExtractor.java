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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * This class is responsible for extracting XRY records from a specified
 * XRYReport. XRY records are the numbered, blank line separated 'groups' in an
 * XRY report.
 *
 * Example:
 *
 * Calls #	1
 * Call Type:	Missed
 * Time:	1/2/2019 1:23:45 PM (Device)
 * From
 * Tel:         12345678
 *
 */
public final class XRYReportExtractor {

    private final XRYReport xryReport;

    /**
     * Creates an XRYReportExtractor.
     *
     * @param report Report to be extracted.
     */
    public XRYReportExtractor(XRYReport report) {
        this.xryReport = report;
    }

    /**
     *
     * @param parser
     * @throws IOException
     */
    public List<BlackboardArtifact> extract(XRYRecordParser parser) throws IOException {
        try {
            BufferedReader reader = Files.newBufferedReader(xryReport.getPath(), xryReport.getEncoding());

            //Get a stream of all lines in the file. Skip the first n header lines.
            Stream<String> xryReportStream = reader.lines().skip(xryReport.getTotalLinesInHeader());

            StringBuilder xryRecord = new StringBuilder();
            List<BlackboardArtifact> artifacts = new ArrayList<>();
            xryReportStream.forEach((line) -> {
                if (this.isEndOfXRYRecord(line)) {
                    //Pass only non empty XRY records to the parser.
                    if (xryRecord.length() > 0) {
                        artifacts.add(parser.makeArtifact(xryRecord.toString()));
                        xryRecord.setLength(0);
                    }
                } else {
                    xryRecord.append(line).append("\n");
                }
            });

            //The file may have ended without a blank line (which is used to delimit
            //records). The last XRY record would not have been processed.
            if (xryRecord.length() > 0) {
                artifacts.add(parser.makeArtifact(xryRecord.toString()));
            }

            return artifacts;
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    /**
     * Determines if the line encountered during file reading signifies the end
     * of an XRYRecord.
     *
     * @param line
     * @return
     */
    private boolean isEndOfXRYRecord(String line) {
        return line.isEmpty();
    }
}
