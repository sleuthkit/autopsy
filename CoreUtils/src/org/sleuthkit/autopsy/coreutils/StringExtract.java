/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Language and encoding aware utility to extract strings from stream of bytes
 * Currently supports Latin UTF-16 LE and UTF-16 BE
 */
public class StringExtract {

    /**
     * Scripts listed in the unicodeTable loaded
     */
    public static enum SCRIPT {

        NONE,
        COMMON,
        LATIN_1,
        GREEK,
        CYRILLIC,
        ARMENIAN,
        HEBREW,
        ARABIC,
        SYRIAC,
        THAANA,
        DEVANAGARI,
        BENGALI,
        GURMUKHI,
        GUJARATI,
        ORIYA,
        TAMIL,
        TELUGU,
        KANNADA,
        MALAYALAM,
        SINHALA,
        THAI,
        LAO,
        TIBETAN,
        MYANMAR,
        GEORGIAN,
        HANGUL,
        ETHIOPIC,
        CHEROKEE,
        CANADIAN_ABORIGINAL,
        OGHAM,
        RUNIC,
        KHMER,
        MONGOLIAN,
        HIRAGANA,
        KATAKANA,
        BOPOMOFO,
        HAN,
        YI,
        OLD_ITALIC,
        GOTHIC,
        DESERET,
        INHERITED,
        TAGALOG,
        HANUNOO,
        BUHID,
        TAGBANWA,
        LIMBU,
        TAI_LE,
        LINEAR_B,
        UGARITIC,
        SHAVIAN,
        OSMANYA,
        CYPRIOT,
        BRAILLE,
        BUGINESE,
        COPTIC,
        NEW_TAI_LUE,
        GLAGOLITIC,
        TIFINAGH,
        SYLOTI_NAGRI,
        OLD_PERSIAN,
        KHAROSHTHI,
        BALINESE,
        CUNEIFORM,
        PHOENICIAN,
        PHAGS_PA,
        NKO,
        CONTROL,
        LATIN_2
    };
    private static final SCRIPT[] SCRIPT_VALUES = SCRIPT.values();

    /**
     * Get script given byte value
     *
     * @param value
     * @return the script type corresponding to the value
     */
    public SCRIPT getScript(int value) {
        char scriptVal = unicodeTable[value];
        return SCRIPT_VALUES[scriptVal];
    }

    /**
     * Get the value of the script
     *
     * @param script the script to get value of
     * @return the value corresponding to ordering in the SCRIPT enum
     */
    public static int getScriptValue(SCRIPT script) {
        return script.ordinal();
    }
    private static final Logger logger = Logger.getLogger(StringExtract.class.getName());
    /**
     * table has an entry for every possible 2-byte value
     */
    private static final int UNICODE_TABLE_SIZE = 65536;
    /**
     * unicode lookup table with 2 byte index and value of script
     */
    private static final char[] unicodeTable = new char[UNICODE_TABLE_SIZE];
    private static boolean initialized = false;
    /**
     * min. number of extracted chars to qualify as string
     */
    public static final int MIN_CHARS_STRING = 4;

    /**
     * Initialization, loads unicode tables
     */
    static {
        Properties properties = new Properties();
        try {
            //properties.load(new FileInputStream("StringExtract.properties"));            
            InputStream inputStream = StringExtract.class.getResourceAsStream("StringExtract.properties");
            properties.load(inputStream);
            String table = properties.getProperty("UnicodeTable");
            StringTokenizer st = new StringTokenizer(table, " ");
            int toks = st.countTokens();
            //logger.log(Level.INFO, "TABLE TOKS: " + toks);
            if (toks != UNICODE_TABLE_SIZE) {
                logger.log(Level.WARNING, "Unicode table corrupt, expecting: " + UNICODE_TABLE_SIZE, ", have: " + toks);
            }

            int tableIndex = 0;
            while (st.hasMoreTokens()) {
                String tok = st.nextToken();
                char code = (char) Integer.parseInt(tok);
                unicodeTable[tableIndex++] = code;
            }

            initialized = true;
            logger.log(Level.INFO, "initialized, unicode table loaded");

        } catch (IOException ex) {
            initialized = false;
            logger.log(Level.WARNING, "Could not load StringExtract.properties");
        }

    }

