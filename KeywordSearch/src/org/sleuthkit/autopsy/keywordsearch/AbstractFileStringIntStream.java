/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractResult;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper over StringExtract to provide streaming API Given AbstractFile
 * object, extract international strings from the file and read output as a
 * stream of UTF-8 strings as encoded bytes.
 */
public class AbstractFileStringIntStream extends InputStream {

    private AbstractFile content;
    private final byte[] oneCharBuf = new byte[1];
    private StringExtract stringExtractor;
    private static final int FILE_BUF_SIZE = 1024 * 1024;
    private static final byte[] fileReadBuff = new byte[FILE_BUF_SIZE]; //NOTE: need to run all stream extraction in same thread
    private int fileReadOffset = 0;
    private byte[] convertBuff; //stores extracted string encoded as bytes, before returned to user
    private int convertBuffOffset = 0; //offset to start returning data to user on next read()
    private int bytesInConvertBuff = 0; //amount of data currently in the buffer
    private boolean fileEOF = false; //if file has more bytes to read
    private Charset outCharset;
    private static final Logger logger = Logger.getLogger(AbstractFileStringIntStream.class.getName());

    /**
     * Constructs new stream object that does convertion from file, to extracted
     * strings, then to byte stream, for specified script auto-detected encoding
     * (UTF8, UTF16LE, UTF16BE), and specified output byte stream encoding
     *
     * @param content
     * @param script
     * @param outCharset
     */
    public AbstractFileStringIntStream(AbstractFile content, StringExtract.StringExtractUnicodeTable.SCRIPT script, Charset outCharset) {
        this.content = content;
        this.stringExtractor = new StringExtract();
        this.stringExtractor.setEnabledScript(script);
        this.outCharset = outCharset;
    }

    @Override
    public int read() throws IOException {
        final int read = read(oneCharBuf, 0, 1);
        if (read == 1) {
            return oneCharBuf[0];
        } else {
            return -1;
        }

    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        long fileSize = content.getSize();
        if (fileSize == 0) {
            return -1;
        }
        

        //read and convert until user buffer full
        //we have data if file can be read or when byteBuff has converted strings to return
        int bytesToUser = 0; //returned to user so far
        while (bytesToUser < len) {
            //check if we have enough converted strings         
            int remain = bytesInConvertBuff - convertBuffOffset;

            if ((convertBuff == null || remain == 0) && !fileEOF && fileReadOffset < fileSize) {
                try {
                    //convert more strings, store in buffer
                    //TODO read repeatadly to ensure we have entire max FILE_BUF_SIZE
                    final long toRead = Math.min(FILE_BUF_SIZE, fileSize - fileReadOffset);
                    int read = content.read(fileReadBuff, fileReadOffset, toRead);
                    if (read == -1 || read == 0) {
                        fileEOF = true;
                    } else {
                        fileReadOffset += read;
                        if (fileReadOffset >= fileSize) {
                            fileEOF = true;
                        }

                        //put converted string in convertBuff
                        convert(read);
                        remain = bytesInConvertBuff - convertBuffOffset;
                    }
                } catch (TskCoreException ex) {
                    //Exceptions.printStackTrace(ex);
                    fileEOF = true;
                }
            }

            //nothing more to read, and no more bytes in convertBuff
            if (convertBuff == null || remain == 0) {
                if (fileEOF) {
                    return bytesToUser > 0 ? bytesToUser : -1;
                } else {
                    //no strings extracted, try another read
                    continue;
                }
            }

            //return part or all of convert buff to user
            final int toCopy = Math.min(remain, len - off);
            System.arraycopy(convertBuff, convertBuffOffset, b, off, toCopy);
            convertBuffOffset += toCopy;

            //TODO ensure that total bytesToUser < len, and save for next read()
            bytesToUser += toCopy;

        }

        return bytesToUser;
    }

    /**
     * convert bytes in file buffer to string, and encode string in
     * convertBuffer
     *
     * @param numBytes num bytes in the fileReadBuff
     */
    private void convert(int numBytes) {
        StringExtractResult ser = stringExtractor.extract(fileReadBuff, numBytes, 0);
        convertBuff = ser.getText().getBytes(outCharset);

        //reset tracking vars
        if (ser.getNumBytes() == 0) {
            bytesInConvertBuff = 0;
        } else {
            bytesInConvertBuff = convertBuff.length;
        }
        convertBuffOffset = 0;
    }
}
