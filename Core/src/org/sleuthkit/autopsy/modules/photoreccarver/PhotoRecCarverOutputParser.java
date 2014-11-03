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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
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
class PhotoRecCarverOutputParser {

    private final Path basePath;
    private static final Logger logger = Logger.getLogger(PhotoRecCarverFileIngestModule.class.getName());
    private static final List<LayoutFile> EMPTY_LIST = Collections.unmodifiableList(new ArrayList<LayoutFile>());
    
    PhotoRecCarverOutputParser(Path base) {
        basePath = base;
    }

    /**
     * Gets the value inside the XML element and returns it. Ignores leading whitespace.
     *
     * @param name The XML element we are looking for.
     * @param line The line in which we are looking for the element.
     * @return The String value found
     */
    private static String getValue(String name, String line) {
        return line.replaceAll("[\t ]*</?" + name + ">", ""); //NON-NLS
    }

    /**
     * Parses the given report.xml file, creating a List<LayoutFile> to return. Uses FileManager to add
     * all carved files that it finds to the TSK database as $CarvedFiles under the passed-in parent id.
     *
     * @param xmlInputFile The XML file we are trying to read and parse
     * @param id The parent id of the unallocated space we are parsing.
     * @param af The AbstractFile representing the unallocated space we are parsing.
     * @return A List<LayoutFile> containing all the files added into the database
     * @throws FileNotFoundException
     * @throws IOException
     */
    List<LayoutFile> parse(File xmlInputFile, long id, AbstractFile af) throws FileNotFoundException, IOException {
        try {
            String fileName;
            long fileSize;
            String result;
            String[] fields;

            FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

            // create and initialize the list to put into the database
            List<CarvedFileContainer> carvedFileContainer = new ArrayList<CarvedFileContainer>();

            // create and initialize a line
            try ( // create a BufferedReader
                    BufferedReader in = new BufferedReader(new FileReader(xmlInputFile))) {
                // create and initialize a line
                String line = in.readLine();
                
                // loop until an empty line is read
                reachedEndOfFile:
                while (!line.isEmpty()) {
                    while (!line.contains("<fileobject>")) //NON-NLS
                    {
                        if (line.equals("</dfxml>")) //NON-NLS
                        { // We have found the end. Break out of both loops and move on to processing.
                            line=""; /// KDM does this break right?
                            break reachedEndOfFile;
                        }
                        line = in.readLine();
                    }
                    
                    List<TskFileRange> ranges = new ArrayList<TskFileRange>();
                    
                    // read filename line
                    line = in.readLine();
                    fileName = getValue("filename", line); //NON-NLS
                    Path p = Paths.get(fileName);
                    if (p.startsWith(basePath)) {
                        fileName = p.getFileName().toString();
                    }
                    
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
            }
            return fileManager.addCarvedFiles(carvedFileContainer);
        }
        catch (IOException | NumberFormatException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error parsing PhotoRec output and inserting it into the database{0}", ex); //NON_NLS
        }
        return EMPTY_LIST;
    }
}
