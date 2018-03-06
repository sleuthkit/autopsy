/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.CarvingResult;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskFileRange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class parses the xml output from PhotoRec, and creates a list of entries
 * to add back in to be processed.
 */
class PhotoRecCarverOutputParser {

    private final Path basePath;
    private static final Logger logger = Logger.getLogger(PhotoRecCarverFileIngestModule.class.getName());

    PhotoRecCarverOutputParser(Path base) {
        basePath = base;
    }

    /**
     * Parses the given report.xml file, creating a List<LayoutFile> to return.
     * Uses FileManager to add all carved files that it finds to the TSK
     * database as $CarvedFiles under the passed-in parent id.
     *
     * @param xmlInputFile The XML file we are trying to read and parse
     * @param id           The parent id of the unallocated space we are
     *                     parsing.
     * @param af           The AbstractFile representing the unallocated space
     *                     we are parsing.
     *
     * @return A List<LayoutFile> containing all the files added into the
     *         database
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    List<LayoutFile> parse(File xmlInputFile, AbstractFile af, IngestJobContext context) throws FileNotFoundException, IOException {
        try {
            final Document doc = XMLUtil.loadDoc(PhotoRecCarverOutputParser.class, xmlInputFile.toString());
            if (doc == null) {
                return new ArrayList<>();
            }

            Element root = doc.getDocumentElement();
            if (root == null) {
                logger.log(Level.SEVERE, "Error loading config file: invalid file format (bad root)."); //NON-NLS
                return new ArrayList<>();
            }

            NodeList fileObjects = root.getElementsByTagName("fileobject"); //NON-NLS
            final int numberOfFiles = fileObjects.getLength();

            if (numberOfFiles == 0) {
                return new ArrayList<>();
            }
            String fileName;
            Long fileSize;
            NodeList fileNames;
            NodeList fileSizes;
            NodeList fileRanges;
            Element entry;
            Path filePath;
            FileManager fileManager = Case.getOpenCase().getServices().getFileManager();

            // create and initialize the list to put into the database
            List<CarvingResult.CarvedFile> carvedFiles = new ArrayList<>();
            for (int fileIndex = 0; fileIndex < numberOfFiles; ++fileIndex) {
                if (context.fileIngestIsCancelled() == true) {
                    // if it was cancelled by the user, result is OK
                    logger.log(Level.INFO, "PhotoRec cancelled by user"); // NON-NLS
                    MessageNotifyUtil.Notify.info(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.cancelledByUser"));
                    break;
                }
                entry = (Element) fileObjects.item(fileIndex);
                fileNames = entry.getElementsByTagName("filename"); //NON-NLS
                fileSizes = entry.getElementsByTagName("filesize"); //NON-NLS
                fileRanges = entry.getElementsByTagName("byte_run"); //NON-NLS

                fileSize = Long.parseLong(fileSizes.item(0).getTextContent());
                fileName = fileNames.item(0).getTextContent();
                filePath = Paths.get(fileName);
                if (filePath.startsWith(basePath)) {
                    fileName = filePath.getFileName().toString();
                }

                List<TskFileRange> tskRanges = new ArrayList<>();
                for (int rangeIndex = 0; rangeIndex < fileRanges.getLength(); ++rangeIndex) {

                    Long img_offset = Long.parseLong(((Element) fileRanges.item(rangeIndex)).getAttribute("img_offset")); //NON-NLS
                    Long len = Long.parseLong(((Element) fileRanges.item(rangeIndex)).getAttribute("len")); //NON-NLS

                    // Verify PhotoRec's output
                    long fileByteStart = af.convertToImgOffset(img_offset);
                    if (fileByteStart == -1) {
                        // This better never happen... Data for this file is corrupted. Skip it.
                        logger.log(Level.INFO, "Error while parsing PhotoRec output for file {0}", fileName); //NON-NLS
                        continue;
                    }

                    // check that carved file is within unalloc block
                    long fileByteEnd = img_offset + len;
                    if (fileByteEnd > af.getSize()) {
                        long overshoot = fileByteEnd - af.getSize();
                        if (fileSize > overshoot) {
                            fileSize -= overshoot;
                        } else {
                            // This better never happen... Data for this file is corrupted. Skip it.
                            continue;
                        }
                    }

                    tskRanges.add(new TskFileRange(fileByteStart, len, rangeIndex));
                }

                if (!tskRanges.isEmpty()) {
                    carvedFiles.add(new CarvingResult.CarvedFile(fileName, fileSize, tskRanges));
                }
            }
            return fileManager.addCarvedFiles(new CarvingResult(af, carvedFiles));
        } catch (NumberFormatException | TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error parsing PhotoRec output and inserting it into the database", ex); //NON-NLS
        }

        List<LayoutFile> empty = Collections.emptyList();
        return empty;
    }
}
