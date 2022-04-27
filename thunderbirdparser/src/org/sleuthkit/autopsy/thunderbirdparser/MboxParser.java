/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.thunderbirdparser;

import java.io.BufferedInputStream;
import java.io.CharConversionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.mboxiterator.CharBufferWrapper;
import org.apache.james.mime4j.mboxiterator.MboxIterator;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.james.mime4j.mboxiterator.MboxIterator.Builder;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * An Iterator for parsing mbox files. Wraps an instance of MBoxEmailIterator.
 */
class MboxParser extends MimeJ4MessageParser implements Iterator<EmailMessage> {

    private static final Logger logger = Logger.getLogger(MboxParser.class.getName());

    private Iterator<EmailMessage> emailIterator = null;
    
    private MboxIterator mboxIterable;

    private MboxParser(String localPath) {
        setLocalPath(localPath);
    }

    static boolean isValidMimeTypeMbox(byte[] buffer, AbstractFile abstractFile) {
        String mboxHeaderLine = new String(buffer);
        if (mboxHeaderLine.startsWith("From ")) {
            String mimeType = abstractFile.getMIMEType();
        
            // if it is not present, attempt to use the FileTypeDetector to determine
            if (mimeType == null || mimeType.isEmpty()) {
                FileTypeDetector fileTypeDetector = null;
                try {
                    fileTypeDetector = new FileTypeDetector();
                } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                    logger.log(Level.WARNING, String.format("Unable to create file type detector for determining MIME type for file %s with id of %d", abstractFile.getName(), abstractFile.getId()));
                    return false;
                }
                mimeType = fileTypeDetector.getMIMEType(abstractFile);
            } 
            if (mimeType.equalsIgnoreCase("application/mbox")) {
                return true;
            }
        }
        return false; //NON-NLS
    }

    /**
     * Returns an instance of MBoxParser that will iterate and return
     * EMailMessage objects with only the information needed for threading
     * emails.
     *
     * @param localPath String path to the mboxFile
     * @param mboxFile  The mboxFile to parse
     *
     * @return Instance of MboxParser
     */
    static MboxParser getThreadInfoIterator(String localPath, File mboxFile) {
        MboxParser parser = new MboxParser(localPath);
        parser.createIterator(mboxFile, 0, false);
        return parser;
    }

    /**
     * Returns an instance of MBoxParser that will iterate "whole"
     * EmailMessages.
     *
     * @param localPath String path to the mboxFile
     * @param mboxFile  The mboxFile to parse
     * @param fileID    The fileID of the abstractFile that mboxFile was found
     *
     * @return Instance of MboxParser
     */
    static MboxParser getEmailIterator(String localPath, File mboxFile, long fileID) {
        MboxParser parser = new MboxParser(localPath);
        parser.createIterator(mboxFile, fileID, true);

        return parser;
    }

    /**
     * Creates the real Iterator object instance.
     *
     * @param mboxFile The mboxFile to parse
     * @param fileID   The fileID of the abstractFile that mboxFile was found
     * @param wholeMsg True if EmailMessage should have the whole message, not
     *                 just the thread information.
     */
    private void createIterator(File mboxFile, long fileID, boolean wholeMsg) {
        // Detect possible charsets
        List<CharsetEncoder> encoders = getPossibleEncoders(mboxFile);

        // Loop through the possible encoders and find the first one that works.
        // That will usually be one of the first ones.
        for (CharsetEncoder encoder : encoders) {
            try {
                mboxIterable = MboxIterator
                        .fromFile(mboxFile)
                        // use more permissive from line from mbox iterator 0.8.0, but handling CRLF/LF
                        .fromLine("^From .*\r?\n")
                        .charset(encoder.charset())
                        .build();
                if (mboxIterable != null) {
                    emailIterator = new MBoxEmailIterator(mboxIterable.iterator(), encoder, fileID, wholeMsg);
                }
                break;
            } catch (CharConversionException | UnsupportedCharsetException ex) {
                // Not the right encoder
            } catch (IllegalArgumentException ex) {
                // Not the right encoder
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Failed to open mbox file: %s %d", mboxFile.getName(), fileID), ex); //NON-NLS
                addErrorMessage(NbBundle.getMessage(this.getClass(), "MboxParser.parse.errMsg.failedToReadFile"));
            }
        }
    }

    @Override
    public boolean hasNext() {
        return emailIterator != null && emailIterator.hasNext();
    }

    @Override
    public EmailMessage next() {
        return emailIterator != null ? emailIterator.next() : null;
    }
    
    @Override
    public void close() throws IOException{
        if(mboxIterable != null) {
            mboxIterable.close();
        }
    }

    /**
     * Get a list of the possible encoders for the given mboxFile using Tika's
     * CharsetDetector. At a minimum, returns the standard built in charsets.
     *
     * @param mboxFile
     *
     * @return
     */
    private List<CharsetEncoder> getPossibleEncoders(File mboxFile) {
        InputStream is;
        List<CharsetEncoder> possibleEncoders = new ArrayList<>();

        possibleEncoders.add(StandardCharsets.ISO_8859_1.newEncoder());
        possibleEncoders.add(StandardCharsets.US_ASCII.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_16.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_16BE.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_16LE.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_8.newEncoder());

        try {
            is = new BufferedInputStream(new FileInputStream(mboxFile));
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "Failed to find mbox file while detecting charset"); //NON-NLS
            return possibleEncoders;
        }

        try {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(is);
            CharsetMatch[] matches = detector.detectAll();
            for (CharsetMatch match : matches) {
                try {
                    possibleEncoders.add(Charset.forName(match.getName()).newEncoder());
                } catch (UnsupportedCharsetException | IllegalCharsetNameException ex) {
                    // Don't add unsupported charsets to the list
                }
            }
            return possibleEncoders;
        } catch (IOException | IllegalArgumentException ex) {
            logger.log(Level.WARNING, "Failed to detect charset of mbox file.", ex); //NON-NLS
            return possibleEncoders;
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close input stream"); //NON-NLS
            }
        }
    }
    
    /**
     * An Interator for mbox email messages.
     */
    final class MBoxEmailIterator implements Iterator<EmailMessage> {

        private final Iterator<CharBufferWrapper> mboxIterator;
        private final CharsetEncoder encoder;
        private final long fileID;
        private final boolean wholeMsg;

        MBoxEmailIterator(Iterator<CharBufferWrapper> mboxIter, CharsetEncoder encoder, long fileID, boolean wholeMsg) {
            mboxIterator = mboxIter;
            this.encoder = encoder;
            this.fileID = fileID;
            this.wholeMsg = wholeMsg;
        }

        @Override
        public boolean hasNext() {
            return (mboxIterator != null && encoder != null) && mboxIterator.hasNext();
        }

        @Override
        public EmailMessage next() {
            CharBufferWrapper messageBuffer = mboxIterator.next();

            try {
                Message msg = getMessageBuilder().parseMessage(messageBuffer.asInputStream(encoder.charset()));
                if (wholeMsg) {
                    return extractEmail(msg, getLocalPath(), fileID);
                } else {
                    return extractPartialEmail(msg);
                }
            } catch (RuntimeException | IOException ex) {
                logger.log(Level.WARNING, "Failed to get message from mbox: {0}", ex.getMessage()); //NON-NLS
            }
            return null;
        }

    }
}