    public StringExtractResult extract(byte[] buff, int len, int offset) {
        final int buffLen = buff.length;

        int processedBytes = 0;
        int curOffset = offset;
        int startOffset = offset;
        int curStringLen = 0;
        StringBuilder curString = new StringBuilder();

        while (curOffset < buffLen) {

            //shortcut, skip processing empty bytes
            //TODO adjust for UTF8 if needed
            if (buff[curOffset] == 0 && curOffset + 1 < buffLen && buff[curOffset + 1] == 0) {
                curOffset +=2;
                continue;
            }

            //for now 2 possibilities, see which one wins
            StringExtractResult res0 = extractUTF16(buff, len, curOffset, false);
            StringExtractResult res1 = extractUTF16(buff, len, curOffset, true);
            StringExtractResult res2 = extractUTF8(buff, len, curOffset);

            StringExtractResult resWin = res0.numBytes > res1.numBytes ? res0 : res1;
            //TODO handle tie

            resWin = resWin.numBytes > res2.numBytes ? resWin : res2;
            //TODO handle tie

            if (resWin.numChars >= MIN_CHARS_STRING) {
                //record string 
                if (startOffset == offset) {
                    //advance start offset where first string starts it hasn't been advanced
                    startOffset = resWin.offset;
                }
                curStringLen += resWin.numChars;
                curString.append(resWin.textString);
                curString.append("\n");
                curStringLen += resWin.numChars + 1;
            }
            //advance, whether string found or not
            if (resWin.numChars > 0) {
                curOffset += resWin.numBytes;
                processedBytes += resWin.numBytes;
            } else {
                //if no encodings worked, advance 1 byte
                ++curOffset;
                //++processedBytes;
            }

        }

        //build up the result
        StringExtractResult res = new StringExtractResult();
        res.numBytes = processedBytes;
        res.numChars = curStringLen;
        res.offset = startOffset;
        res.textString = curString.toString();

        return res;
    }

    private StringExtractResult extractUTF16(byte[] buff, int len, int offset, boolean endianSwap) {
        StringExtractResult res = new StringExtractResult();

        int curOffset = offset;

        StringBuilder curString = new StringBuilder();

        SCRIPT currentScript = SCRIPT.NONE;

        boolean inControl = false;

        //while we have 2 byte chunks
        byte[] b = new byte[2];
        while (curOffset < len - 1) {
            b[0] = buff[curOffset++];
            b[1] = buff[curOffset++];

            if (endianSwap) {
                byte temp = b[0];
                b[0] = b[1];
                b[1] = temp;
            }

            //convert the byte sequence to 2 byte char
            //ByteBuffer bb = ByteBuffer.wrap(b);
            //int byteVal = bb.getInt();
            char byteVal = (char) b[1];
            byteVal = (char) (byteVal << 8);
            byteVal += b[0];

            //skip if beyond range
            if (byteVal > UNICODE_TABLE_SIZE - 1) {
                break;
            }

            //lookup byteVal in the unicode table
            SCRIPT scriptFound = getScript(byteVal);

            if (scriptFound == SCRIPT.NONE) {
                break;
            }

            /*
             else if (scriptFound == SCRIPT.CONTROL) {
             //update bytes processed
             res.numBytes += 2;
             continue;
             } else if (inControl) {
             break;
             }*/


            final boolean isGeneric = isGeneric(scriptFound);
            //allow generic and one of supported script we locked in to
            if (isGeneric
                    || scriptFound == SCRIPT.LATIN_2
                    || scriptFound == SCRIPT.ARABIC //|| scriptFound == SCRIPT.CYRILLIC
                    //|| scriptFound == SCRIPT.HAN
                    ) {

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
                    curString.append(byteVal);
                } else {
                    //bail out ? check
                    break;
                }
            } else {
                //bail out ? check
                break;
            }

        } //no more data

        res.textString = curString.toString();

