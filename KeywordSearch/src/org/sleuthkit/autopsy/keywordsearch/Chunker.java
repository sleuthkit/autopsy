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

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.concurrent.NotThreadSafe;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.keywordsearch.Chunker.Chunk;

/**
 * Encapsulates the content chunking algorithm in an implementation of the
 * Iterator interface. Also implements Iterable so it can be used directly in a
 * for loop. The base chunk is the part of the chunk before the overlapping
 * window. The window will be included at the end of the current chunk as well
 * as at the beginning of the next chunk.
 */
@NotThreadSafe
class Chunker implements Iterator<Chunk>, Iterable<Chunk> {

    //local references to standard encodings
    private static final Charset UTF_16 = StandardCharsets.UTF_16;
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    //Chunking algorithm paramaters-------------------------------------//
    /**
     * the maximum size of a chunk, including the window.
     */
    private static final int MAX_TOTAL_CHUNK_SIZE = 32760; //bytes
    /**
     * the minimum to read before we start the process of looking for whitespace
     * to break at and creating an overlapping window.
     */
    private static final int MINIMUM_BASE_CHUNK_SIZE = 30 * 1024; //bytes
    /**
     * The maximum size of the chunk, before the overlapping window, even if we
     * couldn't find whitespace to break at.
     */
    private static final int MAXIMUM_BASE_CHUNK_SIZE = 31 * 1024; //bytes
    /**
     * The amount of text we will read through before we give up on finding
     * whitespace to break the chunk/window at.
     */
    private static final int WHITE_SPACE_BUFFER_SIZE = 512; //bytes
    /**
     * The number of characters to read in one go from the Reader.
     */
    private static final int READ_CHARS_BUFFER_SIZE = 512; //chars
    /**
     * When toLowerCase() is called on a character, the lower cased output 
     * can be different in size than the original input. I have seen a single
     * input character turn into 3 characters (and 5 bytes) after lowercasing. 
     * I could not find any info as to what is the upper limit of how much a 
     * character can "increase in size" during lower casing. I'm guestimating
     * and setting that limit at 10 bytes.
     */
    private static final int MAX_CHAR_SIZE_INCREASE_IN_BYTES = 10; //bytes

    ////chunker state--------------------------------------------///
    /**
     * The Reader that this chunk reads from, and divides into chunks. It must
     * be a buffered reader to ensure that mark/reset are supported.
     */
    private final PushbackReader reader;
    /**
     * The local buffer of characters read from the Reader.
     */
    private final char[] tempChunkBuf = new char[READ_CHARS_BUFFER_SIZE];

    /**
     * the size in bytes of the chunk (so far).
     */
    private int chunkSizeBytes = 0;

    /**
     * the size in bytes of the lowercased chunk (so far). Note that lowercasing
     * in Java can change the size of the string so we need to make sure the
     * lowercased string also fits in MAX_TOTAL_CHUNK_SIZE.
     */
    private int lowerCasedChunkSizeBytes = 0;
    /**
     * Has the chunker reached the end of the Reader? If so, there are no more
     * chunks, and the current chunk does not need a window.
     */
    private boolean endOfReaderReached = false;
    /**
     * Store any exception encountered reading from the Reader.
     */
    private Exception ex;

    /**
     * Create a Chunker that will chunk the content of the given Reader.
     *
     * @param reader The content to chunk.
     */
    Chunker(Reader reader) {
        //Using MAX_TOTAL_CHUNK_SIZE is safe but probably overkill.
        this.reader = new PushbackReader(reader, MAX_TOTAL_CHUNK_SIZE);
    }

    @Override
    public Iterator<Chunk> iterator() {
        return this;
    }

    /**
     * Has this Chunker encountered an exception reading from the Reader?
     *
     *
     * @return True if this Chunker encountered an exception.
     */
    boolean hasException() {
        return ex != null;
    }

    /**
     * Get the exception encountered reading from the Reader.
     *
     * @return The exception, or null if no exception was encountered.
     */
    public Exception getException() {
        return ex;
    }

    @Override
    public boolean hasNext() {
        return (ex == null)
                && (endOfReaderReached == false);
    }

    /**
     * Sanitize the given StringBuilder by replacing non-UTF-8 characters with
     * caret '^'
     *
     * @param sb the StringBuilder to sanitize
     *
     * //JMTODO: use Charsequence.chars() or codePoints() and then a mapping
     * function?
     */
    private static StringBuilder sanitizeToUTF8(StringBuilder sb) {
        final int length = sb.length();
        for (int i = 0; i < length; i++) {
            if (TextUtil.isValidSolrUTF8(sb.charAt(i)) == false) {
                sb.replace(i, i + 1, "^");
            }
        }
        return sb;
    }

