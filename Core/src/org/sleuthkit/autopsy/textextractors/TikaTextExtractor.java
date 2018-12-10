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
package org.sleuthkit.autopsy.textextractors;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import java.io.File;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParsingReader;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.openide.util.NbBundle;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.textextractors.extractionconfigs.ImageFileExtractionConfig;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extracts text from Tika supported content. Protects against Tika parser hangs
 * (for unexpected/corrupt content) using a timeout mechanism.
 */
final class TikaTextExtractor extends TextExtractor {

    //Mimetype groups to aassist extractor implementations in ignoring binary and 
    //archive files.
    private static final List<String> BINARY_MIME_TYPES
            = ImmutableList.of(
                    //ignore binary blob data, for which string extraction will be used
                    "application/octet-stream", //NON-NLS
                    "application/x-msdownload"); //NON-NLS

    /**
     * generally text extractors should ignore archives and let unpacking
     * modules take care of them
     */
    private static final List<String> ARCHIVE_MIME_TYPES
            = ImmutableList.of(
                    //ignore unstructured binary and compressed data, for which string extraction or unzipper works better
                    "application/x-7z-compressed", //NON-NLS
                    "application/x-ace-compressed", //NON-NLS
                    "application/x-alz-compressed", //NON-NLS
                    "application/x-arj", //NON-NLS
                    "application/vnd.ms-cab-compressed", //NON-NLS
                    "application/x-cfs-compressed", //NON-NLS
                    "application/x-dgc-compressed", //NON-NLS
                    "application/x-apple-diskimage", //NON-NLS
                    "application/x-gca-compressed", //NON-NLS
                    "application/x-dar", //NON-NLS
                    "application/x-lzx", //NON-NLS
                    "application/x-lzh", //NON-NLS
                    "application/x-rar-compressed", //NON-NLS
                    "application/x-stuffit", //NON-NLS
                    "application/x-stuffitx", //NON-NLS
                    "application/x-gtar", //NON-NLS
                    "application/x-archive", //NON-NLS
                    "application/x-executable", //NON-NLS
                    "application/x-gzip", //NON-NLS
                    "application/zip", //NON-NLS
                    "application/x-zoo", //NON-NLS
                    "application/x-cpio", //NON-NLS
                    "application/x-shar", //NON-NLS
                    "application/x-tar", //NON-NLS
                    "application/x-bzip", //NON-NLS
                    "application/x-bzip2", //NON-NLS
                    "application/x-lzip", //NON-NLS
                    "application/x-lzma", //NON-NLS
                    "application/x-lzop", //NON-NLS
                    "application/x-z", //NON-NLS
                    "application/x-compress"); //NON-NLS

    private static final java.util.logging.Logger tikaLogger = java.util.logging.Logger.getLogger("Tika"); //NON-NLS

    private final ExecutorService tikaParseExecutor = Executors.newSingleThreadExecutor();
    private static final String SQLITE_MIMETYPE = "application/x-sqlite3";

    private final AutoDetectParser parser = new AutoDetectParser();
    private final Content content;

    private boolean tesseractOCREnabled;
    private static final String TESSERACT_DIR_NAME = "Tesseract-OCR"; //NON-NLS
    private static final String TESSERACT_EXECUTABLE = "tesseract.exe"; //NON-NLS
    private static final File TESSERACT_PATH = locateTesseractExecutable();

    private static final List<String> TIKA_SUPPORTED_TYPES
            = new Tika().getParser().getSupportedTypes(new ParseContext())
                    .stream()
                    .map(mt -> mt.getType() + "/" + mt.getSubtype())
                    .collect(Collectors.toList());

    public TikaTextExtractor(Content content) {
        this.content = content;
    }

    /**
     * Returns a reader that will iterate over the text extracted from Apache
     * Tika.
     *
     * @param content Supported source content to extract
     *
     * @return Reader that contains Apache Tika extracted text
     *
     * @throws
     * org.sleuthkit.autopsy.textextractors.TextExtractor.TextExtractorException
     */
    @Override
    public Reader getReader() throws ExtractionException {
        ReadContentInputStream stream = new ReadContentInputStream(content);

        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);

