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
package org.sleuthkit.autopsy.coreutils;

import java.io.IOException;
import java.io.InputStream;
import static java.lang.Byte.toUnsignedInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;

/**
 * Language and encoding aware utility to extract strings from stream of bytes
 * Currently supports UTF-16 LE, UTF-16 BE and UTF8 Latin, Cyrillic, Chinese,
 * Arabic
 *
 * TODO: process control characters
 *
 * TODO: handle tie better (when number of chars in 2 results is equal)
 */
public class StringExtract {

    private static final Logger logger = Logger.getLogger(StringExtract.class.getName());
    /**
     * min. number of extracted chars to qualify as string
     */
    public static final int MIN_CHARS_STRING = 4;
    private final StringExtractUnicodeTable unicodeTable;
    /**
     * currently enabled scripts
     */
    private List<SCRIPT> enabledScripts;
    private boolean enableUTF8;
    private boolean enableUTF16;

    //stored and reused results
    private final StringExtractResult resUTF16En1 = new StringExtractResult();
    private final StringExtractResult resUTF16En2 = new StringExtractResult();
    private final StringExtractResult resUTF8 = new StringExtractResult();

    /**
     * supported scripts, can be overridden with enableScriptX methods
     */
    private static final List<SCRIPT> SUPPORTED_SCRIPTS
            = Arrays.asList(
                    SCRIPT.LATIN_1, SCRIPT.LATIN_2, SCRIPT.ARABIC, SCRIPT.CYRILLIC, SCRIPT.HAN,
                    SCRIPT.HIRAGANA, SCRIPT.KATAKANA, SCRIPT.HANGUL,
                    SCRIPT.ARMENIAN, SCRIPT.BENGALI, SCRIPT.KHMER, SCRIPT.ETHIOPIC,
                    SCRIPT.GEORGIAN, SCRIPT.HEBREW, SCRIPT.LAO, SCRIPT.MONGOLIAN, SCRIPT.THAI, SCRIPT.TIBETAN);
    //current total string buffer, reuse for performance
    private final StringBuilder curString = new StringBuilder();

    /**
     * Initializes the StringExtract utility Sets enabled scripts to all
     * supported ones
     */
    public StringExtract() {
        unicodeTable = StringExtractUnicodeTable.getInstance();

        if (unicodeTable == null) {
            throw new IllegalStateException(
                    NbBundle.getMessage(StringExtract.class, "StringExtract.illegalStateException.cannotInit.msg"));
        }

        setEnabledScripts(SUPPORTED_SCRIPTS);
        enableUTF8 = true;
        enableUTF16 = true;
    }

    public boolean isEnableUTF8() {
        return enableUTF8;
    }

    public void setEnableUTF8(boolean enableUTF8) {
        this.enableUTF8 = enableUTF8;
    }

    public boolean isEnableUTF16() {
        return enableUTF16;
    }

    public void setEnableUTF16(boolean enableUTF16) {
        this.enableUTF16 = enableUTF16;
    }

    /**
     * Sets the enabled scripts to ones provided, resets previous setting
     *
     * @param scripts scripts to consider for when extracting strings
     */
    public final void setEnabledScripts(List<SCRIPT> scripts) {
        this.enabledScripts = scripts;
    }

    /**
     * Sets the enabled script to one provided, resets previous setting
     *
     * @param script script to consider for when extracting strings
     */
    public final void setEnabledScript(SCRIPT script) {
        this.enabledScripts = new ArrayList<>();
        this.enabledScripts.add(script);
    }

    /**
     * Check if extraction of the script is supported by the utility
     *
     * @param script script to check if supported
     *
     * @return true if the the utility supports the extraction of the script
     */
    public static boolean isExtractionSupported(SCRIPT script) {
        return SUPPORTED_SCRIPTS.contains(script);
    }

