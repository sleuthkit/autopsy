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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Processes all XRY files in an XRY folder.
 */
final class XRYReportProcessor {

    private static final Logger logger = Logger.getLogger(XRYReportProcessor.class.getName());

    /**
     * Processes all XRY Files and creates artifacts on the given Content
     * instance.
     *
     * All resources will be closed if an exception is encountered.
     *
     * @param folder XRY folder to process
     * @param parent Content instance to hold newly created artifacts.
     * @throws IOException If an I/O exception occurs.
     * @throws TskCoreException If an error occurs adding artifacts.
     */
    static void process(XRYFolder folder, Content parent) throws IOException, TskCoreException {
        //Get all XRY file readers from this folder.
        List<XRYFileReader> xryFileReaders = folder.getXRYFileReaders();

        try {
            for (XRYFileReader xryFileReader : xryFileReaders) {
                String reportType = xryFileReader.getReportType();
                if (XRYFileParserFactory.supports(reportType)) {
                    XRYFileParser parser = XRYFileParserFactory.get(reportType);
                    parser.parse(xryFileReader, parent);
                } else {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] XRY File (in brackets) "
                            + "[ %s ] was found, but no parser to support its report type exists. "
                            + "Report type is [ %s ]", xryFileReader.getReportPath().toString(), reportType));
                }
            }
        } finally {
            try {
                //Try to close all resources
                for (XRYFileReader xryFileReader : xryFileReaders) {
                    xryFileReader.close();
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "[XRY DSP] Encountered I/O exception trying "
                        + "to close all xry file readers.", ex);
            }
        }
    }

    //Prevent direct instantiation.
    private XRYReportProcessor() {

    }
}
