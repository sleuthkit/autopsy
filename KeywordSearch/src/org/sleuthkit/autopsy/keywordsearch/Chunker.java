/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import com.google.common.base.Utf8;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.concurrent.NotThreadSafe;
import org.sleuthkit.autopsy.coreutils.TextUtil;

/**
 * Encapsulates the content chunking algorithm in an implementation of the
 * Iterator interface. Also implements Iterable so it can be used directly in a
 * for loop. The base chunk is the part of the chunk before the overlapping
 * window. The window will be included at the end of the current chunk as well
 * as at the beginning of the next chunk.
 */
@NotThreadSafe
class Chunker implements Iterator<Chunk>, Iterable<Chunk> {

    //Chunking algorithm paramaters-------------------------------------//
    /** the maximum size of a chunk, including the window. */
    private static final int MAX_TOTAL_CHUNK_SIZE = 32766; //bytes
    /** the minimum to read before we start the process of looking for
     * whitespace to break at and creating an overlapping window. */
    private static final int MINIMUM_BASE_CHUNK_SIZE = 30 * 1024; //bytes
    /** The maximum size of the chunk, before the overlapping window, even if we
     * couldn't find whitespace to break at. */
    private static final int MAXIMUM_BASE_CHUNK_SIZE = 31 * 1024; //bytes
    /** The amount of text we will read through before we give up on finding
     * whitespace to break the chunk/window at. */
    private static final int WHITE_SPACE_BUFFER_SIZE = 512; //bytes
    /** The number of characters to read in one go from the Reader. */
    private static final int READ_CHARS_BUFFER_SIZE = 512; //chars

    ////chunker state--------------------------------------------///
    /** The Reader that this chunk reads from, and divides into chunks. It must
     * be a buffered reader to ensure that mark/reset are supported. */
    private final PushbackReader reader;
    /** The local buffer of characters read from the Reader. */
    private final char[] tempChunkBuf = new char[READ_CHARS_BUFFER_SIZE];
    /** number of chars read in the most recent read operation. */
    private int charsRead = 0;

    /** The text of the current chunk (so far). */
    private StringBuilder currentChunk;
    private StringBuilder currentWindow;
    /** the size in bytes of the chunk (so far). */
    private int chunkSizeBytes = 0;
    /** the size in chars of the (base) chunk (so far). */
    private int baseChunkSizeChars;

    /** has the chunker found whitespace to break on? */
    private boolean whitespaceFound = false;
    /** has the chunker reached the end of the Reader? If so, there are no more
     * chunks, and the current chunk does not need a window. */
    private boolean endOfReaderReached = false;

    /**
     * Create a Chunker that will chunk the content of the given Reader.
     *
     * @param reader The content to chunk.
     */
    Chunker(Reader reader) {
        this.reader = new PushbackReader(reader, 2048);
    }

    @Override
    public Iterator<Chunk> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return endOfReaderReached == false;
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

    @Override
    public Chunk next() {
        if (endOfReaderReached) {
            throw new NoSuchElementException("There are no more chunks.");
        }
        //reset state for the next chunk
        currentChunk = new StringBuilder();
        currentWindow = new StringBuilder();
        chunkSizeBytes = 0;
        baseChunkSizeChars = 0;

        try {
            readBaseChunk();
            baseChunkSizeChars = currentChunk.length();
//            reader.mark(2048); //mark the reader so we can rewind the reader here to begin the next chunk
            readWindow();

        } catch (IOException ioEx) {
            throw new RuntimeException("IOException while reading chunk.", ioEx);
        }
        try {
            reader.unread(currentWindow.toString().toCharArray());
//            reader.reset(); //reset the reader the so the next chunk can begin at the position marked above
        } catch (IOException ex) {
            throw new RuntimeException("IOException while resetting chunk reader.", ex);
        }

        if (endOfReaderReached) {
            /* if we have reached the end of the content,we won't make another
             * overlapping chunk, so the base chunk can be extended to the end. */
            baseChunkSizeChars = currentChunk.length();
        }
        //sanitize the text and return a Chunk object, that includes the base chunk length.
        return new Chunk(sanitizeToUTF8(currentChunk), baseChunkSizeChars);
    }