    /**
     * Check if extraction of the script is enabled by this instance of the
     * utility. For LATIN_2 (extended LATIN), enable also LATIN_1, even if it's
     * not explicitely enabled.
     *
     * @param script script that was identified, to check if it is enabled
     *
     * @return true if the the script extraction is enabled
     */
    public boolean isExtractionEnabled(SCRIPT script) {
        if (script.equals(SCRIPT.LATIN_1)) {
            return enabledScripts.contains(SCRIPT.LATIN_1)
                    || enabledScripts.contains(SCRIPT.LATIN_2);
        } else {
            return enabledScripts.contains(script);
        }

    }

    /**
     * Determine if Basic Latin/English extraction is set enabled only
     *
     * @return true if only Basic Latin/English extraction is set enabled only
     */
    public boolean isExtractionLatinBasicOnly() {
        return enabledScripts.size() == 1
                && enabledScripts.get(0).equals(SCRIPT.LATIN_1);
    }

    public static List<SCRIPT> getSupportedScripts() {
        return SUPPORTED_SCRIPTS;
    }

    /**
     * Runs the byte buffer through the string extractor
     *
     * @param buff
     * @param len
     * @param offset
     *
     * @return string extraction result, with the string extracted and
     *         additional info
     */
    public StringExtractResult extract(byte[] buff, int len, int offset) {
        if (this.enableUTF16 == false && this.enableUTF8 == false) {
            return new StringExtractResult();
        }

        final int buffLen = buff.length;

        int processedBytes = 0;
        int curOffset = offset;
        int startOffset = offset;
        int curStringLen = 0;

        //reset curString buffer
        curString.delete(0, curString.length());

        //keep track of first byte offset that hasn't been processed
        //(one byte past the last byte processed in by last extraction)
        int firstUnprocessedOff = offset;

        while (curOffset < buffLen) {
            //shortcut, skip processing empty bytes
            if (buff[curOffset] == 0 && curOffset + 1 < buffLen && buff[curOffset + 1] == 0) {
                curOffset += 2;
                continue;
            }

            //extract using all methods and see which one wins
            StringExtractResult resUTF16 = null;
            boolean runUTF16 = false;
            if (enableUTF16 && curOffset % 2 == 0) {
                runUTF16 = true;
                extractUTF16(buff, len, curOffset, true, resUTF16En1);
                extractUTF16(buff, len, curOffset, false, resUTF16En2);
                resUTF16 = resUTF16En1.numChars > resUTF16En2.numChars ? resUTF16En1 : resUTF16En2;
            }

            if (enableUTF8) {
                extractUTF8(buff, len, curOffset, resUTF8);
            }

            StringExtractResult resWin = null;
            if (enableUTF8 && resUTF16 != null) {
                resWin = runUTF16 && resUTF16.numChars > resUTF8.numChars ? resUTF16 : resUTF8;
            } else if (runUTF16) {
                //Only let resUTF16 "win" if it was actually run.
                resWin = resUTF16;
            } else if (enableUTF8) {
                resWin = resUTF8;
            }

            if (resWin != null && resWin.numChars >= MIN_CHARS_STRING) {
                //record string 
                if (startOffset == offset) {
                    //advance start offset where first string starts it hasn't been advanced
                    startOffset = resWin.offset;
                }
                curStringLen += resWin.numChars;
                curString.append(resWin.textString);
                curString.append("\n");
                curStringLen += resWin.numChars + 1;

                //advance
                curOffset += resWin.numBytes;
                processedBytes += resWin.numBytes;
                firstUnprocessedOff = resWin.offset + resWin.numBytes;
            } else {
                //if no encodings worked, advance byte
                if (enableUTF8 == false) {
                    curOffset += 2;
                } else {
                    ++curOffset;
                }
            }
        }

        //build up the final result
        StringExtractResult res = new StringExtractResult();
        res.numBytes = processedBytes;
        res.numChars = curStringLen;
        res.offset = startOffset;
        res.textString = curString.toString();
        res.firstUnprocessedOff = firstUnprocessedOff; //save that of the last winning result

        return res;
    }

