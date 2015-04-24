/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

public class ImageExtractor {

    private Case currentCase;
    private FileManager fileManager;
    private IngestServices services;
    private String moduleDirRelative; //relative to the case, to store in db
    private String moduleDirAbsolute; //absolute, to extract to
    private static final Logger logger = Logger.getLogger(ImageExtractor.class.getName());
    private IngestJobContext context;
    protected enum SupportedFormats {

        doc {
            @Override
            public String toString() {
            return "application/msword";} //NON-NLS
        },
        docx {
            @Override
            public String toString() {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";} //NON-NLS
        },
        ppt {
            @Override
            public String toString() {
            return "application/vnd.ms-powerpoint";} //NON-NLS
        },
        pptx {
            @Override
            public String toString() {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";} //NON-NLS
        },
        xls {
            @Override
            public String toString() {
            return "application/vnd.ms-excel";} //NON-NLS
        },
        xlsx {
            @Override
            public String toString() {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";} //NON-NLS
        };
        // TODO Expand to support more formats
    }

    ImageExtractor(IngestJobContext context) {

        this.currentCase = Case.getCurrentCase();
        this.fileManager = Case.getCurrentCase().getServices().getFileManager();
        this.services = IngestServices.getInstance();
        this.context = context;
        this.moduleDirRelative = Case.getModulesOutputDirRelPath() + File.separator + KeywordSearchModuleFactory.getModuleName();
        this.moduleDirAbsolute = currentCase.getModulesOutputDirAbsPath() + File.separator + KeywordSearchModuleFactory.getModuleName();
        File extractionDirectory = new File(moduleDirAbsolute);
        if (!extractionDirectory.exists()) {
            try {
                extractionDirectory.mkdirs();
            } catch (SecurityException ex) {
                logger.log(Level.SEVERE, "Error initializing output dir: " + moduleDirAbsolute, ex); //NON-NLS
                services.postMessage(IngestMessage.createErrorMessage(KeywordSearchModuleFactory.getModuleName(), "Error initializing", "Error initializing output dir: " + moduleDirAbsolute)); //NON-NLS
                throw new RuntimeException(ex);
            }
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
    protected void extractImage(SupportedFormats format, AbstractFile abstractFile){
        // 
        // switchcase for different supported formats
        // process abstractFile according to the format by calling appropriate methods.

        List<ExtractedImage> extractedImages = null;
        List<AbstractFile> listOfExtractedImages = null;

        switch (format) {
            case doc:
                extractedImages = extractImagesFromDoc(abstractFile);
                break;
            case docx:
                extractedImages = extractImagesFromDocx(abstractFile);
                break;
            case ppt:
                extractedImages = extractImagesFromPpt(abstractFile);
                break;
            case pptx:
                extractedImages = extractImagesFromPptx(abstractFile);
                break;
            case xls:
                extractedImages = extractImagesFromXls(abstractFile);
                break;
            case xlsx:
                extractedImages = extractImagesFromXlsx(abstractFile);
                break;
            default:
                break;
        }
        

        if (extractedImages == null) {
            return;
        }
        // the common task of adding abstractFile to derivedfiles is performed.
        listOfExtractedImages = new ArrayList<>();
        for (ExtractedImage extractedImage : extractedImages) {
            try {
                listOfExtractedImages.add(fileManager.addDerivedFile(extractedImage.getFileName(), extractedImage.getLocalPath(), extractedImage.getSize(),
                        extractedImage.getCtime(), extractedImage.getCrtime(), extractedImage.getAtime(), extractedImage.getAtime(),
                        true, abstractFile, null, KeywordSearchModuleFactory.getModuleName(), null, null));
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error adding derived files to the DB", ex); //NON-NLS
            }
        }
        if (!extractedImages.isEmpty()) {
            services.fireModuleContentEvent(new ModuleContentEvent(abstractFile));
            context.addFilesToJob(listOfExtractedImages);
        }
    }

    private List<ExtractedImage> extractImagesFromDoc(AbstractFile af) {
        // TODO check for BBArtifact ENCRYPTION_DETECTED? Might be detected elsewhere...?
        List<ExtractedImage> listOfExtractedImages = new ArrayList<>();
        String parentFileName = getUniqueName(af);
        HWPFDocument docA = null;
        try {
            docA = new HWPFDocument(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "HWPFDocument container could not be instantiated while reading " + af.getName(), ex); //NON-NLS
            return null;
        }
        PicturesTable pictureTable = docA.getPicturesTable();
        List<org.apache.poi.hwpf.usermodel.Picture> listOfAllPictures = pictureTable.getAllPictures();
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, "Could not get path for image extraction from AbstractFile: {0}", af.getName()); //NON-NLS
            return null;
        }
        for (org.apache.poi.hwpf.usermodel.Picture picture : listOfAllPictures) {
            FileOutputStream fos = null;
            String fileName = picture.suggestFullFileName();
            try {
                fos = new FileOutputStream(outputFolderPath + File.separator + fileName);
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Invalid path provided for image extraction", ex); //NON-NLS
                continue;
            }
            try {
                fos.write(picture.getContent());
                fos.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write to the provided location", ex); //NON-NLS
                continue;
            }
            // TODO Extract more info from the Picture viz ctime, crtime, atime, mtime
            String fileRelativePath = File.separator + moduleDirRelative + File.separator + parentFileName + File.separator + fileName;
            long size = picture.getSize();
            ExtractedImage extractedimage = new ExtractedImage(fileName, fileRelativePath, size, af);
            listOfExtractedImages.add(extractedimage);
        }

        return listOfExtractedImages;
    }

    private List<ExtractedImage> extractImagesFromDocx(AbstractFile af) {
        // check for BBArtifact ENCRYPTION_DETECTED? Might be detected elsewhere...?
        // TODO check for BBArtifact ENCRYPTION_DETECTED? Might be detected elsewhere...?
        List<ExtractedImage> listOfExtractedImages = new ArrayList<>();
        String parentFileName = getUniqueName(af);
        XWPFDocument docxA = null;
        try {
            docxA = new XWPFDocument(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "XWPFDocument container could not be instantiated while reading " + af.getName(), ex); //NON-NLS
            return null;
        }
        List<XWPFPictureData> listOfAllPictures = docxA.getAllPictures();

        // if no images are extracted from the ppt, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, "Could not get path for image extraction from AbstractFile: {0}", af.getName()); //NON-NLS
            return null;
        }
        for (XWPFPictureData xwpfPicture : listOfAllPictures) {
            String fileName = xwpfPicture.getFileName();
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputFolderPath + File.separator + fileName);
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Invalid path provided for image extraction", ex); //NON-NLS
                continue;
            }
            try {
                fos.write(xwpfPicture.getData());
                fos.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write to the provided location", ex); //NON-NLS
                continue;
            }
            String fileRelativePath = File.separator + moduleDirRelative + File.separator + parentFileName + File.separator + fileName;
            long size = xwpfPicture.getData().length;
            ExtractedImage extractedimage = new ExtractedImage(fileName, fileRelativePath, size, af);
            listOfExtractedImages.add(extractedimage);
        }
        return listOfExtractedImages;
    }

