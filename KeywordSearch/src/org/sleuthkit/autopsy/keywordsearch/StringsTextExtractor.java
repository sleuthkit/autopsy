/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * TextExtractor that extracts raw strings from an AbstractFile.
 */
class StringsTextExtractor extends FileTextExtractor {

    /**
     * Options for this extractor
     */
    enum ExtractOptions {
        EXTRACT_UTF16, ///< extract UTF16 text, true/false
        EXTRACT_UTF8, ///< extract UTF8 text, true/false
    };
    private final List<SCRIPT> extractScripts = new ArrayList<>();
    private Map<String, String> extractOptions = new HashMap<>();

    public StringsTextExtractor() {
        //LATIN_2 is the default script
        extractScripts.add(SCRIPT.LATIN_2);
    }

    /**
     * Sets the scripts to use for the extraction
     *
     * @param extractScripts scripts to use
     */
    public void setScripts(List<SCRIPT> extractScripts) {
        this.extractScripts.clear();
        this.extractScripts.addAll(extractScripts);
    }

    /**
     * Get the currently used scripts for extraction
     *
     * @return scripts currently used or null if not supported
     */
    public List<SCRIPT> getScripts() {
        return new ArrayList<>(extractScripts);
    }

    /**
     * Get current options
     *
     * @return currently used, extractor specific options, or null of not
     *         supported
     */
    public Map<String, String> getOptions() {
        return extractOptions;
    }

    /**
     * Set extractor specific options
     *
     * @param options options to use
     */
    public void setOptions(Map<String, String> options) {
        this.extractOptions = options;
    }

    @Override
    boolean isDisabled() {
        boolean extractUTF8 = Boolean.parseBoolean(extractOptions.get(ExtractOptions.EXTRACT_UTF8.toString()));
        boolean extractUTF16 = Boolean.parseBoolean(extractOptions.get(ExtractOptions.EXTRACT_UTF16.toString()));

        return extractUTF8 == false && extractUTF16 == false;
    }

    @Override
    InputStreamReader getReader(final InputStream stringStream, AbstractFile sourceFile) throws Ingester.IngesterException {
        return new InputStreamReader(stringStream, Server.DEFAULT_INDEXED_TEXT_CHARSET);
    }

    @Override
    InputStream getInputStream(AbstractFile sourceFile) {
        //check which extract stream to use
        if (extractScripts.size() == 1 && extractScripts.get(0).equals(SCRIPT.LATIN_1)) {
            return new EnglishOnlyStream(sourceFile);//optimal for english, english only
        } else {
            boolean extractUTF8 = Boolean.parseBoolean(extractOptions.get(ExtractOptions.EXTRACT_UTF8.toString()));
            boolean extractUTF16 = Boolean.parseBoolean(extractOptions.get(ExtractOptions.EXTRACT_UTF16.toString()));

            return new InternationalStream(sourceFile, extractScripts, extractUTF8, extractUTF16);
        }
    }

    @Override
    public boolean isContentTypeSpecific() {
        return false;
    }

    @Override
    public boolean isSupported(AbstractFile file, String detectedFormat) {
        // strings can be run on anything. 
        return true;
    }

    /**
     * AbstractFile input string stream reader/converter - given AbstractFile,
     * extract strings from it and return encoded bytes via read()
     *
     * Note: the utility supports extraction of only LATIN script and UTF8,
     * UTF16LE, UTF16BE encodings and uses a brute force encoding detection -
     * it's fast but could apply multiple encodings on the same string.
     *
     * For other script/languages support and better encoding detection use
     * AbstractFileStringIntStream streaming class, which wraps around
     * StringExtract extractor.
     */
    private static class EnglishOnlyStream extends InputStream {

        private static final Logger logger = Logger.getLogger(EnglishOnlyStream.class.getName());
        private static final String NLS = Character.toString((char) 10); //new line
        private static final int READ_BUF_SIZE = 256;
        private static final int MIN_PRINTABLE_CHARS = 4; //num. of chars needed to qualify as a char string

        //args
        private final AbstractFile content;

        //internal working data
        private long contentOffset = 0; //offset in fscontent read into curReadBuf
        private final byte[] curReadBuf = new byte[READ_BUF_SIZE];
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
        private final byte[] oneCharBuf = new byte[1];