        // Use the more memory efficient Tika SAX parsers for DOCX and
        // PPTX files (it already uses SAX for XLSX).
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        officeParserConfig.setUseSAXDocxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        // configure OCR if it is enabled in KWS settings and installed on the machine
        if (TESSERACT_PATH != null && tesseractOCREnabled && PlatformUtil.isWindowsOS() == true) {

            // configure PDFParser. 
            PDFParserConfig pdfConfig = new PDFParserConfig();

            // Extracting the inline images and letting Tesseract run on each inline image.
            // https://wiki.apache.org/tika/PDFParser%20%28Apache%20PDFBox%29
            // https://tika.apache.org/1.7/api/org/apache/tika/parser/pdf/PDFParserConfig.html
            pdfConfig.setExtractInlineImages(true);
            // Multiple pages within a PDF file might refer to the same underlying image.
            pdfConfig.setExtractUniqueInlineImagesOnly(true);
            parseContext.set(PDFParserConfig.class, pdfConfig);

            // Configure Tesseract parser to perform OCR
            TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
            String tesseractFolder = TESSERACT_PATH.getParent();
            ocrConfig.setTesseractPath(tesseractFolder);
            // Tesseract expects language data packs to be in a subdirectory of tesseractFolder, in a folder called "tessdata".
            // If they are stored somewhere else, use ocrConfig.setTessdataPath(String tessdataPath) to point to them
            System.out.println(getLanguagePacks());
            ocrConfig.setLanguage(getLanguagePacks());
            parseContext.set(TesseractOCRConfig.class, ocrConfig);
        }

