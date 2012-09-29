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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskException;

/**
 * AbstractFile input string stream reader/converter - given AbstractFile, 
 * extract strings from it and return encoded bytes via read()
 * 
 * Note: the utility supports extraction of only LATIN script and UTF8, UTF16LE, UTF16BE encodings
 * and uses a brute force encoding detection - it's fast but could apply multiple encodings on the same string.
 * 
 * For other script/languages support and better encoding detection use AbstractFileStringIntStream streaming class,
 * which wraps around StringExtract extractor.
 */
public class AbstractFileStringStream extends InputStream {

    //args
    private AbstractFile content;
    private Charset outputCharset;
    //internal data
    private long contentOffset = 0; //offset in fscontent read into curReadBuf
    private static final int READ_BUF_SIZE = 256;
    private static final byte[] curReadBuf = new byte[READ_BUF_SIZE];
    private int bytesInReadBuf = 0;
    private int readBufOffset = 0; //offset in read buf processed
    private StringBuilder curString = new StringBuilder();
    private int curStringLen = 0;
    private StringBuilder tempString = new StringBuilder();
    private int tempStringLen = 0;
    private boolean isEOF = false;
    private boolean stringAtTempBoundary = false; //if temp has part of string that didn't make it in previous read()
    private boolean stringAtBufBoundary = false; //if read buffer has string being processed, continue as string from prev read() in next read()
    private boolean inString = false; //if current temp has min chars required
    private static final byte[] oneCharBuf = new byte[1];
    private final int MIN_PRINTABLE_CHARS = 4; //num. of chars needed to qualify as a char string
    private static final String NLS = Character.toString((char) 10); //new line
    private static final Logger logger = Logger.getLogger(AbstractFileStringStream.class.getName());

    /**
     * Construct new string stream from FsContent
     *
     * @param content to extract strings from
     * @param outputCharset target encoding to index as
     * @param preserveOnBuffBoundary whether to preserve or split string on a
     * buffer boundary. If false, will pack into read buffer up to max.
     * possible, potentially splitting a string. If false, the string will be
     * preserved for next read.
     */
    public AbstractFileStringStream(AbstractFile content, Charset outputCharset, boolean preserveOnBuffBoundary) {
        this.content = content;
        this.outputCharset = outputCharset;
        //this.preserveOnBuffBoundary = preserveOnBuffBoundary;
        //logger.log(Level.INFO, "FILE: " + content.getParentPath() + "/" + content.getName());
    }

