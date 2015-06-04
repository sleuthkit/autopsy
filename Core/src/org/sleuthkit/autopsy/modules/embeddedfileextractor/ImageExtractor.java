/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.poi.hslf.model.Picture;
import org.apache.poi.hslf.usermodel.PictureData;
import org.apache.poi.hslf.usermodel.SlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

class ImageExtractor {

    private final FileManager fileManager;
    private final IngestServices services;
    private static final Logger logger = Logger.getLogger(ImageExtractor.class.getName());
    private final IngestJobContext context;
    private String parentFileName;
    private final String UNKNOWN_NAME_PREFIX = "image_";
    private final FileTypeDetector fileTypeDetector;
    /**
     * Enum of mimetypes which support image extraction
     */
    enum SupportedImageExtractionFormats {

        DOC("application/msword"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PPT("application/vnd.ms-powerpoint"),
        PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        XLS("application/vnd.ms-excel"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String mimeType;

        SupportedImageExtractionFormats(final String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return this.mimeType;
        }
        // TODO Expand to support more formats
    }
    private SupportedImageExtractionFormats abstractFileExtractionFormat;

    ImageExtractor(IngestJobContext context, FileTypeDetector fileTypeDetector) {

        this.fileManager = Case.getCurrentCase().getServices().getFileManager();
        this.services = IngestServices.getInstance();
        this.context = context;
        this.fileTypeDetector = fileTypeDetector;
    }
    
    /**
     * This method returns true if the file format is currently supported. Else
     * it returns false. Performs only Apache Tika based detection.
     *
     * @param abstractFile The AbstractFilw whose mimetype is to be determined.
     * @return This method returns true if the file format is currently
     * supported. Else it returns false.
     */
    boolean isImageExtractionSupported(AbstractFile abstractFile) {
        try {
            String abstractFileMimeType = fileTypeDetector.getFileType(abstractFile);
            for (SupportedImageExtractionFormats s : SupportedImageExtractionFormats.values()) {
                if (s.toString().equals(abstractFileMimeType)) {
                    abstractFileExtractionFormat = s;
                    return true;
                }
            }
            return false;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error executing FileTypeDetector.getFileType()", ex); // NON-NLS
            return false;
        }
    }

    /**
     * This method selects the appropriate process of extracting images from
     * files using POI classes. Once the images have been extracted, the method
     * adds them to the DB and fires a ModuleContentEvent. ModuleContent Event
     * is not fired if the no images were extracted from the processed file.
     *
     * @param format
     * @param abstractFile The abstract file to be processed.
     */
    protected void extractImage(AbstractFile abstractFile) {
        // 
        // switchcase for different supported formats
        // process abstractFile according to the format by calling appropriate methods.

        List<ExtractedImage> listOfExtractedImages = null;
        List<AbstractFile> listOfExtractedImageAbstractFiles = null;
        this.parentFileName = EmbeddedFileExtractorIngestModule.getUniqueName(abstractFile);

        switch (abstractFileExtractionFormat) {
            case DOC:
                listOfExtractedImages = extractImagesFromDoc(abstractFile);
                break;
            case DOCX:
                listOfExtractedImages = extractImagesFromDocx(abstractFile);
                break;
            case PPT:
                listOfExtractedImages = extractImagesFromPpt(abstractFile);
                break;
            case PPTX:
                listOfExtractedImages = extractImagesFromPptx(abstractFile);
                break;
            case XLS:
                listOfExtractedImages = extractImagesFromXls(abstractFile);
                break;
            case XLSX:
                listOfExtractedImages = extractImagesFromXlsx(abstractFile);
                break;
            default:
                break;
        }

        if (listOfExtractedImages == null) {
            return;
        }
        // the common task of adding abstractFile to derivedfiles is performed.
        listOfExtractedImageAbstractFiles = new ArrayList<>();
        for (ExtractedImage extractedImage : listOfExtractedImages) {
            try {
                listOfExtractedImageAbstractFiles.add(fileManager.addDerivedFile(extractedImage.getFileName(), extractedImage.getLocalPath(), extractedImage.getSize(),
                        extractedImage.getCtime(), extractedImage.getCrtime(), extractedImage.getAtime(), extractedImage.getAtime(),
                        true, abstractFile, null, EmbeddedFileExtractorModuleFactory.getModuleName(), null, null));
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.extractImage.addToDB.exception.msg"), ex); //NON-NLS
            }
        }
        if (!listOfExtractedImages.isEmpty()) {
            services.fireModuleContentEvent(new ModuleContentEvent(abstractFile));
            context.addFilesToJob(listOfExtractedImageAbstractFiles);
        }
    }

    /**
     * Extract images from doc format files.
     *
     * @param af the file from which images are to be extracted.
     * @return list of extracted images. Returns null in case no images were
     * extracted.
     */
    private List<ExtractedImage> extractImagesFromDoc(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages;
        HWPFDocument doc = null;
        try {
            doc = new HWPFDocument(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.docContainer.init.err", af.getName()), ex); //NON-NLS
            return null;
        }
        PicturesTable pictureTable = doc.getPicturesTable();
        List<org.apache.poi.hwpf.usermodel.Picture> listOfAllPictures = pictureTable.getAllPictures();
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            return null;
        }
        listOfExtractedImages = new ArrayList<>();
        for (org.apache.poi.hwpf.usermodel.Picture picture : listOfAllPictures) {
            String fileName = picture.suggestFullFileName();
            writeExtractedImage(Paths.get(outputFolderPath, fileName).toString(), picture.getContent());
            // TODO Extract more info from the Picture viz ctime, crtime, atime, mtime
            listOfExtractedImages.add(new ExtractedImage(fileName, getFileRelativePath(fileName), picture.getSize(), af));
        }

        return listOfExtractedImages;
    }

    /**
     * Extract images from docx format files.
     *
     * @param af the file from which images are to be extracted.
     * @return list of extracted images. Returns null in case no images were
     * extracted.
     */
    private List<ExtractedImage> extractImagesFromDocx(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages;
        XWPFDocument docx = null;
        try {
            docx = new XWPFDocument(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.docxContainer.init.err", af.getName()), ex); //NON-NLS
            return null;
        }
        List<XWPFPictureData> listOfAllPictures = docx.getAllPictures();

        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.extractImageFrom.outputPath.exception.msg", af.getName()));
            return null;
        }
        listOfExtractedImages = new ArrayList<>();
        for (XWPFPictureData xwpfPicture : listOfAllPictures) {
            String fileName = xwpfPicture.getFileName();
            writeExtractedImage(Paths.get(outputFolderPath, fileName).toString(), xwpfPicture.getData());
            listOfExtractedImages.add(new ExtractedImage(fileName, getFileRelativePath(fileName), xwpfPicture.getData().length, af));
        }
        return listOfExtractedImages;
    }

    /**
     * Extract images from ppt format files.
     *
     * @param af the file from which images are to be extracted.
     * @return list of extracted images. Returns null in case no images were
     * extracted.
     */
    private List<ExtractedImage> extractImagesFromPpt(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages;
        SlideShow ppt = null;
        try {
            ppt = new SlideShow(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.pptContainer.init.err", af.getName()), ex); //NON-NLS
            return null;
        }

        //extract all pictures contained in the presentation
        PictureData[] listOfAllPictures = ppt.getPictureData();

        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.length == 0) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.extractImageFrom.outputPath.exception.msg", af.getName()));
            return null;
        }

        // extract the images to the above initialized outputFolder.
        // extraction path - outputFolder/image_number.ext
        int i = 0;
        listOfExtractedImages = new ArrayList<>();
        for (PictureData pictureData : listOfAllPictures) {

            // Get image extension, generate image name, write image to the module
            // output folder, add it to the listOfExtractedImageAbstractFiles
            int type = pictureData.getType();
            String ext;
            switch (type) {
                case Picture.JPEG:
                    ext = ".jpg"; //NON-NLS
                    break;
                case Picture.PNG:
                    ext = ".png"; //NON-NLS
                    break;
                case Picture.WMF:
                    ext = ".wmf"; //NON-NLS
                    break;
                case Picture.EMF:
                    ext = ".emf"; //NON-NLS
                    break;
                case Picture.PICT:
                    ext = ".pict"; //NON-NLS
                    break;
                default:
                    continue;
            }
            String imageName = UNKNOWN_NAME_PREFIX + i + ext; //NON-NLS
            writeExtractedImage(Paths.get(outputFolderPath, imageName).toString(), pictureData.getData());
            listOfExtractedImages.add(new ExtractedImage(imageName, getFileRelativePath(imageName), pictureData.getData().length, af));
            i++;
        }
        return listOfExtractedImages;
    }