    private StringExtractResult extractUTF16(byte[] buff, int len, int offset, boolean endianSwap, final StringExtractResult res) {
        res.reset();

        int curOffset = offset;

        final StringBuilder tempString = new StringBuilder();

        SCRIPT currentScript = SCRIPT.NONE;

        //while we have 2 byte chunks
        while (curOffset < len - 1) {
            int msb, lsb;
            
            if (endianSwap) {
                msb = toUnsignedInt(buff[curOffset++]);
                lsb = toUnsignedInt(buff[curOffset++]);
            }
            else {
                lsb = toUnsignedInt(buff[curOffset++]);
                msb = toUnsignedInt(buff[curOffset++]);
            }

            //convert the byte sequence to 2 byte char
            char byteVal = (char) msb;
            byteVal = (char) (byteVal << 8);
            byteVal += lsb;

            //skip if beyond range
            if (byteVal > StringExtractUnicodeTable.UNICODE_TABLE_SIZE - 1) {
                break;
            }

            //lookup byteVal in the unicode table
            SCRIPT scriptFound = unicodeTable.getScript(byteVal);

            if (scriptFound == SCRIPT.NONE) {
                break;
            }

            /*
             * else if (scriptFound == SCRIPT.CONTROL) { //update bytes
             * processed res.numBytes += 2; continue; } else if (inControl) {
             * break;
             }
             */
            final boolean isGeneric = StringExtractUnicodeTable.isGeneric(scriptFound);
            //allow generic and one of enabled scripts we locked in to
            if (isGeneric
                    || isExtractionEnabled(scriptFound)) {

                if (currentScript == SCRIPT.NONE
                        && !isGeneric) {
                    //handle case when this is the first char in the string
                    //lock into the script
                    currentScript = scriptFound;
                }
                //check if we are within the same script we are locked on to, or COMMON
                if (currentScript == scriptFound
                        || isGeneric) {
                    if (res.numChars == 0) {
                        //set the start offset of the string
                        res.offset = curOffset;
                    }
                    //update bytes processed
                    res.numBytes += 2;
                    //append the char
                    ++res.numChars;
                    tempString.append(byteVal);
                } else {
                    //bail out
                    break;
                }
            } else {
                //bail out 
                break;
            }

        } //no more data

        res.textString = tempString.toString();

        return res;
    }

