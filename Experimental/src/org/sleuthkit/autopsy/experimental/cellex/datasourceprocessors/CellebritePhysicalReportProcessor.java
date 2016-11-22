/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.cellex.datasourceprocessors;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutomatedIngestDataSourceProcessor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

/**
 * An Cellebrite UFED output folder data source processor that implements the
 * DataSourceProcessor service provider interface to allow integration with the
 * add data source wizard. It also provides a run method overload to allow it to
 * be used independently of the wizard.
 */
@ServiceProviders(value={
    @ServiceProvider(service=DataSourceProcessor.class),
    @ServiceProvider(service=AutomatedIngestDataSourceProcessor.class)}
)
public class CellebritePhysicalReportProcessor implements AutomatedIngestDataSourceProcessor {

    private static final String DATA_SOURCE_TYPE = "Cellebrite Physical Report";
    private final CellebritePhysicalReportInputPanel configPanel;
    private AddCellebritePhysicalReportTask addImagesTask;
    
    private static final List<String> CELLEBRITE_EXTS = Arrays.asList(new String[]{".bin"});
    private static final String CELLEBRITE_DESC = "Cellebrite Physical Files (*.bin)";
    private static final GeneralFilter binFileFilter = new GeneralFilter(CELLEBRITE_EXTS, CELLEBRITE_DESC);
    private static final List<FileFilter> filtersList = new ArrayList<>();
    static {
        filtersList.add(binFileFilter);
    }

    private static final GeneralFilter zipFilter = new GeneralFilter(Arrays.asList(new String[]{".zip"}), "");
    private static final List<FileFilter> archiveFilters = new ArrayList<>();
    static {
        archiveFilters.add(zipFilter);
    }
    
    private static final String AUTO_INGEST_MODULE_OUTPUT_DIR = "AutoIngest";

    /**
     * Constructs a Cellebrite UFED output folder data source processor that
     * implements the DataSourceProcessor service provider interface to allow
     * integration with the add data source wizard. It also provides a run
     * method overload to allow it to be used independently of the wizard.
     */
    public CellebritePhysicalReportProcessor() {
        configPanel = CellebritePhysicalReportInputPanel.createInstance(CellebritePhysicalReportProcessor.class.getName());
    }

/**
     * Gets the file extensions supported by this data source processor as a
     * list of file filters.
     *
     * @return List<FileFilter> List of FileFilter objects
     */
    public static final List<FileFilter> getFileFilterList() {
        return filtersList;
    }     
    
    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    @Override
    public String getDataSourceType() {
        return DATA_SOURCE_TYPE;
    }

    /**
     * Gets the panel that allows a user to select a data source and do any
     * configuration required by the data source. The panel is less than 544
     * pixels wide and less than 173 pixels high.
     *
     * @return A selection and configuration panel for this data source
     *         processor.
     */
    @Override
    public JPanel getPanel() {
        configPanel.readSettings();
        configPanel.select();
        return configPanel;
    }

