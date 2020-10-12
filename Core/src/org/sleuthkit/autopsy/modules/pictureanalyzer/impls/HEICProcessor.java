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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;

import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.io.FilenameUtils;

import org.openide.modules.InstalledFileLocator;
import org.openide.util.lookup.ServiceProvider;

import org.sleuthkit.autopsy.modules.pictureanalyzer.spi.PictureProcessor;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModuleProcessTerminator;
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

    private static final int EXIT_SUCCESS = 0;
    private static final String HEIC_MODULE_FOLDER = "HEIC";
    private static final long TIMEOUT_IN_SEC = TimeUnit.SECONDS.convert(2, TimeUnit.MINUTES);

    // Windows location
    private static final String IMAGE_MAGICK_FOLDER = "ImageMagick-7.0.10-27-portable-Q16-x64";
    private static final String IMAGE_MAGICK_EXE = "magick.exe";
    private static final String IMAGE_MAGICK_ERROR_FILE = "magick_error.txt";

    // Actual path of ImageMagick on the system
    private final Path IMAGE_MAGICK_PATH;

    public HEICProcessor() {
        IMAGE_MAGICK_PATH = findImageMagick();

        if (IMAGE_MAGICK_PATH == null) {
            logger.log(Level.WARNING, "ImageMagick executable not found. "
                    + "HEIC functionality will be automatically disabled.");
        }
    }

    private Path findImageMagick() {
        final Path windowsLocation = Paths.get(IMAGE_MAGICK_FOLDER, IMAGE_MAGICK_EXE);
        final Path macAndLinuxLocation = Paths.get("/usr", "local", "bin", "magick");

        final String osName = PlatformUtil.getOSName().toLowerCase();

        if (PlatformUtil.isWindowsOS() && PlatformUtil.is64BitJVM()) {
            final File locatedExec = InstalledFileLocator.getDefault().locate(
                windowsLocation.toString(), HEICProcessor.class.getPackage().getName(), false);

            return (locatedExec != null) ? locatedExec.toPath() : null;        
        } else if ((osName.equals("linux") || osName.startsWith("mac")) && 
                    Files.isExecutable(macAndLinuxLocation) && 
                    !Files.isDirectory(macAndLinuxLocation)) {
            return macAndLinuxLocation;      
        } else {
            return null;
        }
    }

    /**
     * Give each file its own folder in module output. This makes scanning for
     * ImageMagick output fast.
     */
    private Path getModuleOutputFolder(AbstractFile file) throws NoCurrentCaseException {
        final String moduleOutputDirectory = Case.getCurrentCaseThrows().getModuleDirectory();

        return Paths.get(moduleOutputDirectory,
                HEIC_MODULE_FOLDER,
                String.valueOf(file.getId()));
    }

    /**
     * Create any sub directories within the module output folder.
     */
    private void createModuleOutputFolder(AbstractFile file) throws IOException, NoCurrentCaseException {
        final Path moduleOutputFolder = getModuleOutputFolder(file);

        if (!Files.exists(moduleOutputFolder)) {
            Files.createDirectories(moduleOutputFolder);
        }
    }

    @Override
    public void process(IngestJobContext context, AbstractFile file) {
        try {
            if (IMAGE_MAGICK_PATH == null) {
                return;
            }
            createModuleOutputFolder(file);

            if (context.fileIngestIsCancelled()) {
                return;
            }

            final Path localDiskCopy = extractToDisk(file);

            convertToJPEG(context, localDiskCopy, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "I/O error encountered during HEIC photo processing.", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add pictures as derived files.", ex);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No open case!", ex);
        }
    }

    /**
     * Copies the HEIC container to disk in order to run ImageMagick.
     */
    private Path extractToDisk(AbstractFile heicFile) throws IOException, NoCurrentCaseException {
        final String tempDir = Case.getCurrentCaseThrows().getTempDirectory();
        final String heicFileName = FileUtil.escapeFileName(heicFile.getName());

        final Path localDiskCopy = Paths.get(tempDir, heicFileName);

        try (BufferedInputStream heicInputStream = new BufferedInputStream(new ReadContentInputStream(heicFile))) {
            Files.copy(heicInputStream, localDiskCopy, StandardCopyOption.REPLACE_EXISTING);
            return localDiskCopy;
        }
    }

    private void convertToJPEG(IngestJobContext context, Path localDiskCopy,
            AbstractFile heicFile) throws IOException, TskCoreException, NoCurrentCaseException {

        // First step, run ImageMagick against this heic container.
        final Path moduleOutputFolder = getModuleOutputFolder(heicFile);

        final String baseFileName = FilenameUtils.getBaseName(FileUtil.escapeFileName(heicFile.getName()));
        final Path outputFile = moduleOutputFolder.resolve(baseFileName + ".jpg");
        
        final Path imageMagickErrorOutput = moduleOutputFolder.resolve(IMAGE_MAGICK_ERROR_FILE);
        Files.deleteIfExists(imageMagickErrorOutput);
        Files.createFile(imageMagickErrorOutput);

        // ImageMagick will write the primary image to the output file.
        // Any additional images found within the HEIC container will be
        // formatted as fileName-1.jpg, fileName-2.jpg, etc.
        final ProcessBuilder processBuilder = new ProcessBuilder()
                .command(IMAGE_MAGICK_PATH.toString(),
                        localDiskCopy.toString(),
                        outputFile.toString());
        
        processBuilder.redirectError(imageMagickErrorOutput.toFile());

        final int exitStatus = ExecUtil.execute(processBuilder, new FileIngestModuleProcessTerminator(context, TIMEOUT_IN_SEC));

        if (context.fileIngestIsCancelled()) {
            return;
        }
        
        if (exitStatus != EXIT_SUCCESS) {
            logger.log(Level.INFO, "Non-zero exit status for HEIC file [id: {0}]. Skipping...", heicFile.getId());
            return;
        }

        // Second step, visit all the output files and create derived files.
        // Glob for the pattern mentioned above.
        final String glob = String.format("{%1$s.jpg,%1$s-*.jpg}", baseFileName);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleOutputFolder, glob)) {

            final Path caseDirectory = Paths.get(Case.getCurrentCaseThrows().getCaseDirectory());
            for (Path candidate : stream) {
                if (context.fileIngestIsCancelled()) {
                    return;
                }

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

        } catch (DirectoryIteratorException ex) {
            throw ex.getCause();
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