    private StringExtractResult extractUTF8(byte[] buff, int len, int offset, final StringExtractResult res) {
        res.reset();

        int curOffset = offset;
        int curChar; //character being extracted
        int chBytes; //num bytes consumed by current char (1 - 4)

        final StringBuilder tempString = new StringBuilder();

        SCRIPT currentScript = SCRIPT.NONE;

        //decode and extract a character
        while (curOffset < len) {
            // based on "valid UTF-8 byte sequences" in the Unicode 5.0 book
            final int curByte = toUnsignedInt(buff[curOffset]);
            if (curByte <= 0x7F) {
                chBytes = 1;
                curChar = curByte;
            } else if (curByte <= 0xC1) {
                break;
            } else if (curByte <= 0xDF) {
                if (len - curOffset < 2) {
                    break;
                }
                final int curByte_1 = toUnsignedInt(buff[curOffset + 1]);
                if (curByte_1 >= 0x80 && curByte_1 <= 0xBF) {
                    chBytes = 2;
                    curChar = (((curByte & 0x1f) << 6) + (curByte_1 & 0x3f));
                } else {
                    break;
                }
            } else if (curByte == 0xE0) {
                if (len - curOffset < 3) {
                    break;
                }
                final int curByte_1 = toUnsignedInt(buff[curOffset + 1]);
                final int curByte_2 = toUnsignedInt(buff[curOffset + 2]);

                if (curByte_1 >= 0xA0 && curByte_1 <= 0xBF
                        && curByte_2 >= 0x80 && curByte_2 <= 0xBF) {
                    chBytes = 3;
                    curChar = (((curByte & 0x0f) << 12) + ((curByte_1 & 0x3f) << 6) + (curByte_2 & 0x3f));
                } else {
                    break;
                }
            } else if (curByte <= 0xEC) {
                if (len - curOffset < 3) {
                    break;
                }
                final int curByte_1 = toUnsignedInt(buff[curOffset + 1]);
                final int curByte_2 = toUnsignedInt(buff[curOffset + 2]);
                if (curByte_1 >= 0x80 && curByte_1 <= 0xBF
                        && curByte_2 >= 0x80 && curByte_2 <= 0xBF) {
                    chBytes = 3;
                    curChar = (((curByte & 0x0f) << 12) + ((curByte_1 & 0x3f) << 6) + (curByte_2 & 0x3f));
                } else {
                    break;
                }
            } else if (curByte == 0xED) {
                if (len - curOffset < 3) {
                    break;
                }
                final int curByte_1 = toUnsignedInt(buff[curOffset + 1]);
                final int curByte_2 = toUnsignedInt(buff[curOffset + 2]);
                if (curByte_1 >= 0x80 && curByte_1 <= 0x9F
                        && curByte_2 >= 0x80 && curByte_2 <= 0xBF) {
                    chBytes = 3;
                    curChar = (((curByte & 0x0f) << 12) + ((curByte_1 & 0x3f) << 6) + (curByte_2 & 0x3f));
                } else {
                    break;
                }
            } else if (curByte <= 0xEF) {
                if (len - curOffset < 3) {
                    break;
                }
                final int curByte_1 = toUnsignedInt(buff[curOffset + 1]);
                final int curByte_2 = toUnsignedInt(buff[curOffset + 2]);
                if (curByte_1 >= 0x80 && curByte_1 <= 0xBF
                        && curByte_2 >= 0x80 && curByte_2 <= 0xBF) {
                    chBytes = 3;
                    curChar = (((curByte & 0x0f) << 12) + ((curByte_1 & 0x3f) << 6) + (curByte_2 & 0x3f));
                } else {
                    break;
                }
            } else if (curByte == 0xF0) {
                if (len - curOffset < 4) {
                    break;
                }
                final int curByte_1 = toUnsignedInt(buff[curOffset + 1]);
                final int curByte_2 = toUnsignedInt(buff[curOffset + 2]);
                final int curByte_3 = toUnsignedInt(buff[curOffset + 3]);
                if (curByte_1 >= 0x90 && curByte_1 <= 0xBF
                        && curByte_2 >= 0x80 && curByte_2 <= 0xBF
                        && curByte_3 >= 0x80 && curByte_3 <= 0xBF) {
                    chBytes = 4;
                    curChar = (((curByte & 0x07) << 18) + ((curByte_1 & 0x3f) << 12) + ((curByte_2 & 0x3f) << 6) + (curByte_3 & 0x3f));
                } else {
                    break;
                }
            } else if (curByte <= 0xF3) {
                if (len - curOffset < 4) {
                    break;
                }
                final int curByte_1 = toUnsignedInt(buff[curOffset + 1]);
                final int curByte_2 = toUnsignedInt(buff[curOffset + 2]);
                final int curByte_3 = toUnsignedInt(buff[curOffset + 3]);
                if (curByte_1 >= 0x80 && curByte_1 <= 0xBF
                        && curByte_2 >= 0x80 && curByte_2 <= 0xBF
                        && curByte_3 >= 0x80 && curByte_3 <= 0xBF) {
                    chBytes = 4;
                    curChar = (((curByte & 0x07) << 18) + ((curByte_1 & 0x3f) << 12) + ((curByte_2 & 0x3f) << 6) + (curByte_3 & 0x3f));
                } else {
                    break;
                }
            } else {
                break;
            }

            curOffset += chBytes;

            //skip if beyond range
            if (curChar > StringExtractUnicodeTable.UNICODE_TABLE_SIZE - 1) {
                break;
            }

            //lookup byteVal in the unicode table
            SCRIPT scriptFound = unicodeTable.getScript(curChar);

            if (scriptFound == SCRIPT.NONE) {
                break;
            }

            /*
             * else if (scriptFound == SCRIPT.CONTROL) { //update bytes
             * processed res.numBytes += chBytes; continue; } else if
             * (inControl) { break;
             }
             */
            final boolean isGeneric = StringExtractUnicodeTable.isGeneric(scriptFound);
            //allow generic and one of enabled scripts we locked in to
            if (isGeneric
                    || isExtractionEnabled(scriptFound)) {

                if (currentScript == SCRIPT.NONE
                        && !isGeneric) {
                    //handle case when this is the first char in the string
                    //lock into the script
                    currentScript = scriptFound;
                }
                //check if we are within the same script we are locked on to, or COMMON
                if (currentScript == scriptFound
                        || isGeneric) {
                    if (res.numChars == 0) {
                        //set the start byte offset of the string
                        res.offset = curOffset;
                    }
                    //update bytes processed
                    res.numBytes += chBytes;
                    //append the char
                    ++res.numChars;
                    tempString.append((char) curChar);
                } else {
                    //bail out
                    break;
                }
            } else {
                //bail out 
                break;
            }

        } //no more data

        res.textString = tempString.toString();

        return res;
    }