        return res;
    }

    private StringExtractResult extractUTF8(byte[] buff, int len, int offset) {
        StringExtractResult res = new StringExtractResult();

        int curOffset = offset;
        int ch = 0; //character being extracted
        int chBytes; //num bytes consumed by current char (1 - 4)

        StringBuilder curString = new StringBuilder();

        //we only care about common script
        //final SCRIPT scriptSupported = SCRIPT.COMMON;
        SCRIPT currentScript = SCRIPT.NONE;

        boolean inControl = false;

        //decode and extract a character
        while (curOffset < len) {
            // based on "valid UTF-8 byte sequences" in the Unicode 5.0 book
            if (buff[curOffset] <= 0x7F) {
                chBytes = 1;
                ch = buff[curOffset];
            } else if (buff[curOffset] <= 0xC1) {
                break;
            } else if (buff[curOffset] <= 0xDF) {
                if (len - curOffset < 2) {
                    break;
                }
                if (buff[curOffset + 1] >= 0x80 && buff[curOffset + 1] <= 0xBF) {
                    chBytes = 2;
                    ch = (((buff[curOffset] & 0x1f) << 6) + (buff[curOffset + 1] & 0x3f));
                } else {
                    break;
                }
            } else if (buff[curOffset] == 0xE0) {
                if (len - curOffset < 3) {
                    break;
                }
                if (buff[curOffset + 1] >= 0xA0 && buff[curOffset + 1] <= 0xBF
                        && buff[curOffset + 2] >= 0x80 && buff[curOffset + 2] <= 0xBF) {
                    chBytes = 3;
                    ch = (((buff[curOffset] & 0x0f) << 12) + ((buff[curOffset + 1] & 0x3f) << 6) + (buff[curOffset + 2] & 0x3f));
                } else {
                    break;
                }
            } else if (buff[curOffset] <= 0xEC) {
                if (len - curOffset < 3) {
                    break;
                }
                if (buff[curOffset + 1] >= 0x80 && buff[curOffset + 1] <= 0xBF
                        && buff[curOffset + 2] >= 0x80 && buff[curOffset + 2] <= 0xBF) {
                    chBytes = 3;
                    ch = (((buff[curOffset] & 0x0f) << 12) + ((buff[curOffset + 1] & 0x3f) << 6) + (buff[curOffset + 2] & 0x3f));
                } else {
                    break;
                }
            } else if (buff[curOffset] == 0xED) {
                if (len - curOffset < 3) {
                    break;
                }
                if (buff[curOffset + 1] >= 0x80 && buff[curOffset + 1] <= 0x9F
                        && buff[curOffset + 2] >= 0x80 && buff[curOffset + 2] <= 0xBF) {
                    chBytes = 3;
                    ch = (((buff[curOffset] & 0x0f) << 12) + ((buff[curOffset + 1] & 0x3f) << 6) + (buff[curOffset + 2] & 0x3f));
                } else {
                    break;
                }
            } else if (buff[curOffset] <= 0xEF) {
                if (len - curOffset < 3) {
                    break;
                }
                if (buff[curOffset + 1] >= 0x80 && buff[curOffset + 1] <= 0xBF
                        && buff[curOffset + 2] >= 0x80 && buff[curOffset + 2] <= 0xBF) {
                    chBytes = 3;
                    ch = (((buff[curOffset] & 0x0f) << 12) + ((buff[curOffset + 1] & 0x3f) << 6) + (buff[curOffset + 2] & 0x3f));
                } else {
                    break;
                }
            } else if (buff[curOffset] == 0xF0) {
                if (len - curOffset < 4) {
                    break;
                }
                if (buff[curOffset + 1] >= 0x90 && buff[curOffset + 1] <= 0xBF
                        && buff[curOffset + 2] >= 0x80 && buff[curOffset + 2] <= 0xBF
                        && buff[curOffset + 3] >= 0x80 && buff[curOffset + 3] <= 0xBF) {
                    chBytes = 4;
                    ch = (((buff[curOffset] & 0x07) << 18) + ((buff[curOffset + 1] & 0x3f) << 12) + ((buff[curOffset + 2] & 0x3f) << 6) + (buff[curOffset + 3] & 0x3f));
                } else {
                    break;
                }
            } else if (buff[curOffset] <= 0xF3) {
                if (len - curOffset < 4) {
                    break;
                }
                if (buff[curOffset + 1] >= 0x80 && buff[curOffset + 1] <= 0xBF
                        && buff[curOffset + 2] >= 0x80 && buff[curOffset + 2] <= 0xBF
                        && buff[curOffset + 3] >= 0x80 && buff[curOffset + 3] <= 0xBF) {
                    chBytes = 4;
                    ch = (((buff[curOffset] & 0x07) << 18) + ((buff[curOffset + 1] & 0x3f) << 12) + ((buff[curOffset + 2] & 0x3f) << 6) + (buff[curOffset + 3] & 0x3f));
                } else {
                    break;
                }
            } else {
                break;
            }


            curOffset += chBytes;

            //skip if beyond range
            if (ch < 0 || ch > UNICODE_TABLE_SIZE - 1) {
                break;
            }

            //lookup byteVal in the unicode table
            SCRIPT scriptFound = getScript(ch);

            if (scriptFound == SCRIPT.NONE) {
                break;
            }

            /*else if (scriptFound == SCRIPT.CONTROL) {
             //update bytes processed
             res.numBytes += chBytes;
             continue;
             } else if (inControl) {
             break;
             }*/

            final boolean isGeneric = isGeneric(scriptFound);
            //allow generic and one of supported script we locked in to
            if (isGeneric
                    || scriptFound == SCRIPT.LATIN_2
                    || scriptFound == SCRIPT.ARABIC //|| scriptFound == SCRIPT.CYRILLIC
                    //|| scriptFound == SCRIPT.HAN
                    ) {

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
                    res.numBytes += chBytes;
                    //append the char
                    ++res.numChars;
                    curString.append((char) ch);
                } else {
                    //bail out ? check
                    break;
                }
            } else {
                //bail out ? check
                break;
            }

        } //no more data

        res.textString = curString.toString();



        return res;
    }

    private static boolean isGeneric(SCRIPT script) {
        return script == SCRIPT.COMMON || script == SCRIPT.LATIN_1;
    }

    /**
     * Representation of the string extraction result
     */
    public class StringExtractResult {

        int offset; ///< offset in input buffer where the first string starts (TODO not really needed)
        int numBytes; ///< num bytes in input buffer consumed
        int numChars; ///< number of encoded characters extracted in the textString
        String textString; ///< the actual text string extracted, of numChars long

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
    }
}
