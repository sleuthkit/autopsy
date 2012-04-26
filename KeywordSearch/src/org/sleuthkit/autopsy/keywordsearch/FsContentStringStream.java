package org.sleuthkit.autopsy.keywordsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

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
/**
 * FsContent input string stream reader/converter
 */
public class FsContentStringStream extends InputStream {

    public static enum Encoding {
        ASCII,
    };
    private FsContent content;
    private String encoding;
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
    private boolean stringAtBoundary = false; //if temp has part of string that didn't make it in previous read()
    private static final byte[] oneCharBuf = new byte[1];
    private final int ASCII_CHARS_MIN = 4; //num. of chars needed to qualify as a char string
    private static final String NLS = Character.toString((char)10); //new line
    private static final Logger logger = Logger.getLogger(FsContentStringStream.class.getName());

    /**
     * 
     * @param content to extract strings from
     * @param encoding target encoding, current only ASCII supported
     */
    public FsContentStringStream(FsContent content, Encoding encoding) {
        this.content = content;
        this.encoding = encoding.toString();
        //logger.log(Level.INFO, "FILE: " + content.getParentPath() + "/" + content.getName());
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

        if (stringAtBoundary) {
            //append entire temp string residual from previous read()
            //because qualified string was broken down into 2 parts
            curString.append(tempString); 
            curStringLen += tempStringLen;

            //reset temp
            tempString = new StringBuilder();
            tempStringLen = 0;

            stringAtBoundary = false;
            //there could be more to this string in fscontent/buffer
        }

        boolean isZero = false;
        int newCurLen = curStringLen + tempStringLen;
        while (newCurLen < len) {
            //need to extract more strings
            if (readBufOffset > bytesInReadBuf - 1) {
                //no more bytes to process into strings, read them
                try {
                    bytesInReadBuf = content.read(curReadBuf, contentOffset, READ_BUF_SIZE);
                } catch (TskException ex) {
                    isEOF = true;
                    //have some extracted string, return that, and fail next time
                    if (curStringLen > 0) {
                        int copied = copyToReturn(b, off, len);
                        return copied;
                    } else {
                        return -1; //EOF
                    }
                }
                if (bytesInReadBuf < 1) {
                    isEOF = true;
                    //have some extracted string, return that, and fail next time
                    if (curStringLen > 0) {
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
            if (c == 0 && isZero == false) {
                //allow to skip one zero
                isZero = true;
            }
            else {
                isZero = false;
            }
            if (isUsableChar(c)) {
                tempString.append(c);
                ++tempStringLen;
                //boundary case handled after the loop
            } else if (! isZero) {
                //break the string, clear temp
                if (tempStringLen >= ASCII_CHARS_MIN) {
                    //append entire temp string
                    tempString.append(NLS);
                    ++tempStringLen;

                    curString.append(tempString);
                    curStringLen += tempStringLen;
                }
                //reset temp
                tempString = new StringBuilder();
                tempStringLen = 0;
            }

            newCurLen = curStringLen + tempStringLen;
        }

        //check if temp still has chars to qualify as a string
        //we might need to break up temp into 2 parts for next read() call
        //consume as many as possible to fill entire user buffer
        if (tempStringLen >= ASCII_CHARS_MIN) {
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

                stringAtBoundary = true;

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

    //copy currently extracted string to user buffer
    //and reset for next read() call
    private int copyToReturn(byte[] b, int off, long len) {
        try {
            final String curStringS = curString.toString();
            //logger.log(Level.INFO, curStringS);
            byte[] stringBytes = curStringS.getBytes(encoding);
            System.arraycopy(stringBytes, 0, b, off, Math.min(curStringLen, (int) len));
            //copied all string, reset
            curString = new StringBuilder();
            int ret = curStringLen;
            curStringLen = 0;
            return ret;
        } catch (UnsupportedEncodingException ex) {
            //should not happen
            logger.log(Level.SEVERE, "Bad encoding string: " + encoding, ex);
        }

        return 0;
    }

    private static boolean isUsableChar(char c) {
        return c >= 32 && c <= 126 && c != 9;
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
}