    /**
     * Indicates whether the settings in the selection and configuration panel
     * are valid and complete.
     *
     * @return True if the settings are valid and complete and the processor is
     *         ready to have its run method called, false otherwise.
     */
    @Override
    public boolean isPanelValid() {
        return configPanel.validatePanel();
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the settings provided by the selection and
     * configuration panel. Returns as soon as the background task is started.
     * The background task uses a callback object to signal task completion and
     * return results.
     *
     * This method should not be called unless isPanelValid returns true.
     *
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    @Override
    public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        configPanel.storeSettings();
        run(UUID.randomUUID().toString(), configPanel.getContentPaths(), configPanel.getTimeZone(), progressMonitor, callback);
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the given settings instead of those provided by the
     * selection and configuration panel. Returns as soon as the background task
     * is started and uses the callback object to signal task completion and
     * return results.
     *
     * This method should not be called unless isPanelValid returns true.
     *
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    public void run(String deviceId, String imageFolderPath, String timeZone, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        List<String> imageFilePaths = getImageFilePaths(imageFolderPath);
        addImagesTask = new AddCellebritePhysicalReportTask(deviceId, imageFilePaths, timeZone, progressMonitor, callback);
        new Thread(addImagesTask).start();
    }

    /**
     * Requests cancellation of the background task that adds a data source to
     * the case database, after the task is started using the run method. This
     * is a "best effort" cancellation, with no guarantees that the case
     * database will be unchanged. If cancellation succeeded, the list of new
     * data sources returned by the background task will be empty.
     */
    @Override
    public void cancel() {
        if (null != addImagesTask) {
            addImagesTask.cancelTask();
        }
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        configPanel.reset();
    }

    /**
     * Gets the paths of the image files in a Cellebrite UFED output folder.
     *
     * @param folderPath The path to a Cellebrite UFED output folder
     *
     * @return A list of image file paths.
     */
    private static List<String> getImageFilePaths(String folderPath) {
        List<String> imageFilePaths = new ArrayList<>();
        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile() && isValidDataSource(file.toPath())){
                Path filePathName = Paths.get(folderPath, file.getName());
                imageFilePaths.add(filePathName.toString());
            }
        }
        return imageFilePaths;
    }

    /**
     * Extracts the contents of a ZIP archive submitted as a data source to a
     * subdirectory of the auto ingest module output directory.
     *
     * @throws IOException if there is a problem extracting the data source from
     *                     the archive.
     */
    private static Path extractDataSource(Path outputDirectoryPath, Path dataSourcePath) throws IOException {
        String dataSourceFileNameNoExt = FilenameUtils.removeExtension(dataSourcePath.getFileName().toString());
        Path destinationFolder = Paths.get(outputDirectoryPath.toString(),
                AUTO_INGEST_MODULE_OUTPUT_DIR,
                dataSourceFileNameNoExt + "_" + TimeStampUtils.createTimeStamp());
        Files.createDirectories(destinationFolder);

        int BUFFER_SIZE = 524288; // Read/write 500KB at a time
        File sourceZipFile = dataSourcePath.toFile();
        ZipFile zipFile;
        zipFile = new ZipFile(sourceZipFile, ZipFile.OPEN_READ);
        Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
        try {
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry entry = zipFileEntries.nextElement();
                String currentEntry = entry.getName();
                File destFile = new File(destinationFolder.toString(), currentEntry);
                destFile = new File(destinationFolder.toString(), destFile.getName());
                File destinationParent = destFile.getParentFile();
                destinationParent.mkdirs();
                if (!entry.isDirectory()) {
                    BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
                    int currentByte;
                    byte data[] = new byte[BUFFER_SIZE];
                    try (FileOutputStream fos = new FileOutputStream(destFile); BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE)) {
                        currentByte = is.read(data, 0, BUFFER_SIZE);
                        while (currentByte != -1) {
                            dest.write(data, 0, currentByte);
                            currentByte = is.read(data, 0, BUFFER_SIZE);
                        }
                    }
                }
            }
        } finally {
            zipFile.close();
        }
        return destinationFolder;
    }
   
    private static boolean isValidDataSource(Path dataSourcePath) {        
                
        String fileName = dataSourcePath.getFileName().toString();        
        // is it a ".bin" image
        if (!isAcceptedByFiler(new File(fileName), filtersList)) {
            return false;
        }

        // this needs to identify and handle different Cellebrite scenarios:
        //  i  single image in a single file
        // ii. Single image split over multiple files - just need to pass the first to TSK and it will combine the split image files.
        //       Note there may be more than  than one split images in a single dir, 
        //       e.g. blk0_mmcblk0.bin, blk0_mmcblk0(1).bin......, and blk24_mmcblk1.bin, blk24_mmcblk1(1).bin......
        //iii. Multiple image files - one per volume - need to handle each one separately
        //       e.g. blk0_mmcblk0.bin, mtd0_system.bin, mtd1_cache.bin, mtd2_userdata.bin
        String fNameNoExt = FilenameUtils.removeExtension(fileName);
        return (! fNameNoExt.toLowerCase().matches("\\w+\\(\\d+\\)"));
    }
    
    private static boolean isAcceptedByFiler(File file, List<FileFilter> filters) {
        for (FileFilter filter : filters) {
            if (filter.accept(file)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isArchive(Path dataSourcePath) throws AutomatedIngestDataSourceProcessorException {
        String fileName = dataSourcePath.getFileName().toString();
        // check whether it's a zip archive file that can be extracted
        if (isAcceptedByFiler(new File(fileName), archiveFilters)) {
            return true;
        }
        return false;
    }

    @Override
    public int canProcess(Path dataSourcePath) throws AutomatedIngestDataSourceProcessorException {      
        // check whether this is an archive or a ".bin" file
        if (isArchive(dataSourcePath) || isValidDataSource(dataSourcePath)) {
            // return "high confidence" value
            return 90;
        }
        return 0;
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) throws AutomatedIngestDataSourceProcessorException {
        
        List<String> dataSourcePathList = Collections.emptyList();
        if (isArchive(dataSourcePath)) {
            // extract the archive and pass the extracted folder as input
            Path extractedDataSourcePath = Paths.get("");
            try {
                Case currentCase = Case.getCurrentCase();
                extractedDataSourcePath = extractDataSource(Paths.get(currentCase.getModuleDirectory()), dataSourcePath);
            } catch (Exception ex) {
                throw new AutomatedIngestDataSourceProcessorException(NbBundle.getMessage(CellebritePhysicalReportProcessor.class, "CellebritePhysicalReportProcessor.process.exception.text"), ex);
            }
            run(deviceId, extractedDataSourcePath.toString(), "", progressMonitor, callBack);
        } else if (isValidDataSource(dataSourcePath)) {
            // pass the single ".bin" file as input
            dataSourcePathList = Arrays.asList(new String[]{dataSourcePath.toString()});
            // in this particular case we don't want to call run() method as it will try to identify and process all ".bin" files in data source folder
            addImagesTask = new AddCellebritePhysicalReportTask(deviceId, dataSourcePathList, "", progressMonitor, callBack);
            new Thread(addImagesTask).start();
        }
    }
}
