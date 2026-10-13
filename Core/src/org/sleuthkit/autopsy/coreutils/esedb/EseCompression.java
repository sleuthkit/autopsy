/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils.esedb;

import java.io.ByteArrayOutputStream;

/**
 * Decompression of ESE column values. The first byte of a compressed value
 * identifies the scheme in its upper 5 bits: 7-bit ASCII, 7-bit Unicode or
 * LZXPRESS (plain LZ77, see [MS-XCA]). XPRESS9 and XPRESS10 are not
 * supported.
 */
final class EseCompression {

    static final int SCHEME_NONE = 0;
    static final int SCHEME_7BIT_ASCII = 1;
    static final int SCHEME_7BIT_UNICODE = 2;
    static final int SCHEME_XPRESS = 3;
    static final int SCHEME_SCRUB = 4;
    static final int SCHEME_XPRESS9 = 5;
    static final int SCHEME_XPRESS10 = 6;

    private EseCompression() {
    }

    /**
     * Decompresses a value according to the scheme encoded in its first
     * byte. Values with no recognised scheme are returned unchanged.
     */
    static byte[] decompress(byte[] data) throws EseException {
        if (data.length == 0) {
            return data;
        }
        int scheme = (data[0] & 0xFF) >> 3;
        switch (scheme) {
            case SCHEME_7BIT_ASCII:
                return sevenBit(data, 1, false);
            case SCHEME_7BIT_UNICODE:
                return sevenBit(data, 1, true);
            case SCHEME_XPRESS:
                if (data.length < 3) {
                    throw new EseException("LZXPRESS value is too short");
                }
                // The header carries the uncompressed size; the stream may not produce more than that.
                int uncompressedSize = readShort(data, 1);
                return lzxpress(data, 3, uncompressedSize);
            case SCHEME_XPRESS9:
            case SCHEME_XPRESS10:
                throw new EseException(String.format("Compression scheme %d (XPRESS%s) is not supported", scheme, scheme == SCHEME_XPRESS9 ? "9" : "10"));
            default:
                return data;
        }
    }

    /**
     * Unpacks 7-bit characters stored least significant bit first.
     *
     * @param wide True to produce UTF-16LE (each character followed by a 0
     *             byte).
     */
    private static byte[] sevenBit(byte[] data, int start, boolean wide) {
        ByteArrayOutputStream out = new ByteArrayOutputStream((data.length - start) * 8 / 7 * (wide ? 2 : 1) + 2);
        int value = 0;
        int shift = 0;
        for (int i = start; i < data.length; i++) {
            value |= (data[i] & 0xFF) << shift;
            out.write(value & 0x7F);
            if (wide) {
                out.write(0);
            }
            value >>= 7;
            shift += 1;
            if (shift == 7) {
                out.write(value & 0x7F);
                if (wide) {
                    out.write(0);
                }
                value >>= 7;
                shift = 0;
            }
        }
        return out.toByteArray();
    }

    /**
     * Upper bound on the output of a stream whose uncompressed size is not
     * known from a header.
     */
    static final int MAX_LZXPRESS_OUTPUT = 64 * 1024 * 1024;

    /**
     * Plain LZ77 decompression as specified in [MS-XCA] section 2.3.
     *
     * @param src       Buffer holding the compressed stream.
     * @param start     Offset of the stream in the buffer.
     * @param maxOutput Size the decompressed data may not exceed (the
     *                  uncompressed size announced by the caller's header,
     *                  or MAX_LZXPRESS_OUTPUT).
     */
    static byte[] lzxpress(byte[] src, int start, int maxOutput) throws EseException {
        byte[] out = new byte[Math.max(64, Math.min(maxOutput, (src.length - start) * 4))];
        int outSize = 0;
        int pos = start;
        int size = src.length;
        long bufferedFlags = 0;
        int bufferedFlagCount = 0;
        int lastLengthHalfByte = -1;
        try {
            while (pos < size) {
                if (bufferedFlagCount == 0) {
                    bufferedFlags = readInt(src, pos);
                    pos += 4;
                    bufferedFlagCount = 32;
                }
                bufferedFlagCount--;
                if ((bufferedFlags & (1L << bufferedFlagCount)) == 0) {
                    if (outSize + 1 > maxOutput) {
                        throw new EseException("LZXPRESS data is larger than its announced size");
                    }
                    out = ensureCapacity(out, outSize + 1);
                    out[outSize++] = src[pos++];
                } else {
                    if (pos == size) {
                        break;
                    }
                    int match = readShort(src, pos);
                    pos += 2;
                    int matchOffset = (match / 8) + 1;
                    int matchLength = match % 8;
                    if (matchLength == 7) {
                        if (lastLengthHalfByte < 0) {
                            lastLengthHalfByte = pos;
                            matchLength = (src[pos++] & 0xFF) % 16;
                        } else {
                            matchLength = (src[lastLengthHalfByte] & 0xFF) / 16;
                            lastLengthHalfByte = -1;
                        }
                        if (matchLength == 15) {
                            matchLength = src[pos++] & 0xFF;
                            if (matchLength == 255) {
                                matchLength = readShort(src, pos);
                                pos += 2;
                                if (matchLength == 0) {
                                    matchLength = (int) readInt(src, pos);
                                    pos += 4;
                                }
                                if (matchLength < 15 + 7) {
                                    throw new EseException("Invalid LZXPRESS match length");
                                }
                                matchLength -= 15 + 7;
                            }
                            matchLength += 15;
                        }
                        matchLength += 7;
                    }
                    matchLength += 3;
                    if (matchOffset > outSize) {
                        throw new EseException("Invalid LZXPRESS match offset");
                    }
                    if (matchLength < 0 || matchLength > maxOutput - outSize) {
                        throw new EseException("LZXPRESS match length exceeds the announced size");
                    }
                    out = ensureCapacity(out, outSize + matchLength);
                    // Copy byte by byte: the match may overlap the bytes being written.
                    for (int i = 0; i < matchLength; i++) {
                        out[outSize] = out[outSize - matchOffset];
                        outSize++;
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new EseException("Truncated LZXPRESS data", ex);
        }
        byte[] result = new byte[outSize];
        System.arraycopy(out, 0, result, 0, outSize);
        return result;
    }

    private static byte[] ensureCapacity(byte[] buffer, int needed) {
        if (buffer.length >= needed) {
            return buffer;
        }
        byte[] grown = new byte[Math.max(buffer.length * 2, needed + 256)];
        System.arraycopy(buffer, 0, grown, 0, buffer.length);
        return grown;
    }

    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readInt(byte[] data, int offset) {
        return (data[offset] & 0xFFL) | ((data[offset + 1] & 0xFFL) << 8) | ((data[offset + 2] & 0xFFL) << 16) | ((data[offset + 3] & 0xFFL) << 24);
    }
}