    private List<ExtractedImage> extractImagesFromPpt(AbstractFile af) {
        // check for BBArtifact ENCRYPTION_DETECTED? Might be detected elsewhere...?
        // TODO check for BBArtifact ENCRYPTION_DETECTED? Might be detected elsewhere...?
        List<ExtractedImage> listOfExtractedImages = new ArrayList<>();
        String parentFileName = getUniqueName(af);
        SlideShow ppt = null;
        try {
            ppt = new SlideShow(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "SlideShow container could not be instantiated while reading " + af.getName(), ex); //NON-NLS
            return null;
        }

        //extract all pictures contained in the presentation
        PictureData[] listOfAllPictures = ppt.getPictureData();

        // if no images are extracted from the ppt, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.length == 0) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, "Could not get path for image extraction from AbstractFile: {0}", af.getName()); //NON-NLS
            return null;
        }

        // extract the images to the above initialized outputFolder.
        // extraction path - outputFolder/image_number.ext
        int i = 0;
        for (PictureData pictureData : listOfAllPictures) {

            // Get image extension, generate image name, write image to the module
            // output folder, add it to the listOfExtractedImages
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
            String imageName = "image_" + i + ext; //NON-NLS

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputFolderPath + File.separator + imageName);
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Invalid path provided for image extraction", ex); //NON-NLS
                continue;
            }
            try {
                fos.write(pictureData.getData());
                fos.close();
                i++;
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write to the provided location", ex); //NON-NLS
                continue;
            }

            String fileRelativePath = File.separator + moduleDirRelative + File.separator + parentFileName + File.separator + imageName;
            long size = pictureData.getData().length;
            ExtractedImage extractedimage = new ExtractedImage(imageName, fileRelativePath, size, af);
            listOfExtractedImages.add(extractedimage);
        }
        return listOfExtractedImages;
    }

    private List<ExtractedImage> extractImagesFromPptx(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages = new ArrayList<>();
        String parentFileName = getUniqueName(af);
        XMLSlideShow pptx;
        try {
            pptx = new XMLSlideShow(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "SlideShow container could not be instantiated while reading " + af.getName(), ex); //NON-NLS
            return null;
        }
        List<XSLFPictureData> listOfAllPictures = pptx.getAllPictures();

        // if no images are extracted from the ppt, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, "Could not get path for image extraction from AbstractFile: {0}", af.getName()); //NON-NLS
            return null;
        }

        for (XSLFPictureData xslsPicture : listOfAllPictures) {

            // get image file name, write it to the module outputFolder, and add
            // it to the listOfExtractedImages.
            String fileName = xslsPicture.getFileName();
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputFolderPath + File.separator + fileName);
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Invalid path provided for image extraction", ex); //NON-NLS
                continue;
            }
            try {
                fos.write(xslsPicture.getData());
                fos.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write to the provided location", ex); //NON-NLS
                continue;
            }

            String fileRelativePath = File.separator + moduleDirRelative + File.separator + parentFileName + File.separator + fileName;
            long size = xslsPicture.getData().length;
            ExtractedImage extractedimage = new ExtractedImage(fileName, fileRelativePath, size, af);
            listOfExtractedImages.add(extractedimage);

        }

        return listOfExtractedImages;

    }

    private List<ExtractedImage> extractImagesFromXls(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages = new ArrayList<>();
        String parentFileName = getUniqueName(af);

        Workbook workbook;
        try {
            workbook = new HSSFWorkbook(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "HSSFWorkbook container could not be instantiated while reading " + af.getName(), ex); //NON-NLS
            return null;
        }

        List<? extends org.apache.poi.ss.usermodel.PictureData> listOfAllPictures = workbook.getAllPictures();
        // if no images are extracted from the ppt, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, "Could not get path for image extraction from AbstractFile: {0}", af.getName()); //NON-NLS
            return null;
        }

        int i = 0;
        for (org.apache.poi.ss.usermodel.PictureData pictureData : listOfAllPictures) {
            String imageName = "image_" + i + "." + pictureData.suggestFileExtension(); //NON-NLS
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputFolderPath + File.separator + imageName);
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Invalid path provided for image extraction", ex); //NON-NLS
                continue;
            }
            try {
                fos.write(pictureData.getData());
                fos.close();
                i++;
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write to the provided location", ex); //NON-NLS
                continue;
            }

            String fileRelativePath = File.separator + moduleDirRelative + File.separator + parentFileName + File.separator + imageName;
            long size = pictureData.getData().length;
            ExtractedImage extractedimage = new ExtractedImage(imageName, fileRelativePath, size, af);
            listOfExtractedImages.add(extractedimage);
        }
        return listOfExtractedImages;

    }

    private List<ExtractedImage> extractImagesFromXlsx(AbstractFile af) {
        List<ExtractedImage> listOfExtractedImages = new ArrayList<>();
        String parentFileName = getUniqueName(af);

        Workbook workbook;
        try {
            workbook = new XSSFWorkbook(new ReadContentInputStream(af));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "HSSFWorkbook container could not be instantiated while reading " + af.getName(), ex); //NON-NLS
            return null;
        }

        List<? extends org.apache.poi.ss.usermodel.PictureData> listOfAllPictures = workbook.getAllPictures();
        // if no images are extracted from the ppt, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(parentFileName);
        }
        if (outputFolderPath == null) {
            logger.log(Level.WARNING, "Could not get path for image extraction from AbstractFile: {0}", af.getName()); //NON-NLS
            return null;
        }

        int i = 0;
        for (org.apache.poi.ss.usermodel.PictureData pictureData : listOfAllPictures) {
            String imageName = "image_" + i + "." + pictureData.suggestFileExtension();
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputFolderPath + File.separator + imageName);
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Invalid path provided for image extraction", ex); //NON-NLS
                continue;
            }
            try {
                fos.write(pictureData.getData());
                fos.close();
                i++;
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write to the provided location", ex); //NON-NLS
                continue;
            }

            String fileRelativePath = File.separator + moduleDirRelative + File.separator + parentFileName + File.separator + imageName;
            long size = pictureData.getData().length;
            ExtractedImage extractedimage = new ExtractedImage(imageName, fileRelativePath, size, af);
            listOfExtractedImages.add(extractedimage);
        }
        return listOfExtractedImages;

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
        String outputFolderPath = moduleDirAbsolute + File.separator + parentFileName;
        File outputFilePath = new File(outputFolderPath);
        if (!outputFilePath.exists()) {
            try {
                outputFilePath.mkdirs();
            } catch (SecurityException ex) {
                logger.log(Level.WARNING, "Unable to create the output path to write the extracted image", ex); //NON-NLS
                return null;
            }
        }
        return outputFolderPath;
    }

    /**
     * Generates unique name for abstract files.
     *
     * @param af
     * @return
     */
    private String getUniqueName(AbstractFile af) {
        return af.getName() + "_" + af.getId();
    }

    private static class ExtractedImage {
        //String fileName, String localPath, long size, long ctime, long crtime, 
        //long atime, long mtime, boolean isFile, AbstractFile parentFile, String rederiveDetails, String toolName, String toolVersion, String otherDetails

        private String fileName;
        private String localPath;
        private long size;
        private long ctime;
        private long crtime;
        private long atime;
        private long mtime;
        private AbstractFile parentFile;

        ExtractedImage(String fileName, String localPath, long size, AbstractFile parentPath) {
            this.fileName = fileName;
            this.localPath = localPath;
            this.size = size;
            this.parentFile = parentPath;
            this.ctime = 0;
            this.crtime = 0;
            this.atime = 0;
            this.mtime = 0;
        }

        ExtractedImage(String fileName, String localPath, long size, long ctime, long crtime, long atime, long mtime, AbstractFile parentPath) {
            this.fileName = fileName;
            this.localPath = localPath;
            this.size = size;
            this.ctime = ctime;
            this.crtime = crtime;
            this.atime = atime;
            this.mtime = mtime;
            this.parentFile = parentPath;
        }

        /**
         * @return the fileName
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * @return the localPath
         */
        public String getLocalPath() {
            return localPath;
        }

        /**
         * @return the size
         */
        public long getSize() {
            return size;
        }

        /**
         * @return the ctime
         */
        public long getCtime() {
            return ctime;
        }

        /**
         * @return the crtime
         */
        public long getCrtime() {
            return crtime;
        }

        /**
         * @return the atime
         */
        public long getAtime() {
            return atime;
        }

        /**
         * @return the mtime
         */
        public long getMtime() {
            return mtime;
        }

        /**
         * @return the parentFile
         */
        public AbstractFile getParentFile() {
            return parentFile;
        }
    }
    
}