    /**
     * Extract images from pptx format files.
     *
     * @param af the file from which images are to be extracted.
     * @return list of extracted images. Returns null in case no images were
     * extracted.
     */
    private List<ExtractedImage> extractImagesFromPptx(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages;
        XMLSlideShow pptx;
        try {
            pptx = new XMLSlideShow(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.pptxContainer.init.err", af.getName()), ex); //NON-NLS
            return null;
        }
        List<XSLFPictureData> listOfAllPictures = pptx.getAllPictures();

        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.extractImageFrom.outputPath.exception.msg", af.getName()));
            return null;
        }

        listOfExtractedImages = new ArrayList<>();
        for (XSLFPictureData xslsPicture : listOfAllPictures) {

            // get image file name, write it to the module outputFolder, and add
            // it to the listOfExtractedImageAbstractFiles.
            String fileName = xslsPicture.getFileName();
            writeExtractedImage(Paths.get(outputFolderPath, fileName).toString(), xslsPicture.getData());
            listOfExtractedImages.add(new ExtractedImage(fileName, getFileRelativePath(fileName), xslsPicture.getData().length, af));

        }

        return listOfExtractedImages;

    }

    /**
     * Extract images from xls format files.
     *
     * @param af the file from which images are to be extracted.
     * @return list of extracted images. Returns null in case no images were
     * extracted.
     */
    private List<ExtractedImage> extractImagesFromXls(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages;

        Workbook xls;
        try {
            xls = new HSSFWorkbook(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.xlsContainer.init.err", af.getName()) + af.getName(), ex); //NON-NLS
            return null;
        }

        List<? extends org.apache.poi.ss.usermodel.PictureData> listOfAllPictures = xls.getAllPictures();
        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.extractImageFrom.outputPath.exception.msg", af.getName()));
            return null;
        }

        int i = 0;
        listOfExtractedImages = new ArrayList<>();
        for (org.apache.poi.ss.usermodel.PictureData pictureData : listOfAllPictures) {
            String imageName = UNKNOWN_NAME_PREFIX + i + "." + pictureData.suggestFileExtension(); //NON-NLS
            writeExtractedImage(Paths.get(outputFolderPath, imageName).toString(), pictureData.getData());
            listOfExtractedImages.add(new ExtractedImage(imageName, getFileRelativePath(imageName), pictureData.getData().length, af));
            i++;
        }
        return listOfExtractedImages;

    }

