/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import org.openide.util.NbBundle;

/**
 * Provides ability to convert text to hex text.
 */
public final class TextConverter {

    private static final char[] TMP = "hgleri21auty84fwe".toCharArray(); //NON-NLS
    private static final byte[] SALT = {(byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12, (byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12};

    /**
     * Convert text to hex text.
     *
     * @param property Input text string.
     *
     * @return Converted hex string.
     *
     * @throws org.sleuthkit.autopsy.coreutils.TextConverterException
     */
    public static String convertTextToHexText(String property) throws TextConverterException {
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES"); //NON-NLS
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(TMP));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES"); //NON-NLS
            pbeCipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
            return base64Encode(pbeCipher.doFinal(property.getBytes("UTF-8")));
        } catch (Exception ex) {
            throw new TextConverterException(NbBundle.getMessage(TextConverter.class, "TextConverter.convert.exception.txt"));
        }
    }

    public static String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Convert hex text back to text.
     *
     * @param property Input hex text string.
     *
     * @return Converted text string.
     *
     * @throws org.sleuthkit.autopsy.coreutils.TextConverterException
     */
    public static String convertHexTextToText(String property) throws TextConverterException {
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES"); //NON-NLS
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(TMP));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES"); //NON-NLS
            pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
            return new String(pbeCipher.doFinal(base64Decode(property)), "UTF-8");
        } catch (Exception ex) {
            throw new TextConverterException(NbBundle.getMessage(TextConverter.class, "TextConverter.convertFromHex.exception.txt"));
        }
    }

    public static byte[] base64Decode(String property) {
        return Base64.getDecoder().decode(property);
    }

}
