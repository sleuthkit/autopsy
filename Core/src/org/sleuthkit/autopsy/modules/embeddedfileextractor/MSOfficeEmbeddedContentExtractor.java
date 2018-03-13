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
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.record.RecordInputStream.LeftoverDataException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.RecordFormatException;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts embedded content (e.g. images, audio, video) from Microsoft Office
 * documents (both original and OOXML forms).
 */
class MSOfficeEmbeddedContentExtractor {

    private final FileManager fileManager;
    private final IngestServices services;
    private static final Logger LOGGER = Logger.getLogger(MSOfficeEmbeddedContentExtractor.class.getName());
    private final IngestJobContext context;
    private String parentFileName;
    private final String UNKNOWN_IMAGE_NAME_PREFIX = "image_"; //NON-NLS
    private final FileTypeDetector fileTypeDetector;

    private String moduleDirRelative;
    private String moduleDirAbsolute;

    private AutoDetectParser parser = new AutoDetectParser();
    private Detector detector = parser.getDetector();
    private TikaConfig config = TikaConfig.getDefaultConfig();

    /**
     * Enum of mimetypes for which we can extract embedded content.
     */
    enum SupportedExtractionFormats {

        DOC("application/msword"), //NON-NLS
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), //NON-NLS
        PPT("application/vnd.ms-powerpoint"), //NON-NLS
        PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"), //NON-NLS
        XLS("application/vnd.ms-excel"), //NON-NLS
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); //NON-NLS

        private final String mimeType;

        SupportedExtractionFormats(final String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return this.mimeType;
        }
    }
    private SupportedExtractionFormats abstractFileExtractionFormat;

    MSOfficeEmbeddedContentExtractor(IngestJobContext context, FileTypeDetector fileTypeDetector, String moduleDirRelative, String moduleDirAbsolute) throws NoCurrentCaseException {

        this.fileManager = Case.getOpenCase().getServices().getFileManager();
        this.services = IngestServices.getInstance();
        this.context = context;
        this.fileTypeDetector = fileTypeDetector;
        this.moduleDirRelative = moduleDirRelative;
        this.moduleDirAbsolute = moduleDirAbsolute;
    }

    /**
     * This method returns true if the file format is currently supported. Else
     * it returns false. Performs only Apache Tika based detection.
     *
     * @param abstractFile The AbstractFile whose mimetype is to be determined.
     *
     * @return This method returns true if the file format is currently
     *         supported. Else it returns false.
     */
    boolean isContentExtractionSupported(AbstractFile abstractFile) {
        String abstractFileMimeType = fileTypeDetector.getMIMEType(abstractFile);
        for (SupportedExtractionFormats s : SupportedExtractionFormats.values()) {
            if (s.toString().equals(abstractFileMimeType)) {
                abstractFileExtractionFormat = s;
                return true;
            }
        }
        return false;
    }

    /**
     * This method selects the appropriate process of extracting embedded
     * content from files using either Tika or POI classes. Once the content has
     * been extracted as files, the method adds them to the DB and fires a
     * ModuleContentEvent. ModuleContent Event is not fired if no content was
     * extracted from the processed file.
     *
     * @param abstractFile The abstract file to be processed.
     */
    void extractEmbeddedContent(AbstractFile abstractFile) {
        List<ExtractedFile> listOfExtractedImages = null;
        List<AbstractFile> listOfExtractedImageAbstractFiles = null;
        this.parentFileName = EmbeddedFileExtractorIngestModule.getUniqueName(abstractFile);

        // Skip files that already have been unpacked.
        try {
            if (abstractFile.hasChildren()) {
                //check if local unpacked dir exists
                if (new File(getOutputFolderPath(parentFileName)).exists()) {
                    LOGGER.log(Level.INFO, "File already has been processed as it has children and local unpacked file, skipping: {0}", abstractFile.getName()); //NON-NLS
                    return;
                }
            }
        } catch (TskCoreException e) {
            LOGGER.log(Level.SEVERE, String.format("Error checking if file already has been processed, skipping: %s", parentFileName), e); //NON-NLS
            return;
        }

        // Call the appropriate extraction method based on mime type
        switch (abstractFileExtractionFormat) {
            case DOCX:
            case PPTX:
            case XLSX:
                listOfExtractedImages = extractEmbeddedContentFromOOXML(abstractFile);
                break;
            case DOC:
                listOfExtractedImages = extractEmbeddedImagesFromDoc(abstractFile);
                break;
            case PPT:
                listOfExtractedImages = extractEmbeddedImagesFromPpt(abstractFile);
                break;
            case XLS:
                listOfExtractedImages = extractImagesFromXls(abstractFile);
                break;
            default:
                break;
        }

        if (listOfExtractedImages == null) {
            return;
        }
        // the common task of adding abstractFile to derivedfiles is performed.
        listOfExtractedImageAbstractFiles = new ArrayList<>();
        for (ExtractedFile extractedImage : listOfExtractedImages) {
            try {
                listOfExtractedImageAbstractFiles.add(fileManager.addDerivedFile(extractedImage.getFileName(), extractedImage.getLocalPath(), extractedImage.getSize(),
                        extractedImage.getCtime(), extractedImage.getCrtime(), extractedImage.getAtime(), extractedImage.getAtime(),
                        true, abstractFile, null, EmbeddedFileExtractorModuleFactory.getModuleName(), null, null, TskData.EncodingType.XOR1));
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.extractImage.addToDB.exception.msg"), ex); //NON-NLS
            }
        }
        if (!listOfExtractedImages.isEmpty()) {
            services.fireModuleContentEvent(new ModuleContentEvent(abstractFile));
            context.addFilesToJob(listOfExtractedImageAbstractFiles);
        }
    }

    /**
     * Extracts embedded content from OOXML documents (i.e. pptx, docx and xlsx)
     * using Tika. This will extract images and other multimedia content
     * embedded in the given file.
     *
     * @param abstractFile The file to extract content from.
     *
     * @return A list of extracted files.
     */
    private List<ExtractedFile> extractEmbeddedContentFromOOXML(AbstractFile abstractFile) {
        Metadata metadata = new Metadata();

        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);

        // Passing -1 to the BodyContentHandler constructor disables the Tika
        // write limit (which defaults to 100,000 characters.
        ContentHandler contentHandler = new BodyContentHandler(-1);

        // Use the more memory efficient Tika SAX parsers for DOCX and
        // PPTX files (it already uses SAX for XLSX).
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        officeParserConfig.setUseSAXDocxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        EmbeddedDocumentExtractor extractor = new EmbeddedContentExtractor(parseContext);
        parseContext.set(EmbeddedDocumentExtractor.class, extractor);
        ReadContentInputStream stream = new ReadContentInputStream(abstractFile);

        try {
            parser.parse(stream, contentHandler, metadata, parseContext);
        } catch (IOException | SAXException | TikaException ex) {
            LOGGER.log(Level.WARNING, "Error while parsing file, skipping: " + abstractFile.getName(), ex); //NON-NLS
            return null;
        }

        return ((EmbeddedContentExtractor) extractor).getExtractedImages();
    }

    /**
     * Extract embedded images from doc format files.
     *
     * @param af the file from which images are to be extracted.
     *
     * @return list of extracted images. Returns null in case no images were
     *         extracted.
     */
    private List<ExtractedFile> extractEmbeddedImagesFromDoc(AbstractFile af) {
        List<Picture> listOfAllPictures;

        try {
            HWPFDocument doc = new HWPFDocument(new ReadContentInputStream(af));
            PicturesTable pictureTable = doc.getPicturesTable();
            listOfAllPictures = pictureTable.getAllPictures();
        } catch (IOException | IllegalArgumentException
                | IndexOutOfBoundsException | NullPointerException ex) {
            // IOException:
            // Thrown when the document has issues being read.

            // IllegalArgumentException:
            // This will catch OldFileFormatException, which is thrown when the
            // document's format is Word 95 or older. Alternatively, this is
            // thrown when attempting to load an RTF file as a DOC file.
            // However, our code verifies the file format before ever running it
            // through the EmbeddedContentExtractor. This exception gets thrown in the
            // "IN10-0137.E01" image regardless. The reason is unknown.
            // IndexOutOfBoundsException:
            // NullPointerException:
            // These get thrown in certain images. The reason is unknown. It is
            // likely due to problems with the file formats that POI is poorly
            // handling.
            return null;
        } catch (Throwable ex) {
            // instantiating POI containers throw RuntimeExceptions
            LOGGER.log(Level.SEVERE, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.docContainer.init.err", af.getName()), ex); //NON-NLS
            return null;
        }

        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            return null;
        }
        List<ExtractedFile> listOfExtractedImages = new ArrayList<>();
        byte[] data = null;
        for (Picture picture : listOfAllPictures) {
            String fileName = picture.suggestFullFileName();
            try {
                data = picture.getContent();
            } catch (Exception ex) {
                return null;
            }
            writeExtractedImage(Paths.get(outputFolderPath, fileName).toString(), data);
            // TODO Extract more info from the Picture viz ctime, crtime, atime, mtime
            listOfExtractedImages.add(new ExtractedFile(fileName, getFileRelativePath(fileName), picture.getSize()));
        }

        return listOfExtractedImages;
    }

    /**
     * Extract embedded images from ppt format files.
     *
     * @param af the file from which images are to be extracted.
     *
     * @return list of extracted images. Returns null in case no images were
     *         extracted.
     */
    private List<ExtractedFile> extractEmbeddedImagesFromPpt(AbstractFile af) {
        List<HSLFPictureData> listOfAllPictures = null;

        try {
            HSLFSlideShow ppt = new HSLFSlideShow(new ReadContentInputStream(af));
            listOfAllPictures = ppt.getPictureData();
        } catch (IOException | IllegalArgumentException
                | IndexOutOfBoundsException ex) {
            // IllegalArgumentException:
            // This will catch OldFileFormatException, which is thrown when the
            // document version is unsupported. The IllegalArgumentException may
            // also get thrown for unknown reasons.

            // IOException:
            // Thrown when the document has issues being read.
            // IndexOutOfBoundsException:
            // This gets thrown in certain images. The reason is unknown. It is
            // likely due to problems with the file formats that POI is poorly
            // handling.
            return null;
        } catch (Throwable ex) {
            // instantiating POI containers throw RuntimeExceptions
            LOGGER.log(Level.SEVERE, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.pptContainer.init.err", af.getName()), ex); //NON-NLS
            return null;
        }

        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            return null;
        }

        // extract the content to the above initialized outputFolder.
        // extraction path - outputFolder/image_number.ext
        int i = 0;
        List<ExtractedFile> listOfExtractedImages = new ArrayList<>();
        byte[] data = null;
        for (HSLFPictureData pictureData : listOfAllPictures) {

            // Get image extension, generate image name, write image to the module
            // output folder, add it to the listOfExtractedImageAbstractFiles
            PictureType type = pictureData.getType();
            String ext;
            switch (type) {
                case JPEG:
                    ext = ".jpg"; //NON-NLS
                    break;
                case PNG:
                    ext = ".png"; //NON-NLS
                    break;
                case WMF:
                    ext = ".wmf"; //NON-NLS
                    break;
                case EMF:
                    ext = ".emf"; //NON-NLS
                    break;
                case PICT:
                    ext = ".pict"; //NON-NLS
                    break;
                default:
                    continue;
            }
            String imageName = UNKNOWN_IMAGE_NAME_PREFIX + i + ext; //NON-NLS
            try {
                data = pictureData.getData();
            } catch (Exception ex) {
                return null;
            }
            writeExtractedImage(Paths.get(outputFolderPath, imageName).toString(), data);
            listOfExtractedImages.add(new ExtractedFile(imageName, getFileRelativePath(imageName), pictureData.getData().length));
            i++;
        }
        return listOfExtractedImages;
    }

    /**
     * Extract images from xls format files.
     *
     * @param af the file from which images are to be extracted.
     *
     * @return list of extracted images. Returns null in case no images were
     *         extracted.
     */
    private List<ExtractedFile> extractImagesFromXls(AbstractFile af) {
        List<? extends org.apache.poi.ss.usermodel.PictureData> listOfAllPictures = null;

        try {
            Workbook xls = new HSSFWorkbook(new ReadContentInputStream(af));
            listOfAllPictures = xls.getAllPictures();
        } catch (IOException | LeftoverDataException
                | RecordFormatException | IllegalArgumentException
                | IndexOutOfBoundsException ex) {
            // IllegalArgumentException:
            // This will catch OldFileFormatException, which is thrown when the
            // document version is unsupported. The IllegalArgumentException may
            // also get thrown for unknown reasons.

            // IOException:
            // Thrown when the document has issues being read.
            // LeftoverDataException:
            // This is thrown for poorly formatted files that have more data
            // than expected.
            // RecordFormatException:
            // This is thrown for poorly formatted files that have less data
            // that expected.
            // IllegalArgumentException:
            // IndexOutOfBoundsException:
            // These get thrown in certain images. The reason is unknown. It is
            // likely due to problems with the file formats that POI is poorly
            // handling.
            return null;
        } catch (Throwable ex) {
            // instantiating POI containers throw RuntimeExceptions
            LOGGER.log(Level.SEVERE, String.format("%s%s", NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.xlsContainer.init.err", af.getName()), af.getName()), ex); //NON-NLS
            return null;
        }

        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        String outputFolderPath;
        if (listOfAllPictures.isEmpty()) {
            return null;
        } else {
            outputFolderPath = getOutputFolderPath(this.parentFileName);
        }
        if (outputFolderPath == null) {
            return null;
        }

        int i = 0;
        List<ExtractedFile> listOfExtractedImages = new ArrayList<>();
        byte[] data = null;
        for (org.apache.poi.ss.usermodel.PictureData pictureData : listOfAllPictures) {
            String imageName = UNKNOWN_IMAGE_NAME_PREFIX + i + "." + pictureData.suggestFileExtension(); //NON-NLS
            try {
                data = pictureData.getData();
            } catch (Exception ex) {
                return null;
            }
            writeExtractedImage(Paths.get(outputFolderPath, imageName).toString(), data);
            listOfExtractedImages.add(new ExtractedFile(imageName, getFileRelativePath(imageName), pictureData.getData().length));
            i++;
        }
        return listOfExtractedImages;

    }

    /**
     * Writes image to the module output location.
     *
     * @param outputPath Path where images is written
     * @param data       byte representation of the data to be written to the
     *                   specified location.
     */
    private void writeExtractedImage(String outputPath, byte[] data) {
        try (EncodedFileOutputStream fos = new EncodedFileOutputStream(new FileOutputStream(outputPath), TskData.EncodingType.XOR1)) {
            fos.write(data);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not write to the provided location: " + outputPath, ex); //NON-NLS
        }
    }

    /**
     * Gets path to the output folder for file extraction. If the path does not
     * exist, it is created.
     *
     * @param parentFileName name of the abstract file being processed
     *
     * @return path to the file extraction folder for a given abstract file.
     */
    private String getOutputFolderPath(String parentFileName) {
        String outputFolderPath = moduleDirAbsolute + File.separator + parentFileName;
        File outputFilePath = new File(outputFolderPath);
        if (!outputFilePath.exists()) {
            try {
                outputFilePath.mkdirs();
            } catch (SecurityException ex) {
                LOGGER.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "EmbeddedFileExtractorIngestModule.ImageExtractor.getOutputFolderPath.exception.msg", parentFileName), ex);
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
     *                 generated.
     *
     * @return
     */
    private String getFileRelativePath(String fileName) {
        // Used explicit FWD slashes to maintain DB consistency across operating systems.
        return "/" + moduleDirRelative + "/" + this.parentFileName + "/" + fileName; //NON-NLS
    }

    /**
     * Represents a file extracted using either Tika or POI methods. Currently,
     * POI is not capable of extracting ctime, crtime, mtime, and atime; these
     * values are set to 0.
     */
    private static class ExtractedFile {
        //String fileName, String localPath, long size, long ctime, long crtime, 
        //long atime, long mtime, boolean isFile, AbstractFile parentFile, String rederiveDetails, String toolName, String toolVersion, String otherDetails

        private final String fileName;
        private final String localPath;
        private final long size;
        private final long ctime;
        private final long crtime;
        private final long atime;
        private final long mtime;

        ExtractedFile(String fileName, String localPath, long size) {
            this(fileName, localPath, size, 0, 0, 0, 0);
        }

        ExtractedFile(String fileName, String localPath, long size, long ctime, long crtime, long atime, long mtime) {
            this.fileName = fileName;
            this.localPath = localPath;
            this.size = size;
            this.ctime = ctime;
            this.crtime = crtime;
            this.atime = atime;
            this.mtime = mtime;
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
    }

    /**
     * Our custom embedded content extractor for OOXML files. We pass an
     * instance of this class to Tika and Tika calls the parseEmbedded() method
     * when it encounters an embedded file.
     */
    private class EmbeddedContentExtractor extends ParsingEmbeddedDocumentExtractor {

        private int fileCount = 0;
        // Map of file name to ExtractedFile instance. This can revert to a 
        // plain old list after we upgrade to Tika 1.16 or above.
        private final Map<String, ExtractedFile> nameToExtractedFileMap = new HashMap<>();

        public EmbeddedContentExtractor(ParseContext context) {
            super(context);
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler,
                Metadata metadata, boolean outputHtml) throws SAXException, IOException {

            // Get the mime type for the embedded document
            MediaType contentType = detector.detect(stream, metadata);

            if (!contentType.getType().equalsIgnoreCase("image") //NON-NLS
                    && !contentType.getType().equalsIgnoreCase("video") //NON-NLS
                    && !contentType.getType().equalsIgnoreCase("application") //NON-NLS
                    && !contentType.getType().equalsIgnoreCase("audio")) { //NON-NLS
                return;
            }

            // try to get the name of the embedded file from the metadata
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);

            // TODO: This can be removed after we upgrade to Tika 1.16 or
            // above. The 1.16 version of Tika keeps track of files that 
            // have been seen before.
            if (nameToExtractedFileMap.containsKey(name)) {
                return;
            }

            if (name == null) {
                name = UNKNOWN_IMAGE_NAME_PREFIX + fileCount++;
            } else {
                //make sure to select only the file name (not any directory paths
                //that might be included in the name) and make sure
                //to normalize the name
                name = FilenameUtils.normalize(FilenameUtils.getName(name));
            }

            // Get the suggested extension based on mime type.
            if (name.indexOf('.') == -1) {
                try {
                    name += config.getMimeRepository().forName(contentType.toString()).getExtension();
                } catch (MimeTypeException ex) {
                    LOGGER.log(Level.WARNING, "Failed to get suggested extension for the following type: " + contentType.toString(), ex); //NON-NLS
                }
            }

            File extractedFile = new File(Paths.get(getOutputFolderPath(parentFileName), name).toString());
            byte[] fileData = IOUtils.toByteArray(stream);
            writeExtractedImage(extractedFile.getAbsolutePath(), fileData);
            nameToExtractedFileMap.put(name, new ExtractedFile(name, getFileRelativePath(name), fileData.length));
        }

        /**
         * Get list of extracted files.
         *
         * @return List of extracted files.
         */
        public List<ExtractedFile> getExtractedImages() {
            return new ArrayList<>(nameToExtractedFileMap.values());
        }
    }
}