    /**
     * Cleanup invalid codepoint sequences by replacing them with the default
     * replacement character: U+FFFD / �.
     *
     * @param s The string to cleanup.
     *
     * @return A StringBuilder with the same content as s but where all invalid
     * code * points have been replaced.
     */
    private static StringBuilder replaceInvalidUTF16(String s) {
        /* encode the string to UTF-16 which does the replcement, see
         * Charset.encode(), then decode back to a StringBuilder. */
        return new StringBuilder(UTF_16.decode(UTF_16.encode(s)));
    }

    /**
     * Wrapper method that performs UTF-8 string sanitization.
     *
     * @param s String to be sanitized.
     *
     * @return Sanitized string.
     */
    static StringBuilder sanitize(String s) {
        String normStr = Normalizer.normalize(s, Normalizer.Form.NFKC);
        return sanitizeToUTF8(replaceInvalidUTF16(normStr));
    }

    @Override
    public Chunk next() {
        if (hasNext() == false) {
            throw new NoSuchElementException("There are no more chunks.");
        }
        //reset state for the next chunk

        chunkSizeBytes = 0;
        lowerCasedChunkSizeBytes = 0;
        int baseChunkSizeChars = 0;
        StringBuilder currentChunk = new StringBuilder();
        StringBuilder currentWindow = new StringBuilder();
        StringBuilder lowerCasedChunk = new StringBuilder();

        try {
            readBaseChunk(currentChunk, lowerCasedChunk);
            baseChunkSizeChars = currentChunk.length(); //save the base chunk length
            readWindow(currentWindow, lowerCasedChunk);
            //add the window text to the current chunk.
            currentChunk.append(currentWindow);
            if (endOfReaderReached) {
                /* if we have reached the end of the content,we won't make
                 * another overlapping chunk, so the length of the base chunk
                 * can be extended to the end. */
                baseChunkSizeChars = currentChunk.length();
            } else {
                /* otherwise we will make another chunk, so unread the window */
                reader.unread(currentWindow.toString().toCharArray());
            }
        } catch (Exception ioEx) {
            /* Save the exception, which will cause hasNext() to return false,
             * and break any chunking loop in client code. */
            ex = ioEx;
        }

        //sanitize the text and return a Chunk object, that includes the base chunk length.
        return new Chunk(currentChunk, baseChunkSizeChars, lowerCasedChunk);
    }

    /**
     * Read the base chunk from the reader, attempting to break at whitespace.
     *
     * @throws IOException if there is a problem reading from the reader.
     */
    private void readBaseChunk(StringBuilder currentChunk, StringBuilder lowerCasedChunk) throws IOException {
        //read the chunk until the minimum base chunk size
        readHelper(MINIMUM_BASE_CHUNK_SIZE, currentChunk, lowerCasedChunk);

        //keep reading until the maximum base chunk size or white space is reached.
        readToWhiteSpaceHelper(MAXIMUM_BASE_CHUNK_SIZE, currentChunk, lowerCasedChunk);
    }

    /**
     * Read the window from the reader, attempting to break at whitespace.
     *
     * @throws IOException if there is a problem reading from the reader.
     */
    private void readWindow(StringBuilder currentChunk, StringBuilder lowerCasedChunk) throws IOException {
        //read the window, leaving some room to look for white space to break at.
        readHelper(MAX_TOTAL_CHUNK_SIZE - WHITE_SPACE_BUFFER_SIZE, currentChunk, lowerCasedChunk);

        //keep reading until the max chunk size, or until whitespace is reached.
        readToWhiteSpaceHelper(MAX_TOTAL_CHUNK_SIZE, currentChunk, lowerCasedChunk);
    }

    /**
     * Read until the maxBytes reached, or end of reader.
     *
     * @param maxBytes
     * @param currentSegment
     *
     * @throws IOException
     */
    private void readHelper(int maxBytes, StringBuilder currentSegment, StringBuilder currentLowerCasedSegment) throws IOException {
        int charsRead = 0;
        //read chars up to maxBytes, or the end of the reader.
        while ((chunkSizeBytes < maxBytes) && (lowerCasedChunkSizeBytes < maxBytes)
                && (endOfReaderReached == false)) {
            charsRead = reader.read(tempChunkBuf, 0, READ_CHARS_BUFFER_SIZE);
            if (-1 == charsRead) {
                //this is the last chunk
                endOfReaderReached = true;
                return;
            } else {
                //if the last char might be part of a surroate pair, unread it.
                final char lastChar = tempChunkBuf[charsRead - 1];
                if (Character.isHighSurrogate(lastChar)) {
                    charsRead--;
                    reader.unread(lastChar);
                }

                //cleanup any invalid utf-16 sequences
                StringBuilder chunkSegment = sanitize(new String(tempChunkBuf, 0, charsRead));

                //get the length in utf8 bytes of the read chars
                int segmentSize = chunkSegment.toString().getBytes(UTF_8).length;

                // lower case the string and get it's size. NOTE: lower casing can 
                // change the size of the string!
                String lowerCasedSegment = chunkSegment.toString().toLowerCase();
                int lowerCasedSegmentSize = lowerCasedSegment.getBytes(UTF_8).length;

                //if it will not put us past maxBytes
                if ((chunkSizeBytes + segmentSize < maxBytes) && (lowerCasedChunkSizeBytes + lowerCasedSegmentSize < maxBytes)) {
                    //add it to the chunk
                    currentSegment.append(chunkSegment);
                    chunkSizeBytes += segmentSize;

                    currentLowerCasedSegment.append(lowerCasedSegment);
                    lowerCasedChunkSizeBytes += lowerCasedSegmentSize;
                } else {
                    //unread it, and break out of read loop.
                    reader.unread(tempChunkBuf, 0, charsRead);
                    return;
                }
            }
        }
    }