    /**
     * Extract images from xlsx format files.
     *
     * @param af the file from which images are to be extracted.
     * @return list of extracted images. Returns null in case no images were
     * extracted.
     */
    private List<ExtractedImage> extractImagesFromXlsx(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages;
        Workbook xlsx;
        try {
            xlsx = new XSSFWorkbook(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.xlsxContainer.init.err", af.getName()), ex); //NON-NLS
            return null;
        }

        List<? extends org.apache.poi.ss.usermodel.PictureData> listOfAllPictures = xlsx.getAllPictures();
        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.extractImageFrom.outputPath.exception.msg", af.getName()));
            return null;
        }

        int i = 0;
        listOfExtractedImages = new ArrayList<>();
        for (org.apache.poi.ss.usermodel.PictureData pictureData : listOfAllPictures) {
            String imageName = UNKNOWN_NAME_PREFIX + i + "." + pictureData.suggestFileExtension();
            writeExtractedImage(Paths.get(outputFolderPath, imageName).toString(), pictureData.getData());
            listOfExtractedImages.add(new ExtractedImage(imageName, getFileRelativePath(imageName), pictureData.getData().length, af));
            i++;
        }
        return listOfExtractedImages;

    }

    /**
     * Writes image to the module output location.
     *
     * @param outputPath Path where images is written
     * @param data byte representation of the data to be written to the
     * specified location.
     */
    private void writeExtractedImage(String outputPath, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(data);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not write to the provided location: " + outputPath, ex); //NON-NLS
        }
    }