        //Parse the file in a task, a convenient way to have a timeout...
        final Future<Reader> future = tikaParseExecutor.submit(() -> new ParsingReader(parser, stream, metadata, parseContext));
        try {
            final Reader tikaReader = future.get(getTimeout(content.getSize()), TimeUnit.SECONDS);

            //check if the reader is empty
            PushbackReader pushbackReader = new PushbackReader(tikaReader);
            int read = pushbackReader.read();
            if (read == -1) {
                throw new ExtractionException("Unable to extract text: Tika returned empty reader for " + content);
            }
            pushbackReader.unread(read);

            //concatenate parsed content and meta data into a single reader.
            CharSource metaDataCharSource = getMetaDataCharSource(metadata);
            return CharSource.concat(new ReaderCharSource(pushbackReader), metaDataCharSource).openStream();
        } catch (TimeoutException te) {
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.tikaParseTimeout.text", content.getId(), content.getName());
            throw new ExtractionException(msg, te);
        } catch (ExtractionException ex) {
            throw ex;
        } catch (Exception ex) {
            tikaLogger.log(Level.WARNING, "Exception: Unable to Tika parse the content" + content.getId() + ": " + content.getName(), ex.getCause()); //NON-NLS
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.exception.tikaParse.msg", content.getId(), content.getName());
            throw new ExtractionException(msg, ex);
        } finally {
            future.cancel(true);
        }
    }

    /**
     * Finds and returns the path to the Tesseract executable, if able.
     *
     * @return A File reference or null.
     */
    private static File locateTesseractExecutable() {
        if (!PlatformUtil.isWindowsOS()) {
            return null;
        }

        String executableToFindName = Paths.get(TESSERACT_DIR_NAME, TESSERACT_EXECUTABLE).toString();
        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, TikaTextExtractor.class.getPackage().getName(), false);
        if (null == exeFile) {
            return null;
        }

        if (!exeFile.canExecute()) {
            return null;
        }

        return exeFile;
    }

    /**
     * Gets a CharSource that wraps a formated representation of the given
     * Metadata.
     *
     * @param metadata The Metadata to wrap as a CharSource
     *
     * @return A CharSource for the given MetaData
     */
    static private CharSource getMetaDataCharSource(Metadata metadata) {
        return CharSource.wrap(
                new StringBuilder("\n\n------------------------------METADATA------------------------------\n\n")
                        .append(Stream.of(metadata.names()).sorted()
                                .map(key -> key + ": " + metadata.get(key))
                                .collect(Collectors.joining("\n"))
                        ));
    }

    /**
     * Determines if Tika is supported for this content type and mimetype.
     *
     * @param content        Source content to read
     * @param detectedFormat Mimetype of content
     *
     * @return Flag indicating support for reading content type
     */
    @Override
    public boolean isSupported(Content content, String detectedFormat) {
        if (detectedFormat == null
                || BINARY_MIME_TYPES.contains(detectedFormat) //any binary unstructured blobs (string extraction will be used)
                || ARCHIVE_MIME_TYPES.contains(detectedFormat)
                || (detectedFormat.startsWith("video/") && !detectedFormat.equals("video/x-flv")) //skip video other than flv (tika supports flv only) //NON-NLS
                || detectedFormat.equals(SQLITE_MIMETYPE) //Skip sqlite files, Tika cannot handle virtual tables and will fail with an exception. //NON-NLS
                ) {
            return false;
        }
        return TIKA_SUPPORTED_TYPES.contains(detectedFormat);
    }

    /**
     * Retrieves all of the installed language packs from their designated
     * directory location to be used to configure Tesseract OCR.
     *
     * @return String of all language packs available for Tesseract to use
     */
    private String getLanguagePacks() {
        File languagePackRootDir = new File(TESSERACT_PATH.getParent(), "tessdata");
        //Acceptable extensions for Tesseract-OCR version 3.05 language packs.
        //All extensions other than traineddata are associated with cube files that
        //have been made obsolete since version 4.0.
        List<String> acceptableExtensions = Arrays.asList("traineddata", "params",
                "lm", "fold", "bigrams", "nn", "word-freq", "size",
                "user-patterns", "user-words");
        //Pull out only unique languagePacks
        HashSet<String> languagePacks = new HashSet<>();
        if (languagePackRootDir.exists()) {
            for (File languagePack : languagePackRootDir.listFiles()) {
                if (languagePack.isDirectory() || !acceptableExtensions.contains(
                        FilenameUtils.getExtension(languagePack.getName()))) {
                    continue;
                }
                String threeLetterPackageName = languagePack.getName().substring(0, 3);
                //Ignore the eng language pack if accidentally added
                languagePacks.add(threeLetterPackageName);
            }
        }
        return String.join("+", languagePacks);
    }

    /**
     * Return timeout that should be used to index the content.
     *
     * @param size size of the content
     *
     * @return time in seconds to use a timeout
     */
    private static int getTimeout(long size) {
        if (size < 1024 * 1024L) //1MB
        {
            return 60;
        } else if (size < 10 * 1024 * 1024L) //10MB
        {
            return 1200;
        } else if (size < 100 * 1024 * 1024L) //100MB
        {
            return 3600;
        } else {
            return 3 * 3600;
        }

    }

    /**
     * Determines how the extraction process will proceed given the settings
     * stored in this context instance.
     *
     * See the ImageFileExtractionConfig class in the extractionconfigs package
     * for available settings.
     *
     * @param context Instance containing config classes
     */
    @Override
    public void setExtractionSettings(Lookup context) {
        if (context != null) {
            ImageFileExtractionConfig configInstance = context.lookup(ImageFileExtractionConfig.class);
            if (configInstance == null) {
                return;
            }
            if (Objects.nonNull(configInstance.getOCREnabled())) {
                this.tesseractOCREnabled = configInstance.getOCREnabled();
            }
        }
    }

    /**
     * An implementation of CharSource that just wraps an existing reader and
     * returns it in openStream().
     */
    private static class ReaderCharSource extends CharSource {

        private final Reader reader;

        ReaderCharSource(Reader reader) {
            this.reader = reader;
        }

        @Override
        public Reader openStream() throws IOException {
            return reader;
        }
    }
}