    /*
     * Extract UTF8/16 ASCII characters from byte buffer - only works for Latin,
     * but fast
     *
     * The definition of printable are: -- All of the letters, numbers, and
     * punctuation. -- space and tab -- It does NOT include newlines or control
     * chars. -- When looking for ASCII strings, they evaluate each byte and
     * when they find four or more printable characters they get printed out
     * with a newline in between each string. -- When looking for Unicode
     * strings, they evaluate each two byte sequence and look for four or more
     * printable characters…
     *
     * @param readBuf the bytes that the string read from @param len buffer
     * length @param offset offset to start converting from
     *
     */
    public static String extractASCII(byte[] readBuf, int len, int offset) {
        final StringBuilder result = new StringBuilder();
        StringBuilder temp = new StringBuilder();
        int curLen = 0;

        final char NL = (char) 10; // ASCII char for new line
        final String NLS = Character.toString(NL);
        boolean singleConsecZero = false; //preserve the current sequence of chars if 1 consecutive zero char
        for (int i = offset; i < len; i++) {
            char curChar = (char) toUnsignedInt(readBuf[i]);
            if (curChar == 0 && singleConsecZero == false) {
                //preserve the current sequence if max consec. 1 zero char 
                singleConsecZero = true;
            } else {
                singleConsecZero = false;
            }
            //ignore non-printable ASCII chars
            if (isPrintableAscii(curChar)) {
                temp.append(curChar);
                ++curLen;
            } else if (!singleConsecZero) {
                if (curLen >= MIN_CHARS_STRING) {
                    // add to the result and also add the new line at the end
                    result.append(temp);
                    result.append(NLS);
                }
                // reset the temp and curLen
                temp = new StringBuilder();
                curLen = 0;

            }
        }

        result.append(temp);
        return result.toString();
    }

    /**
     * Determine if char is a printable ASCII char in range <32,126> and a tab
     *
     * @param c char to test
     *
     * @return true if it's a printable char, or false otherwise
     */
    public static boolean isPrintableAscii(char c) {
        return (c >= 32 && c <= 126) || c == 9;
    }

    /**
     * Representation of the string extraction result
     */
    public class StringExtractResult implements Comparable<StringExtractResult> {

        int offset; ///< offset in input buffer where the first string starts
        int numBytes; ///< num bytes in input buffer consumed
        int numChars; ///< number of encoded characters extracted in the textString
        int firstUnprocessedOff; ///< first byte past the last byte used in extraction, offset+numBytes for a single result, but we keep track of it for multiple extractions
        String textString; ///< the actual text string extracted, of numChars long

