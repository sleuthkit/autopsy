/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;

/**
 * A data source processor that handles archive files. Implements the
 * DataSourceProcessor service provider interface to allow integration with the
 * add data source wizard. It also provides a run method overload to allow it to
 * be used independently of the wizard.
 */
@ServiceProviders(value={
    @ServiceProvider(service=DataSourceProcessor.class),
    @ServiceProvider(service=AutoIngestDataSourceProcessor.class)}
)
public class ArchiveExtractorDataSourceProcessor implements DataSourceProcessor, AutoIngestDataSourceProcessor {
    
    private final static String DATA_SOURCE_TYPE = NbBundle.getMessage(ArchiveExtractorDataSourceProcessor.class, "ArchiveExtractorDataSourceProcessor.dsType.text");

    private static GeneralFilter zipFilter;
    private static List<FileFilter> archiveFilters = new ArrayList<>();
    
    private static final String AUTO_INGEST_MODULE_OUTPUT_DIR = "AutoIngest";
    
    private final ArchiveFilePanel configPanel;
    private String deviceId;
    private String imagePath;
    private boolean setDataSourceOptionsCalled;
    
    /**
     * Constructs an archive data source processor that
     * implements the DataSourceProcessor service provider interface to allow
     * integration with the add data source wizard. It also provides a run
     * method overload to allow it to be used independently of the wizard.
     */
    public ArchiveExtractorDataSourceProcessor() {
        String[] extensions = ArchiveUtil.getSupportedArchiveTypes();
        zipFilter = new GeneralFilter(Arrays.asList(extensions), "");
        archiveFilters.add(zipFilter);
        configPanel = ArchiveFilePanel.createInstance(ArchiveExtractorDataSourceProcessor.class.getName(), archiveFilters);
    }
    
    @Override
    public int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        // check whether this is an archive
        if (isArchive(dataSourcePath)){
            // return "high confidence" value
            return 100;
        }
        return 0;
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) throws AutoIngestDataSourceProcessorException {
        if (isArchive(dataSourcePath)) {
            // extract the archive and pass the extracted folder as input
            Path extractedDataSourcePath = Paths.get("");
            try {
                Case currentCase = Case.getCurrentCase();
                extractedDataSourcePath = extractDataSource(Paths.get(currentCase.getModuleDirectory()), dataSourcePath);
            } catch (Exception ex) {
                throw new AutoIngestDataSourceProcessorException(NbBundle.getMessage(ArchiveExtractorDataSourceProcessor.class, "ArchiveExtractorDataSourceProcessor.process.exception.text"), ex);
            }
            run(deviceId, extractedDataSourcePath.toString(), progressMonitor, callBack);
        }
    }

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
        if (!setDataSourceOptionsCalled) {
            configPanel.storeSettings();
            deviceId = UUID.randomUUID().toString();
            imagePath = configPanel.getContentPaths();
        }
        run(deviceId, imagePath, progressMonitor, callback);
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
    public void run(String deviceId, String imageFolderPath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        //List<String> imageFilePaths = getImageFilePaths(imageFolderPath);
        //addImagesTask = new AddCellebritePhysicalReportTask(deviceId, imageFilePaths, timeZone, progressMonitor, callback);
        //new Thread(addImagesTask).start();
    }    

    @Override
    public void cancel() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void reset() {
        deviceId = null;
        imagePath = null;
        configPanel.reset();
        setDataSourceOptionsCalled = false;
    }

    private static boolean isArchive(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        String fileName = dataSourcePath.getFileName().toString();
        // check whether it's a zip archive file that can be extracted
        return isAcceptedByFiler(new File(fileName), archiveFilters);
    }    

    private static boolean isAcceptedByFiler(File file, List<FileFilter> filters) {
        for (FileFilter filter : filters) {
            if (filter.accept(file)) {
                return true;
            }
        }
        return false;
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
}
