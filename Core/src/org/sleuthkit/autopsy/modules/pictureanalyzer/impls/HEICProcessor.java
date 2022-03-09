/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.pictureanalyzer.impls;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.List;
import org.apache.commons.io.FileUtils;

import org.apache.commons.io.FilenameUtils;

import org.openide.util.lookup.ServiceProvider;

import org.sleuthkit.autopsy.modules.pictureanalyzer.spi.PictureProcessor;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Processes HEIC containers by extracting and converting all internal photos to
 * JPEGs, which are then added as derived files of the container.
 *
 * All of this work is serviced by ImageMagick, a third party executable.
 */
@ServiceProvider(service = PictureProcessor.class)
public class HEICProcessor implements PictureProcessor {

    private static final Logger logger = Logger.getLogger(HEICProcessor.class.getName());

    private static final String HEIC_MODULE_FOLDER = "HEIC";
    private final HeifJNI heifJNI;

    public HEICProcessor() {
        HeifJNI heifJNI;
        try {
            heifJNI = HeifJNI.getInstance();
        } catch (UnsatisfiedLinkError ex) {
            logger.log(Level.SEVERE, "libheif native dependencies not found. HEIC functionality will be automatically disabled.", ex);
            heifJNI = null;
        }
        this.heifJNI = heifJNI;
    }

    @Override
    public void process(IngestJobContext context, AbstractFile file) {
        try {
            if (heifJNI == null) {
                return;
            }

            if (context.fileIngestIsCancelled()) {
                return;
            }
            
            if (file == null || file.getId() <= 0) {
                return;
            }

            byte[] heifBytes;
            try (InputStream is = new ReadContentInputStream(file)) {
                heifBytes = new byte[is.available()];
                is.read(heifBytes);
            }

            if (heifBytes == null || heifBytes.length == 0) {
                return;
            }
            
            convertToJPEG(context, heifBytes, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "I/O error encountered during HEIC photo processing.", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add pictures as derived files.", ex);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No open case!", ex);
        }
    }

    /**
     * Create any sub directories within the module output folder.
     *
     * @param file The relevant heic/heif file.
     *
     * @return the parent folder path for any derived images.
     */
    private Path createModuleOutputFolder(AbstractFile file) throws IOException, NoCurrentCaseException {
        final String moduleOutputDirectory = Case.getCurrentCaseThrows().getModuleDirectory();

        Path moduleOutputFolder = Paths.get(moduleOutputDirectory,
                HEIC_MODULE_FOLDER,
                String.valueOf(file.getId()));

        if (!Files.exists(moduleOutputFolder)) {
            Files.createDirectories(moduleOutputFolder);
        }

        return moduleOutputFolder;
    }

    private void convertToJPEG(IngestJobContext context, byte[] heifBytes,
            AbstractFile heicFile) throws IOException, TskCoreException, NoCurrentCaseException {

        Path outputFolder = createModuleOutputFolder(heicFile);

        final String baseFileName = FilenameUtils.getBaseName(FileUtil.escapeFileName(heicFile.getName()));
        final Path outputFile = outputFolder.resolve(baseFileName + ".jpg");

        if (context.fileIngestIsCancelled()) {
            return;
        }

        try {
            this.heifJNI.convertToDisk(heifBytes, outputFile.toString());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            logger.log(Level.WARNING, MessageFormat.format("There was an error processing {0} (id: {1}).", heicFile.getName(), heicFile.getId()), ex);
            return;
        } catch (Throwable ex) {
            logger.log(Level.SEVERE, MessageFormat.format("A severe error occurred while processing {0} (id: {1}).", heicFile.getName(), heicFile.getId()), ex);
            return;
        }

        if (context.fileIngestIsCancelled()) {
            return;
        }

        final Path caseDirectory = Paths.get(Case.getCurrentCaseThrows().getCaseDirectory());
        
        List<File> files = (List<File>) FileUtils.listFiles(outputFolder.toFile(), new String[]{"jpg", "jpeg"}, true);
        for (File file : files) {
            if (context.fileIngestIsCancelled()) {
                return;
            }
            
            Path candidate = file.toPath();
            
            final BasicFileAttributes attrs = Files.readAttributes(candidate, BasicFileAttributes.class);
            final Path localCasePath = caseDirectory.relativize(candidate);

            final DerivedFile jpegFile = Case.getCurrentCaseThrows().getSleuthkitCase()
                    .addDerivedFile(candidate.getFileName().toString(),
                            localCasePath.toString(), attrs.size(), 0L,
                            attrs.creationTime().to(TimeUnit.SECONDS),
                            attrs.lastAccessTime().to(TimeUnit.SECONDS),
                            attrs.lastModifiedTime().to(TimeUnit.SECONDS),
                            attrs.isRegularFile(), heicFile, "",
                            "", "", "", TskData.EncodingType.NONE);

            context.addFilesToJob(Arrays.asList(jpegFile));
            IngestServices.getInstance().fireModuleContentEvent(new ModuleContentEvent(jpegFile));
        }
    }

    @Override
    public Set<String> mimeTypes() {
        return new HashSet<String>() {
            {
                add("image/heif");
                add("image/heic");
            }
        };
    }
}