        void reset() {
            offset = 0;
            numBytes = 0;
            numChars = 0;
            firstUnprocessedOff = 0;
            textString = null;
        }

        public int getFirstUnprocessedOff() {
            return firstUnprocessedOff;
        }

        public int getStartOffset() {
            return offset;
        }

        public int getNumBytes() {
            return numBytes;
        }

        public int getTextLength() {
            return numChars;
        }

        public String getText() {
            return textString;
        }

        @Override
        public int compareTo(StringExtractResult o) {
            //result with highest num of characters is less than (wins)
            //TODO handle tie - pick language with smallest number of chars
            return o.numChars - numChars;
        }
    }

    /**
     * Encapsulates the loaded unicode table and different scripts and provides
     * utilitities for the table and script lookup. Manages loading of the
     * unicode table. Used as a singleton to ensure minimal resource usage for
     * the unicode table.
     */
    public static class StringExtractUnicodeTable {

        public interface LanguageInfo {

            String getLanguages();
        }

        /**
         * Scripts listed in the unicodeTable loaded
         */
        public static enum SCRIPT implements LanguageInfo {

            NONE {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            COMMON {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            LATIN_1 {
                        @Override
                        public String toString() {
                            return "Latin - Basic"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "English"; //NON-NLS
                        }
                    },
            GREEK {
                        @Override
                        public String toString() {
                            return "Greek"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            CYRILLIC {
                        @Override
                        public String toString() {
                            return "Cyrillic"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "Russian, Bulgarian, Serbian, Moldovan"; //NON-NLS
                        }
                    },
            ARMENIAN {
                        @Override
                        public String toString() {
                            return "Armenian"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            HEBREW {
                        @Override
                        public String toString() {
                            return "Hebrew"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            ARABIC {
                        @Override
                        public String toString() {
                            return "Arabic"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            SYRIAC {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            THAANA {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            DEVANAGARI {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            BENGALI {
                        @Override
                        public String toString() {
                            return "Bengali"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            GURMUKHI {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            GUJARATI {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            ORIYA {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            TAMIL {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            TELUGU {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            KANNADA {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            MALAYALAM {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            SINHALA {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            THAI {
                        @Override
                        public String toString() {
                            return "Thai"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            LAO {
                        @Override
                        public String toString() {
                            return "Laotian"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            TIBETAN {
                        @Override
                        public String toString() {
                            return "Tibetian"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            MYANMAR {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            GEORGIAN {
                        @Override
                        public String toString() {
                            return "Georgian"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            HANGUL {
                        @Override
                        public String toString() {
                            return "Hangul"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "Korean"; //NON-NLS
                        }
                    },
            ETHIOPIC {
                        @Override
                        public String toString() {
                            return "Ethiopic"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            CHEROKEE {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            CANADIAN_ABORIGINAL {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            OGHAM {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            RUNIC {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            KHMER {
                        @Override
                        public String toString() {
                            return "Khmer"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "Cambodian"; //NON-NLS
                        }
                    },
            MONGOLIAN {
                        @Override
                        public String toString() {
                            return "Mongolian"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            HIRAGANA {
                        @Override
                        public String toString() {
                            return "Hiragana"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "Japanese"; //NON-NLS
                        }
                    },
            KATAKANA {
                        @Override
                        public String toString() {
                            return "Katakana"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "Japanese"; //NON-NLS
                        }
                    },
            BOPOMOFO {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            HAN {
                        @Override
                        public String toString() {
                            return "Han"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "Chinese, Japanese, Korean"; //NON-NLS
                        }
                    },
            YI {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            OLD_ITALIC {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            GOTHIC {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            DESERET {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            INHERITED {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            TAGALOG {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            HANUNOO {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            BUHID {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            TAGBANWA {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            LIMBU {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            TAI_LE {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            LINEAR_B {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            UGARITIC {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            SHAVIAN {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            OSMANYA {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            CYPRIOT {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            BRAILLE {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            BUGINESE {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            COPTIC {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            NEW_TAI_LUE {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            GLAGOLITIC {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            TIFINAGH {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            SYLOTI_NAGRI {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            OLD_PERSIAN {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            KHAROSHTHI {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            BALINESE {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            CUNEIFORM {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            PHOENICIAN {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            PHAGS_PA {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            NKO {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            CONTROL {
                        @Override
                        public String getLanguages() {
                            return toString();
                        }
                    },
            LATIN_2 {
                        @Override
                        public String toString() {
                            return "Latin - Extended"; //NON-NLS
                        }

                        @Override
                        public String getLanguages() {
                            return "European"; //NON-NLS
                        }
                    }
        };
        private static final SCRIPT[] SCRIPT_VALUES = SCRIPT.values();
        private static final String PROPERTY_FILE = "StringExtract.properties"; //NON-NLS
        /**
         * table has an entry for every possible 2-byte value
         */
        private static final int UNICODE_TABLE_SIZE = 65536;
        /**
         * unicode lookup table with 2 byte index and value of script
         */
        private static final char[] UNICODE_TABLE = new char[UNICODE_TABLE_SIZE];
        private static StringExtractUnicodeTable instance = null; //the singleton instance

        /**
         * return instance of StringExtract of null if it could not be
         * initialized
         *
         * @return
         */
        public static synchronized StringExtractUnicodeTable getInstance() {
            if (instance == null) {
                instance = new StringExtractUnicodeTable();
                if (!instance.init()) {
                    //error condition
                    instance = null;
                }

            }
            return instance;
        }

        /**
         * Lookup and get script given byte value of a potential character
         *
         * @param value
         *
         * @return the script type corresponding to the value
         */
        public SCRIPT getScript(int value) {
            char scriptVal = UNICODE_TABLE[value];
            return SCRIPT_VALUES[scriptVal];
        }

        /**
         * Check if the script belongs to generic/common (chars are shared
         * between different scripts)
         *
         * @param script to check for
         *
         * @return true if the script is generic
         */
        public static boolean isGeneric(SCRIPT script) {
            return script == SCRIPT.COMMON; // || script == SCRIPT.LATIN_1;
        }

        public static int getUnicodeTableSize() {
            return UNICODE_TABLE_SIZE;
        }

        /**
         * Get the value of the script
         *
         * @param script the script to get value of
         *
         * @return the value corresponding to ordering in the SCRIPT enum
         */
        public static int getScriptValue(SCRIPT script) {
            return script.ordinal();
        }

        public static SCRIPT scriptForString(String scriptStringVal) {
            SCRIPT script = SCRIPT.valueOf(scriptStringVal);
            return script;
        }

        /**
         * Initialization, loads unicode tables
         *
         * @return true if initialized properly, false otherwise
         */
        private boolean init() {
            Properties properties = new Properties();
            try {
                //properties.load(new FileInputStream("StringExtract.properties"));            
                InputStream inputStream = StringExtract.class.getResourceAsStream(PROPERTY_FILE);
                properties.load(inputStream);
                String table = properties.getProperty("UnicodeTable");
                StringTokenizer st = new StringTokenizer(table, " ");
                int toks = st.countTokens();
                //logger.log(Level.INFO, "TABLE TOKS: " + toks);
                if (toks != UNICODE_TABLE_SIZE) {
                    logger.log(Level.WARNING, "Unicode table corrupt, expecting: " + UNICODE_TABLE_SIZE, ", have: " + toks); //NON-NLS
                    return false;
                }

                int tableIndex = 0;
                while (st.hasMoreTokens()) {
                    String tok = st.nextToken();
                    char code = (char) Integer.parseInt(tok);
                    UNICODE_TABLE[tableIndex++] = code;
                }

                logger.log(Level.INFO, "initialized, unicode table loaded"); //NON-NLS

            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not load" + PROPERTY_FILE); //NON-NLS
                return false;
            }

            return true;

        }
    }
}
