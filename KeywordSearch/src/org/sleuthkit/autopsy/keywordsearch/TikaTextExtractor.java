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
package org.sleuthkit.autopsy.keywordsearch;

import com.google.common.io.CharSource;
import java.io.File;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extracts text from Tika supported content. Protects against Tika
 * parser hangs (for unexpected/corrupt content) using a timeout mechanism.
 */
class TikaTextExtractor extends ContentTextExtractor {

    static final private Logger logger = Logger.getLogger(TikaTextExtractor.class.getName());
    private final ExecutorService tikaParseExecutor = Executors.newSingleThreadExecutor();

    private final AutoDetectParser parser = new AutoDetectParser();
    
    private static final String TESSERACT_DIR_NAME = "Tesseract-OCR"; //NON-NLS
    private static final String TESSERACT_EXECUTABLE = "tesseract.exe"; //NON-NLS
    private static final File TESSERACT_PATH = locateTesseractExecutable();

    private static final List<String> TIKA_SUPPORTED_TYPES
            = new Tika().getParser().getSupportedTypes(new ParseContext())
                    .stream()
                    .map(mt -> mt.getType() + "/" + mt.getSubtype())
                    .collect(Collectors.toList());

    @Override
    public void logWarning(final String msg, Exception ex) {
        KeywordSearch.getTikaLogger().log(Level.WARNING, msg, ex);
    }

    @Override
    public Reader getReader(Content content) throws TextExtractorException {
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
        if (TESSERACT_PATH != null && KeywordSearchSettings.getOcrOption()) {
            
            // configure PDFParser. 
            // NOTE: There are two ways of running OCR on PDFs:
            // 1. Extracting the inline images and letting Tesseract run on each inline image.
            // 2. Rendering each PDF page as a single image and running Tesseract on that single image.
            // https://wiki.apache.org/tika/PDFParser%20%28Apache%20PDFBox%29
            PDFParserConfig pdfConfig = new PDFParserConfig();
            
            // using option 1
            // https://tika.apache.org/1.7/api/org/apache/tika/parser/pdf/PDFParserConfig.html
            pdfConfig.setExtractInlineImages(true); 
            // Multiple pages within a PDF file might refer to the same underlying image.
            // Note that uniqueness is determined only by the underlying PDF COSObject id, not by file hash or similar equality metric.
            pdfConfig.setExtractUniqueInlineImagesOnly(true);            
            parseContext.set(PDFParserConfig.class, pdfConfig);
            
            /* Option 2:
            // NOTE: looks like this option is no longer available in Tika versions 1.17 and later
            // ocrStrategy options: no_ocr (rely on regular text extraction only), ocr_only (don't bother extracting text, just run OCR on each page), 
            // ocr_and_text (both extract text and run OCR)
            // https://tika.apache.org/1.16/api/org/apache/tika/parser/pdf/PDFParserConfig.html
            pdfConfig.setOcrStrategy("ocr_and_text");
            */
            
            // Configure Tesseract parser to perform OCR
            TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
            String tesseractFolder = TESSERACT_PATH.getParent();
            ocrConfig.setTesseractPath(tesseractFolder);
            // Tesseract expects language data packs to be in a subdirectory of tesseractFolder, in a folder called "tessdata".
            // If they are stored somewhere else, use ocrConfig.setTessdataPath(String tessdataPath) to point to them
            ocrConfig.setLanguage("eng");
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
                throw new TextExtractorException("Unable to extract text: Tika returned empty reader for " + content);
            }
            pushbackReader.unread(read);

            //concatenate parsed content and meta data into a single reader.
            CharSource metaDataCharSource = getMetaDataCharSource(metadata);
            return CharSource.concat(new ReaderCharSource(pushbackReader), metaDataCharSource).openStream();
        } catch (TimeoutException te) {
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.tikaParseTimeout.text", content.getId(), content.getName());
            logWarning(msg, te);
            throw new TextExtractorException(msg, te);
        } catch (TextExtractorException ex) {
            throw ex;
        } catch (Exception ex) {
            KeywordSearch.getTikaLogger().log(Level.WARNING, "Exception: Unable to Tika parse the content" + content.getId() + ": " + content.getName(), ex.getCause()); //NON-NLS
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.exception.tikaParse.msg", content.getId(), content.getName());
            logWarning(msg, ex);
            throw new TextExtractorException(msg, ex);
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

    @Override
    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(Content content, String detectedFormat) {
        if (detectedFormat == null
                || ContentTextExtractor.BLOB_MIME_TYPES.contains(detectedFormat) //any binary unstructured blobs (string extraction will be used)
                || ContentTextExtractor.ARCHIVE_MIME_TYPES.contains(detectedFormat)
                || (detectedFormat.startsWith("video/") && !detectedFormat.equals("video/x-flv")) //skip video other than flv (tika supports flv only) //NON-NLS
                ) {
            return false;
        }
        return TIKA_SUPPORTED_TYPES.contains(detectedFormat);
    }

    @Override
    public boolean isDisabled() {
        return false;
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