        /**
         * Construct new string stream from FsContent Do not attempt to fill
         * entire read buffer if that would break a string
         *
         * @param content       to extract strings from
         * @param outputCharset target charset to encode into bytes and index
         *                      as, e.g. UTF-8
         *
         */
        private EnglishOnlyStream(AbstractFile content) {
            this.content = content;
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
                    if (tempStringLen >= MIN_PRINTABLE_CHARS || stringAtBufBoundary) {
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
            byte[] stringBytes = curStringS.getBytes(Server.DEFAULT_INDEXED_TEXT_CHARSET);
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

    /**
     * Wrapper over StringExtract to provide streaming API Given AbstractFile
     * object, extract international strings from the file and read output as a
     * stream of UTF-8 strings as encoded bytes.
     *
     */
    private static class InternationalStream extends InputStream {

        private static final Logger logger = Logger.getLogger(InternationalStream.class.getName());
        private static final int FILE_BUF_SIZE = 1024 * 1024;
        private final AbstractFile content;
        private final byte[] oneCharBuf = new byte[1];
        private final StringExtract stringExtractor;
        /** true if there is nothing to do because neither extractUTF8 nor
         * extractUTF16 was true in constructor */
        private final boolean nothingToDo;
        private final byte[] fileReadBuff = new byte[FILE_BUF_SIZE];
        private long fileReadOffset = 0L;
        private byte[] convertBuff; //stores extracted string encoded as bytes, before returned to user
        private int convertBuffOffset = 0; //offset to start returning data to user on next read()
        private int bytesInConvertBuff = 0; //amount of data currently in the buffer
        private boolean fileEOF = false; //if file has more bytes to read
        private StringExtract.StringExtractResult lastExtractResult;

        /**
         * Constructs new stream object that does conversion from file, to
         * extracted strings, then to byte stream, for specified script,
         * auto-detected encoding (UTF8, UTF16LE, UTF16BE), and specified output
         * byte stream encoding
         *
         * @param content      input content to process and turn into a stream
         *                     to convert into strings
         * @param scripts      a list of scripts to consider
         * @param extractUTF8  whether to extract utf8 encoding
         * @param extractUTF16 whether to extract utf16 encoding
         */
        private InternationalStream(AbstractFile content, List<SCRIPT> scripts, boolean extractUTF8, boolean extractUTF16) {
            this.content = content;
            this.stringExtractor = new StringExtract();
            this.stringExtractor.setEnabledScripts(scripts);
            this.nothingToDo = extractUTF8 == false && extractUTF16 == false;
            this.stringExtractor.setEnableUTF8(extractUTF8);
            this.stringExtractor.setEnableUTF16(extractUTF16);
        }

        @Override
        public int read() throws IOException {
            if (nothingToDo) {
                return -1;
            }
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
            if (nothingToDo) {
                return -1;
            }
            long fileSize = content.getSize();
            if (fileSize == 0) {
                return -1;
            }
            //read and convert until user buffer full
            //we have data if file can be read or when byteBuff has converted strings to return
            int bytesToUser = 0; //returned to user so far
            int offsetUser = off;
            while (bytesToUser < len && offsetUser < len) {
                //check if we have enough converted strings
                int convertBuffRemain = bytesInConvertBuff - convertBuffOffset;
                if ((convertBuff == null || convertBuffRemain == 0) && !fileEOF && fileReadOffset < fileSize) {
                    try {
                        //convert more strings, store in buffer
                        long toRead = 0;

                        //fill up entire fileReadBuff fresh
                        toRead = Math.min(FILE_BUF_SIZE, fileSize - fileReadOffset);
                        //}
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
                            convertBuffRemain = bytesInConvertBuff - convertBuffOffset;
                        }
                    } catch (TskCoreException ex) {
                        //Exceptions.printStackTrace(ex);
                        fileEOF = true;
                    }
                }
                //nothing more to read, and no more bytes in convertBuff
                if (convertBuff == null || convertBuffRemain == 0) {
                    if (fileEOF) {
                        return bytesToUser > 0 ? bytesToUser : -1;
                    } else {
                        //no strings extracted, try another read
                        continue;
                    }
                }
                //return part or all of convert buff to user
                final int toCopy = Math.min(convertBuffRemain, len - offsetUser);
                System.arraycopy(convertBuff, convertBuffOffset, b, offsetUser, toCopy);

                convertBuffOffset += toCopy;
                offsetUser += toCopy;
                bytesToUser += toCopy;
            }
            //if more string data in convertBuff, will be consumed on next read()
            return bytesToUser;
        }

        /**
         * convert bytes in file buffer to string, and encode string in
         * convertBuffer
         *
         * @param numBytes num bytes in the fileReadBuff
         */
        private void convert(int numBytes) {
            lastExtractResult = stringExtractor.extract(fileReadBuff, numBytes, 0);
            convertBuff = lastExtractResult.getText().getBytes(Server.DEFAULT_INDEXED_TEXT_CHARSET);
            //reset tracking vars
            if (lastExtractResult.getNumBytes() == 0) {
                bytesInConvertBuff = 0;
            } else {
                bytesInConvertBuff = convertBuff.length;
            }
            convertBuffOffset = 0;
        }
    }
}
