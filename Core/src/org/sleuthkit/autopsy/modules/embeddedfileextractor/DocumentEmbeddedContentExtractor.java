/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.apache.poi.ss.usermodel.Workbook;
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
import static org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.FileTaskExecutor.FileTaskFailedException;
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
 * documents (both original and OOXML forms) and PDF documents.
 */
class DocumentEmbeddedContentExtractor {

    private final FileManager fileManager;
    private final IngestServices services;
    private static final Logger LOGGER = Logger.getLogger(DocumentEmbeddedContentExtractor.class.getName());
    private final IngestJobContext context;
    private String parentFileName;
    private final String UNKNOWN_IMAGE_NAME_PREFIX = "image_"; //NON-NLS
    private final FileTypeDetector fileTypeDetector;
    private final FileTaskExecutor fileTaskExecutor;

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
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), //NON-NLS
        PDF("application/pdf"); //NON-NLS

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

    DocumentEmbeddedContentExtractor(IngestJobContext context, FileTypeDetector fileTypeDetector, String moduleDirRelative, String moduleDirAbsolute, FileTaskExecutor fileTaskExecutor) throws NoCurrentCaseException {

        this.fileManager = Case.getCurrentCaseThrows().getServices().getFileManager();
        this.services = IngestServices.getInstance();
        this.context = context;
        this.fileTypeDetector = fileTypeDetector;
        this.moduleDirRelative = moduleDirRelative;
        this.moduleDirAbsolute = moduleDirAbsolute;
        this.fileTaskExecutor = fileTaskExecutor;
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
            if (checkForIngestCancellation(abstractFile)) {
                break;
            }
            if (s.toString().equals(abstractFileMimeType)) {
                abstractFileExtractionFormat = s;
                return true;
            }
        }
        return false;
    }

    /**
     * Private helper method to standardize the cancellation check that is
     * performed when running ingest. Will return false if the
     * DocumentEmbeddedContentExtractor is being used without an
     * IngestJobContext.
     *
     * @param file The file being extracted, this is only used for logging
     *             purposes.
     *
     * @return True if ingest has been cancelled, false otherwise. FFFF
     */
    private boolean checkForIngestCancellation(AbstractFile file) {
        if (fileTaskExecutor != null && context != null && context.fileIngestIsCancelled()) {
            LOGGER.log(Level.INFO, "Ingest was cancelled. Results extracted from the following document file may be incomplete. Name: {0}Object ID: {1}", new Object[]{file.getName(), file.getId()});
            return true;
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
        //save the parent file name with out illegal windows characters
        this.parentFileName = utf8SanitizeFileName(EmbeddedFileExtractorIngestModule.getUniqueName(abstractFile));

        // Skip files that already have been unpacked.
        /*
         * TODO (Jira-7145): Is the logic of this check correct? Also note that
         * this suspect code used to have a bug in that makeOutputFolder() was
         * called, so the directory was always created here if it did not exist,
         * making this check only a call to AbstractFile.hasChildren() in
         * practice.
         */
        try {
            if (abstractFile.hasChildren()) {
                //check if local unpacked dir exists
                File outputFolder = Paths.get(moduleDirAbsolute, parentFileName).toFile();
                if (fileTaskExecutor.exists(outputFolder)) {
                    return;
                }
            }
        } catch (TskCoreException | FileTaskExecutor.FileTaskFailedException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, String.format("Error checking if %s (objID = %d) has already has been processed, skipping", abstractFile.getName(), abstractFile.getId()), e); //NON-NLS
            return;
        }
        if (checkForIngestCancellation(abstractFile)) {
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
            case PDF:
                listOfExtractedImages = extractEmbeddedContentFromPDF(abstractFile);
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
            if (checkForIngestCancellation(abstractFile)) {
                return;
            }
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
        if (checkForIngestCancellation(abstractFile)) {
            return null; //null will cause the calling method to return.
        }
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
        } catch (Exception ex) {
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
            //Any runtime exception escaping 
            LOGGER.log(Level.WARNING, "Word document container could not be initialized. Reason: {0}", ex.getMessage()); //NON-NLS
            return null;
        }

        Path outputFolderPath;
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
        int pictureNumber = 0; //added to ensure uniqueness in cases where suggestFullFileName returns duplicates
        for (Picture picture : listOfAllPictures) {
            if (checkForIngestCancellation(af)) {
                return null; //null will cause the calling method to return.
            }
            String fileName = UNKNOWN_IMAGE_NAME_PREFIX + pictureNumber + "." + picture.suggestFileExtension();
            try {
                data = picture.getContent();
            } catch (Exception ex) {
                return null;
            }
            writeExtractedImage(Paths.get(outputFolderPath.toString(), fileName).toString(), data);
            // TODO Extract more info from the Picture viz ctime, crtime, atime, mtime
            listOfExtractedImages.add(new ExtractedFile(fileName, getFileRelativePath(fileName), picture.getSize()));
            pictureNumber++;
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
        } catch (Exception ex) {
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
            LOGGER.log(Level.WARNING, "PPT container could not be initialized. Reason: {0}", ex.getMessage()); //NON-NLS
            return null;
        }

        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        Path outputFolderPath;
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
            if (checkForIngestCancellation(af)) {
                return null; //null will cause the calling method to return.
            }
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
            writeExtractedImage(Paths.get(outputFolderPath.toString(), imageName).toString(), data);
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
        } catch (Exception ex) {
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
            LOGGER.log(Level.WARNING, "Excel (.xls) document container could not be initialized. Reason: {0}", ex.getMessage()); //NON-NLS
            return null;
        }

        // if no images are extracted from the PPT, return null, else initialize
        // the output folder for image extraction.
        Path outputFolderPath;
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
            if (checkForIngestCancellation(af)) {
                return null; //null will cause the calling method to return.
            }
            String imageName = UNKNOWN_IMAGE_NAME_PREFIX + i + "." + pictureData.suggestFileExtension(); //NON-NLS
            try {
                data = pictureData.getData();
            } catch (Exception ex) {
                return null;
            }
            writeExtractedImage(Paths.get(outputFolderPath.toString(), imageName).toString(), data);
            listOfExtractedImages.add(new ExtractedFile(imageName, getFileRelativePath(imageName), pictureData.getData().length));
            i++;
        }
        return listOfExtractedImages;

    }

    /**
     * Extracts embedded attachments from PDF files.
     *
     * @param abstractFile Input PDF file
     *
     * @return List of extracted files to be made into derived file instances.
     */
    private List<ExtractedFile> extractEmbeddedContentFromPDF(AbstractFile abstractFile) {
        Path outputDirectory = getOutputFolderPath(parentFileName);
        if (outputDirectory == null) {
            return Collections.emptyList();
        }
        PDFAttachmentExtractor pdfExtractor = new PDFAttachmentExtractor(parser);
        try {
            //Get map of attachment name -> location disk.
            Map<String, PDFAttachmentExtractor.NewResourceData> extractedAttachments = pdfExtractor.extract(
                    new ReadContentInputStream(abstractFile), abstractFile.getId(),
                    outputDirectory);

            //Convert output to hook into the existing logic for creating derived files
            List<ExtractedFile> extractedFiles = new ArrayList<>();
            for (Entry<String, PDFAttachmentExtractor.NewResourceData> pathEntry : extractedAttachments.entrySet()) {
                if (checkForIngestCancellation(abstractFile)) {
                    return null; //null will cause the calling method to return.
                }
                String fileName = pathEntry.getKey();
                Path writeLocation = pathEntry.getValue().getPath();
                int fileSize = pathEntry.getValue().getLength();
                extractedFiles.add(new ExtractedFile(fileName,
                        getFileRelativePath(writeLocation.getFileName().toString()),
                        fileSize));
            }
            return extractedFiles;
        } catch (IOException | SAXException | TikaException | InvalidPathException ex) {
            LOGGER.log(Level.WARNING, "Error attempting to extract attachments from PDFs for file Name: " + abstractFile.getName() + " ID: " + abstractFile.getId(), ex); //NON-NLS         
        }
        return Collections.emptyList();
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
     * Gets the path to an output folder for extraction of embedded content from
     * a file. The folder will have the same name as the file name passed in. If
     * the folder does not exist, it is created.
     *
     * @param parentFileName The file name.
     *
     * @return The output folder path or null if the folder could not be found
     *         or created.
     */
    private Path getOutputFolderPath(String parentFileName) {
        Path outputFolderPath = Paths.get(moduleDirAbsolute, parentFileName);
        try {
            File outputFolder = outputFolderPath.toFile();
            if (!fileTaskExecutor.exists(outputFolder)) {
                if (!fileTaskExecutor.mkdirs(outputFolder)) {
                    outputFolderPath = null;
                }
            }
            return outputFolderPath;
        } catch (SecurityException | FileTaskFailedException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, String.format("Failed to find or create %s", outputFolderPath), ex);
            return null;
        }
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
        return Paths.get(moduleDirRelative, this.parentFileName, fileName).toString();
    }

    /**
     * UTF-8 sanitize and escape special characters in a file name or a file
     * name component
     *
     * @param fileName to escape
     *
     * @return Sanitized string
     */
    private static String utf8SanitizeFileName(String fileName) {
        Charset charset = StandardCharsets.UTF_8;
        return charset.decode(charset.encode(escapeFileName(fileName))).toString();
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

        private EmbeddedContentExtractor(ParseContext context) {
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
                fileCount++;
                name = UNKNOWN_IMAGE_NAME_PREFIX + fileCount;
            } else {
                //make sure to select only the file name (not any directory paths
                //that might be included in the name) and make sure
                //to normalize the name
                name = FilenameUtils.normalize(FilenameUtils.getName(name));
                //remove any illegal characters from name
                name = utf8SanitizeFileName(name);
            }

            // Get the suggested extension based on mime type.
            if (name.indexOf('.') == -1) {
                try {
                    name += config.getMimeRepository().forName(contentType.toString()).getExtension();
                } catch (MimeTypeException ex) {
                    LOGGER.log(Level.WARNING, "Failed to get suggested extension for the following type: " + contentType.toString(), ex); //NON-NLS
                }
            }

            Path outputFolderPath = getOutputFolderPath(parentFileName);
            if (outputFolderPath != null) {
                File extractedFile = new File(Paths.get(outputFolderPath.toString(), name).toString());
                byte[] fileData = IOUtils.toByteArray(stream);
                writeExtractedImage(extractedFile.getAbsolutePath(), fileData);
                nameToExtractedFileMap.put(name, new ExtractedFile(name, getFileRelativePath(name), fileData.length));
            }
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
