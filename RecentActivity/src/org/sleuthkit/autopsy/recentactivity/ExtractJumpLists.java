/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
 *
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.poi.EmptyFileException;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.NotOLE2FileException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.JLNK;
import org.sleuthkit.autopsy.coreutils.JLnkParser;
import org.sleuthkit.autopsy.coreutils.JLnkParserException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Extract the LNK files from the jumplists and save them to
 * ModuleOutput\RecentActivity\Jumplists and then add them back into the case as
 * a dervived file.
 */
final class ExtractJumpLists extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractJumpLists.class.getName());
    private static final String RA_DIR_NAME = "RecentActivity"; //NON-NLS
    private static final String AUTOMATIC_DESTINATIONS_FILE_DIRECTORY = "%/AppData/Roaming/Microsoft/Windows/Recent/AutomaticDestinations/";
    private static final String JUMPLIST_DIR_NAME = "jumplists"; //NON-NLS
    private static final String VERSION_NUMBER = "1.0.0"; //NON-NLS
    private String moduleName;
    private FileManager fileManager;
    private final IngestServices services = IngestServices.getInstance();
    private final IngestJobContext context;

    @Messages({
        "Jumplist_module_name=Windows Jumplist Analyzer",
        "Jumplist_adding_extracted_files_msg=Chrome Cache: Adding %d extracted files for analysis."
    })
    ExtractJumpLists(IngestJobContext context) {
        super(Bundle.Jumplist_module_name(), context);
        this.context = context;
    }

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        moduleName = Bundle.Jumplist_module_name();
        fileManager = currentCase.getServices().getFileManager();
        long ingestJobId = context.getJobId();

        String baseRaTempPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), JUMPLIST_DIR_NAME, ingestJobId);
        List<AbstractFile> jumpListFiles = extractJumplistFiles(dataSource, ingestJobId, baseRaTempPath);
        if (jumpListFiles.isEmpty()) {
            return;
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        List<AbstractFile> derivedFiles = new ArrayList<>();
        String derivedPath = null;
        String baseRaModPath = RAImageIngestModule.getRAOutputPath(Case.getCurrentCase(), JUMPLIST_DIR_NAME, ingestJobId);
        for (AbstractFile jumplistFile : jumpListFiles) {
            if (!jumplistFile.getName().toLowerCase().contains("-slack") && !jumplistFile.getName().equals("..")
                    && !jumplistFile.getName().equals(".") && jumplistFile.getSize() > 0) {
                String jlFile = Paths.get(baseRaTempPath, jumplistFile.getName() + "_" + jumplistFile.getId()).toString();
                String moduleOutPath = baseRaModPath + File.separator + jumplistFile.getName() + "_" + jumplistFile.getId();
                derivedPath = RA_DIR_NAME + File.separator + JUMPLIST_DIR_NAME + "_" + ingestJobId + File.separator + jumplistFile.getName() + "_" + jumplistFile.getId();
                File jlDir = new File(moduleOutPath);
                if (jlDir.exists() == false) {
                    boolean dirMade = jlDir.mkdirs();
                    if (!dirMade) {
                        logger.log(Level.WARNING, "Error creating directory to store Jumplist LNK files %s", moduleOutPath); //NON-NLS
                        continue;
                    }
                }
                derivedFiles.addAll(extractLnkFiles(jlFile, moduleOutPath, jumplistFile, derivedPath));
            }
        }

        // notify listeners of new files and schedule for analysis
        progressBar.progress(String.format(Bundle.Jumplist_adding_extracted_files_msg(), derivedFiles.size()));
        derivedFiles.forEach((derived) -> {
            services.fireModuleContentEvent(new ModuleContentEvent(derived));
        });
        context.addFilesToJob(derivedFiles);

    }

    /**
     * Find jumplist and extract jumplist files to temp directory
     *
     * @return - list of jumplist abstractfiles or empty list
     */
    private List<AbstractFile> extractJumplistFiles(Content dataSource, Long ingestJobId, String baseRaTempPath) {
        List<AbstractFile> jumpListFiles = new ArrayList<>();;
        List<AbstractFile> tempJumpListFiles = new ArrayList<>();;

        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        try {
            tempJumpListFiles = fileManager.findFiles(dataSource, "%", AUTOMATIC_DESTINATIONS_FILE_DIRECTORY); //NON-NLS
            if (!tempJumpListFiles.isEmpty()) {
                jumpListFiles.addAll(tempJumpListFiles);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find jumplist files.", ex); //NON-NLS
            return jumpListFiles;  // No need to continue
        }

        for (AbstractFile jumpListFile : jumpListFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return jumpListFiles;
            }

            if (!jumpListFile.getName().toLowerCase().contains("-slack") && !jumpListFile.getName().equals("..")
                    && !jumpListFile.getName().equals(".") && jumpListFile.getSize() > 0) {
                String fileName = jumpListFile.getName() + "_" + jumpListFile.getId();
                String jlFile = Paths.get(baseRaTempPath, fileName).toString();
                try {
                    ContentUtils.writeToFile(jumpListFile, new File(jlFile));
                } catch (IOException ex) {
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", fileName, jlFile), ex); //NON-NLS
                }
            }
        }

        return jumpListFiles;

    }

    /*
     * Read each jumplist file and extract the lnk files to moduleoutput
     */
    private List<DerivedFile> extractLnkFiles(String jumpListFile, String moduleOutPath, AbstractFile jumpListAbsFile, String derivedPath) {

        List<DerivedFile> derivedFiles = new ArrayList<>();
        DerivedFile derivedFile;
        String lnkFileName = "";

        try (POIFSFileSystem fs = new POIFSFileSystem(new File(jumpListFile))) {
            DirectoryEntry root = fs.getRoot();
            for (Entry entry : root) {
                if (entry instanceof DirectoryEntry) {
                    //If this data structure needed to recurse this is where it would do it but jumplists do not need to at this time
                    continue;
                } else if (entry instanceof DocumentEntry) {
                    String jmpListFileName = entry.getName();
                    int fileSize = ((DocumentEntry) entry).getSize();

                    if (fileSize > 0) {
                        try (DocumentInputStream stream = fs.createDocumentInputStream(jmpListFileName)) {
                            byte[] buffer = new byte[stream.available()];
                            stream.read(buffer);

                            JLnkParser lnkParser = new JLnkParser(fs.createDocumentInputStream(jmpListFileName), fileSize);
                            JLNK lnk = lnkParser.parse();
                            lnkFileName = lnk.getBestName() + ".lnk";
                            File targetFile = new File(moduleOutPath + File.separator + entry.getName() + "-" + lnkFileName);
                            String relativePath = Case.getCurrentCase().getModuleOutputDirectoryRelativePath();
                            String derivedFileName = Case.getCurrentCase().getModuleOutputDirectoryRelativePath() + File.separator + derivedPath + File.separator + entry.getName() + "-" + lnkFileName;
                            OutputStream outStream = new FileOutputStream(targetFile);
                            outStream.write(buffer);
                            outStream.close();
                            derivedFile = fileManager.addDerivedFile(lnkFileName, derivedFileName,
                                    fileSize,
                                    0,
                                    0,
                                    0,
                                    0, // TBD 
                                    true,
                                    jumpListAbsFile,
                                    "",
                                    moduleName,
                                    VERSION_NUMBER,
                                    "",
                                    TskData.EncodingType.NONE);
                            derivedFiles.add(derivedFile);

                        } catch (IOException | JLnkParserException ex) {
                            logger.log(Level.WARNING, String.format("No such document, or the Entry represented by documentName is not a DocumentEntry link file is %s", jumpListFile), ex); //NON-NLS
                        } catch (TskCoreException ex) {
                            logger.log(Level.WARNING, String.format("Error trying to add dervived file %s", lnkFileName), ex); //NON-NLS
                        } catch (IndexOutOfBoundsException ex) {
                            // There is some type of corruption within the file that cannot be handled, ignoring it and moving on to next file
                            // in the jumplist.
                            logger.log(Level.WARNING, String.format("Error parsing the the jumplist file %s", jumpListFile), ex); //NON-NLS                
                        }
                    }
                } else {
                    // currently, either an Entry is a DirectoryEntry or a DocumentEntry,
                    // but in the future, there may be other entry subinterfaces.
                    // The internal data structure certainly allows for a lot more entry types.
                    continue;
                }
            }
        } catch (NotOLE2FileException | EmptyFileException ex1) {
            logger.log(Level.WARNING, String.format("Error file not a valid OLE2 Document $s", jumpListFile)); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Error lnk parsing the file to get recent files $s", jumpListFile), ex); //NON-NLS
        }

        return derivedFiles;

    }

}
