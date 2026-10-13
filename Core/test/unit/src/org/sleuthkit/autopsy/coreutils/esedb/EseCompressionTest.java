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

import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests of the ESE value decompression schemes with hand-built vectors.
 */
public class EseCompressionTest {

    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(text.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    @Test
    public void lzxpressShortMatch() throws EseException {
        // flags 0x10000000: three literals then one match; match = offset 3, length 9 (3 + 6).
        byte[] decompressed = EseCompression.lzxpress(hex("00000010" + "616263" + "1600"), 0);
        Assert.assertEquals("abcabcabcabc", new String(decompressed, StandardCharsets.US_ASCII));
    }

    @Test
    public void lzxpressMatchLengthInHalfByte() throws EseException {
        // 'a' then a match of 20 - 1 = 19 bytes at offset 1: length field 7, extra half byte 9.
        byte[] decompressed = EseCompression.lzxpress(hex("00000040" + "61" + "0700" + "09"), 0);
        Assert.assertEquals(20, decompressed.length);
        for (byte b : decompressed) {
            Assert.assertEquals('a', b);
        }
    }

    @Test
    public void lzxpressLongMatchLength() throws EseException {
        // 'b' then a match of 300 bytes: half byte 15, byte 255, 16 bit length.
        byte[] decompressed = EseCompression.lzxpress(hex("00000040" + "62" + "0700" + "0f" + "ff" + "2901"), 0);
        Assert.assertEquals(301, decompressed.length);
        for (byte b : decompressed) {
            Assert.assertEquals('b', b);
        }
    }

    @Test
    public void lzxpressRejectsMatchBeforeStart() {
        try {
            EseCompression.lzxpress(hex("00000080" + "1600"), 0);
            Assert.fail("expected an EseException");
        } catch (EseException expected) {
            // ok
        }
    }

    @Test
    public void sevenBitAscii() throws EseException {
        // Scheme 1 in the upper 5 bits of the first byte, then the packed 7 bit characters.
        byte[] decompressed = EseCompression.decompress(hex("08" + "c8329bfd6681ae6f399b1c02c564335acd76c3e560"));
        String text = new String(decompressed, StandardCharsets.US_ASCII);
        Assert.assertTrue(text, text.startsWith("Hello, World! 1234567890"));
    }

    @Test
    public void sevenBitUnicode() throws EseException {
        byte[] decompressed = EseCompression.decompress(hex("10" + "c87408"));
        String text = new String(decompressed, StandardCharsets.UTF_16LE);
        Assert.assertTrue(text, text.startsWith("Hi!"));
    }

    @Test
    public void xpressWrapperCarriesSizeHeader() throws EseException {
        // Scheme 3: header byte, 16 bit uncompressed size, LZXPRESS stream.
        byte[] decompressed = EseCompression.decompress(hex("18" + "0c00" + "00000010" + "616263" + "1600"));
        Assert.assertEquals("abcabcabcabc", new String(decompressed, StandardCharsets.US_ASCII));
    }

    @Test
    public void uncompressedDataIsReturnedUnchanged() throws EseException {
        byte[] data = hex("0001020304");
        Assert.assertArrayEquals(data, EseCompression.decompress(data));
    }

    @Test
    public void xpress9IsReported() {
        try {
            EseCompression.decompress(hex("28" + "0000"));
            Assert.fail("expected an EseException");
        } catch (EseException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("XPRESS9"));
        }
    }
}