    /**
     * Gets path to the output folder for image extraction. If the path does not
     * exist, it is created.
     *
     * @param parentFileName name of the abstract file being processed for image
     * extraction.
     * @return path to the image extraction folder for a given abstract file.
     */
    private String getOutputFolderPath(String parentFileName) {
        String outputFolderPath = EmbeddedFileExtractorIngestModule.moduleDirAbsolute + File.separator + parentFileName;
        File outputFilePath = new File(outputFolderPath);
        if (!outputFilePath.exists()) {
            try {
                outputFilePath.mkdirs();
            } catch (SecurityException ex) {
                logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.getOutputFolderPath.exception.msg", parentFileName), ex);
                return null;
            }
        }
        return outputFolderPath;
    }

    /**
     * Gets the relative path to the file. The path is relative to the case
     * folder.
     *
     * @param fileName name of the the file for which the path is to be
     * generated.
     * @return
     */
    private String getFileRelativePath(String fileName) {
        // Used explicit FWD slashes to maintain DB consistency across operating systems.
        return "/" + EmbeddedFileExtractorIngestModule.moduleDirRelative + "/" + this.parentFileName + "/" + fileName; //NON-NLS
    }

    /**
     * Represents the image extracted using POI methods. Currently, POI is not
     * capable of extracting ctime, crtime, mtime, and atime; these values are
     * set to 0.
     */
    private static class ExtractedImage {
        //String fileName, String localPath, long size, long ctime, long crtime, 
        //long atime, long mtime, boolean isFile, AbstractFile parentFile, String rederiveDetails, String toolName, String toolVersion, String otherDetails

        private final String fileName;
        private final String localPath;
        private final long size;
        private final long ctime;
        private final long crtime;
        private final long atime;
        private final long mtime;
        private final AbstractFile parentFile;

        ExtractedImage(String fileName, String localPath, long size, AbstractFile parentFile) {
            this(fileName, localPath, size, 0, 0, 0, 0, parentFile);
        }

        ExtractedImage(String fileName, String localPath, long size, long ctime, long crtime, long atime, long mtime, AbstractFile parentFile) {
            this.fileName = fileName;
            this.localPath = localPath;
            this.size = size;
            this.ctime = ctime;
            this.crtime = crtime;
            this.atime = atime;
            this.mtime = mtime;
            this.parentFile = parentFile;
        }

        public String getFileName() {
            return fileName;
        }

        public String getLocalPath() {
            return localPath;
        }

        public long getSize() {
            return size;
        }

        public long getCtime() {
            return ctime;
        }

        public long getCrtime() {
            return crtime;
        }

        public long getAtime() {
            return atime;
        }

        public long getMtime() {
            return mtime;
        }

        public AbstractFile getParentFile() {
            return parentFile;
        }
    }
}