    /**
     * Read the base chunk from the reader, and attempt to break at whitespace.
     *
     * @throws IOException if there is a problem reading from the reader.
     */
    private void readBaseChunk() throws IOException {
        //read the chunk until the minimum base chunk size
        readHelper(MINIMUM_BASE_CHUNK_SIZE - 1024, false, currentChunk);
        //keep reading until the maximum base chunk size or white space is reached.
        whitespaceFound = false;
        readHelper(MAXIMUM_BASE_CHUNK_SIZE - 1024, true, currentChunk);

    }

    /**
     * Read the window from the reader, and attempt to break at whitespace.
     *
     * @throws IOException if there is a problem reading from the reader.
     */
    private void readWindow() throws IOException {
        //read the window, leaving some room to look for white space to break at.
        int windowEnd = Math.min(MAX_TOTAL_CHUNK_SIZE - WHITE_SPACE_BUFFER_SIZE - 1024, chunkSizeBytes + 1024);
        readHelper(windowEnd, false, currentWindow);
        whitespaceFound = false;
        //keep reading until the max chunk size, or until whitespace is reached.
        windowEnd = Math.min(MAX_TOTAL_CHUNK_SIZE - 1024, chunkSizeBytes + 1024);
        readHelper(windowEnd, true, currentWindow);
    }

    /** Helper method that implements reading in a loop.
     *
     * @param maxBytes           The max cummulative length of the content,in
     *                           bytes, to read from the Reader. That is, when
     *                           chunkSizeBytes >= maxBytes stop reading.
     * @param inWhiteSpaceBuffer Should the current read stop once whitespace is
     *                           found?
     *
     * @throws IOException If there is a problem reading from the Reader.
     */
    private void readHelper(int maxBytes, boolean inWhiteSpaceBuffer, StringBuilder currentSegment) throws IOException {
        //only read one character at a time if we are looking for whitespace.
        final int readSize = inWhiteSpaceBuffer ? 1 : READ_CHARS_BUFFER_SIZE;

        //read chars up to maxBytes, whitespaceFound if also inWhiteSpaceBuffer, or we reach the end of the reader.
        while ((chunkSizeBytes < maxBytes)
                && (false == (inWhiteSpaceBuffer && whitespaceFound))
                && (endOfReaderReached == false)) {
            charsRead = reader.read(tempChunkBuf, 0, readSize);
            if (-1 == charsRead) {
                //this is the last chunk
                endOfReaderReached = true;
            } else {

                //add read chars to the chunk and update the length.
                String chunkSegment = new String(tempChunkBuf, 0, charsRead);
                final int segmentSize = Utf8.encodedLength(chunkSegment);

                if (chunkSizeBytes + segmentSize < maxBytes) {
                    chunkSizeBytes += segmentSize;
                    currentSegment.append(chunkSegment);
                } else {
                    for (int i = 0; i < charsRead; i++) {
                        final Character character = tempChunkBuf[i];
                        int charSize = Utf8.encodedLength(character.toString());
                        if (chunkSizeBytes + charSize < maxBytes
                                && (false == (inWhiteSpaceBuffer && whitespaceFound))) {
                            currentSegment.append(character);
                            chunkSizeBytes += charSize;
                            if (inWhiteSpaceBuffer) {
                                //check for whitespace.
                                whitespaceFound = Character.isWhitespace(character);
                            }
                        } else {
                            reader.unread(tempChunkBuf, i, charsRead - i);
                            break;
                        }
                    }
                }
            }
        }
    }
}

/**
 * Represents one chunk as the text in it and the length of the base chunk, in
 * chars.
 */
class Chunk {

    private final StringBuilder sb;
    private final int chunksize;

    Chunk(StringBuilder sb, int baseChunkLength) {
        this.sb = sb;
        this.chunksize = baseChunkLength;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    int getBaseChunkLength() {
        return chunksize;
    }
}