    /**
     * Construct new string stream from FsContent Do not attempt to fill entire
     * read buffer if that would break a string
     *
     * @param content to extract strings from
     * @param outCharset target charset to encode into bytes and index as, e.g. UTF-8
     */
    public AbstractFileStringStream(AbstractFile content, Charset outCharset) {
        this(content, outCharset, false);
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

        if (isEOF) {
            return -1;
        }


        if (stringAtTempBoundary) {
            //append entire temp string residual from previous read()
            //because qualified string was broken down into 2 parts
            appendResetTemp();

            stringAtTempBoundary = false;
            //there could be more to this string in fscontent/buffer
        }

        boolean singleConsecZero = false; //preserve the current sequence of chars if 1 consecutive zero char
        int newCurLen = curStringLen + tempStringLen;


        while (newCurLen < len) {
            //need to extract more strings
            if (readBufOffset > bytesInReadBuf - 1) {
                //no more bytes to process into strings, read them
                try {
                    bytesInReadBuf = 0;
                    bytesInReadBuf = content.read(curReadBuf, contentOffset, READ_BUF_SIZE);
                } catch (TskException ex) {
                    if (curStringLen > 0 || tempStringLen >= MIN_PRINTABLE_CHARS) {
                        appendResetTemp();
                        //have some extracted string, return that, and fail next time
                        isEOF = true;
                        int copied = copyToReturn(b, off, len);
                        return copied;
                    } else {
                        return -1; //EOF
                    }
                }
                if (bytesInReadBuf < 1) {
                    if (curStringLen > 0 || tempStringLen >= MIN_PRINTABLE_CHARS) {
                        appendResetTemp();
                        //have some extracted string, return that, and fail next time
                        isEOF = true;
                        int copied = copyToReturn(b, off, len);
                        return copied;
                    } else {
                        return -1; //EOF
                    }
                }
                //increment content offset for next read
                contentOffset += bytesInReadBuf;
                //reset read buf position
                readBufOffset = 0;
            }
            //get char from cur read buf
            char c = (char) curReadBuf[readBufOffset++];
            if (c == 0 && singleConsecZero == false) {
                //preserve the current sequence if max consec. 1 zero char 
                singleConsecZero = true;
            } else {
                singleConsecZero = false;
            }
            if (StringExtract.isPrintableAscii(c)) {
                tempString.append(c);
                ++tempStringLen;
                if (tempStringLen >= MIN_PRINTABLE_CHARS) {
                    inString = true;
                }

                //boundary case when temp has still chars - handled after the loop
            } else if (!singleConsecZero) {
                //break the string, clear temp
                if (tempStringLen >= MIN_PRINTABLE_CHARS
                        || stringAtBufBoundary) {
                    //append entire temp string with new line
                    tempString.append(NLS);
                    ++tempStringLen;

                    curString.append(tempString);
                    curStringLen += tempStringLen;

                    stringAtBufBoundary = false;
                }
                //reset temp
                tempString = new StringBuilder();
                tempStringLen = 0;
            }

            newCurLen = curStringLen + tempStringLen;
        }

        //check if still in string state, so that next chars in read buf bypass min chars check
        //and qualify as string even if less < min chars required
        if (inString) {
            inString = false; //reset
            stringAtBufBoundary = true; //will bypass the check
        }

        //check if temp still has chars to qualify as a string
        //we might need to break up temp into 2 parts for next read() call
        //consume as many as possible to fill entire user buffer
        if (tempStringLen >= MIN_PRINTABLE_CHARS) {
            if (newCurLen > len) {
                int appendChars = len - curStringLen;
                //save part for next user read(), need to break up temp string
                //do not append new line
                String toAppend = tempString.substring(0, appendChars);
                String newTemp = tempString.substring(appendChars);

                curString.append(toAppend);
                curStringLen += appendChars;

                tempString = new StringBuilder(newTemp);
                tempStringLen = newTemp.length();

                stringAtTempBoundary = true;

            } else {
                //append entire temp
                curString.append(tempString);
                curStringLen += tempStringLen;

                //reset temp
                tempString = new StringBuilder();
                tempStringLen = 0;

            }
        } else {
            //if temp has a few chars, not qualified as string for now, 
            //will be processed during next read() call
        }

        //copy current strings to user
        final int copied = copyToReturn(b, off, len);
        //there may be still chars in read buffer or  tempString, for next read()

        return copied;
    }

    //append temp buffer to cur string buffer and reset temp, if enough chars
    //does not append new line
    private void appendResetTemp() {
        if (tempStringLen >= MIN_PRINTABLE_CHARS) {
            curString.append(tempString);
            curStringLen += tempStringLen;
            tempString = new StringBuilder();
            tempStringLen = 0;
        }
    }

    //copy currently extracted string to user buffer
    //and reset for next read() call
    private int copyToReturn(byte[] b, int off, long len) {

        final String curStringS = curString.toString();
        //logger.log(Level.INFO, curStringS);
        byte[] stringBytes = curStringS.getBytes(outputCharset);
        System.arraycopy(stringBytes, 0, b, off, Math.min(curStringLen, (int) len));
        //logger.log(Level.INFO, curStringS);
        //copied all string, reset
        curString = new StringBuilder();
        int ret = curStringLen;
        curStringLen = 0;
        return ret;

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
    public int available() throws IOException {
        //we don't know how many bytes in curReadBuf may end up as strings
        return 0;
    }

    @Override
    public long skip(long n) throws IOException {
        //use default implementation that reads into skip buffer
        //but it could be more efficient
        return super.skip(n);
    }
}