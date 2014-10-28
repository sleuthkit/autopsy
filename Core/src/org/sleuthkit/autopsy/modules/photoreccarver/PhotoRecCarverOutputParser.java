/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.photoreccarver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.CarvedFileContainer;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskFileRange;

/**
 * This class parses the xml output from PhotoRec, and creates a list of entries to add back in to be processed.
 */
public class PhotoRecCarverOutputParser {

    private static final Logger logger = Logger.getLogger(PhotoRecCarverFileIngestModule.class.getName());

    public PhotoRecCarverOutputParser() {
    }

    public String getValue(String name, String line) {
        return line.replaceAll("[\t ]*</?" + name + ">", ""); //NON-NLS
    }

    public List<LayoutFile> parse(File xmlInputFile, long id, AbstractFile af) throws FileNotFoundException, IOException {
        try {
            String fileName;
            long fileSize;
            String result;
            String[] fields;
            FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

            // create and initialize the list to put into the database
            List<CarvedFileContainer> carvedFileContainer = new ArrayList<CarvedFileContainer>();

            // create a BufferedReader
            BufferedReader in = new BufferedReader(new FileReader(xmlInputFile));

            // create and initialize a line
            String line = in.readLine();

            // loop until an empty line is read
            reachedEndOfFile:
            while (!line.isEmpty()) {
                while (!line.contains("<fileobject>")) //NON-NLS
                {
                    if (line.equals("</dfxml>")) //NON-NLS
                    { // We have found the end. Break out of both loops and move on to processing.
                        break reachedEndOfFile;
                    }
                    line = in.readLine();
                }

                List<TskFileRange> ranges = new ArrayList<TskFileRange>();

                // read filename line
                line = in.readLine();
                fileName = getValue("filename", line); //NON-NLS

                line = in.readLine(); /// read filesize line
                fileSize = Long.parseLong(getValue("filesize", line)); //NON-NLS

                in.readLine(); /// eat a line and move on to the next

                line = in.readLine(); /// now get next valid line
                while (line.contains("<byte_run")) //NON-NLS
                {
                    result = line.replaceAll("[\t ]*<byte_run offset='", ""); //NON-NLS
                    result = result.replaceAll("'[\t ]*img_offset='", " "); //NON-NLS
                    result = result.replaceAll("'[\t ]*len='", " "); //NON-NLS
                    result = result.replaceAll("'/>[\t ]*", ""); //NON-NLS
                    fields = result.split(" ");  /// offset, image offset, length //NON-NLS
                    ranges.add((new TskFileRange(af.convertToImgOffset(Long.parseLong(fields[1])), Long.parseLong(fields[2]), ranges.size())));

                    // read the next line
                    line = in.readLine();
                }
                carvedFileContainer.add(new CarvedFileContainer(fileName, fileSize, id, ranges));
            }

            in.close(); // close the BufferedReader
            return fileManager.addCarvedFiles(carvedFileContainer);
        }
        catch (IOException | NumberFormatException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error parsing PhotoRec output and inserting it into the database" + ex); //NON_NLS
        }
        return null;
    }
}