    /**
     * Read until the maxBytes reached, whitespace, or end of reader.
     *
     * @param maxBytes
     * @param currentChunk
     *
     * @throws IOException
     */
    private void readToWhiteSpaceHelper(int maxBytes, StringBuilder currentChunk, StringBuilder lowerCasedChunk) throws IOException {
        int charsRead = 0;
        boolean whitespaceFound = false;
        //read 1 char at a time up to maxBytes, whitespaceFound, or we reach the end of the reader.
        while ((chunkSizeBytes < maxBytes - MAX_CHAR_SIZE_INCREASE_IN_BYTES) 
                && (lowerCasedChunkSizeBytes < maxBytes - MAX_CHAR_SIZE_INCREASE_IN_BYTES)
                && (whitespaceFound == false)
                && (endOfReaderReached == false)) {
            charsRead = reader.read(tempChunkBuf, 0, 1);
            if (-1 == charsRead) {
                //this is the last chunk
                endOfReaderReached = true;
                return;
            } else {
                //if the last charcter might be part of a surroate pair, read another char
                final char ch = tempChunkBuf[0];
                String chunkSegment;
                if (Character.isHighSurrogate(ch)) {
                    //read another char into the buffer.
                    int surrogateCharsRead = reader.read(tempChunkBuf, 1, 1);
                    charsRead += surrogateCharsRead;
                    if (surrogateCharsRead == -1) {
                        //this is the last chunk, so just drop the unpaired surrogate
                        endOfReaderReached = true;
                        return;
                    } else {
                        //try to use the pair together.
                        chunkSegment = new String(tempChunkBuf, 0, 2);
                    }
                } else {
                    //one char
                    chunkSegment = new String(tempChunkBuf, 0, 1);
                }

                //cleanup any invalid utf-16 sequences
                StringBuilder sanitizedChunkSegment = sanitize(chunkSegment);
                //get the length in utf8 bytes of the read chars
                int segmentSize = chunkSegment.getBytes(UTF_8).length;
                
                // lower case the string and get it's size. NOTE: lower casing can 
                // change the size of the string.
                String lowerCasedSegment = sanitizedChunkSegment.toString().toLowerCase();
                int lowerCasedSegmentSize = lowerCasedSegment.getBytes(UTF_8).length;
                
                //if it will not put us past maxBytes
                if ((chunkSizeBytes + segmentSize < maxBytes - MAX_CHAR_SIZE_INCREASE_IN_BYTES)
                        && (lowerCasedChunkSizeBytes + lowerCasedSegmentSize < maxBytes - MAX_CHAR_SIZE_INCREASE_IN_BYTES)) {

                    //add read chars to the chunk and update the length.
                    currentChunk.append(sanitizedChunkSegment);
                    chunkSizeBytes += segmentSize;

                    lowerCasedChunk.append(lowerCasedSegment);
                    lowerCasedChunkSizeBytes += lowerCasedSegmentSize;
                    
                    //check for whitespace.
                    whitespaceFound = Character.isWhitespace(sanitizedChunkSegment.codePointAt(0));
                } else {
                    //unread it, and break out of read loop.
                    reader.unread(tempChunkBuf, 0, charsRead);
                    return;
                }
            }
        }
    }

    /**
     * Represents one chunk as the text in it and the length of the base chunk,
     * in chars.
     */
    static class Chunk {

        private final StringBuilder sb;
        private final int baseChunkSizeChars;
        private final StringBuilder lowerCasedChunk;

        Chunk(StringBuilder sb, int baseChunkSizeChars, StringBuilder lowerCasedChunk) {
            this.sb = sb;
            this.baseChunkSizeChars = baseChunkSizeChars;
            this.lowerCasedChunk = lowerCasedChunk;
        }

        /**
         * Get the content of the original (non-lower cased) chunk.
         *
         * @return The content of the chunk.
         */
        @Override
        public String toString() {
            return sb.toString();
        }

        /**
         * Get the content of the lower cased chunk.
         *
         * @return The content of the chunk.
         */
        public String geLowerCasedChunk() {
            return lowerCasedChunk.toString();
        }

        /**
         * Get the length of the original (non-lower cased) base chunk in java chars.
         *
         * @return the length of the base chunk in java chars.
         */
        int getBaseChunkLength() {
            return baseChunkSizeChars;
        }
    }
}
