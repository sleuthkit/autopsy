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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.openide.util.NbBundle;

/**
 * Provides ability to convert text to hex text.
 *
 * Encryption uses AES-256-GCM with PBKDF2WithHmacSHA256 key derivation.
 * Values encrypted with the legacy PBEWithMD5AndDES scheme (no "v2:" prefix)
 * are still accepted for decryption to allow backward compatibility.
 */
public final class TextConverter {

    // Kept for backward-compatible decryption of legacy-encrypted values only.
    private static final char[] LEGACY_KEY = "hgleri21auty84fwe".toCharArray(); //NON-NLS
    private static final byte[] LEGACY_SALT = {(byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12, (byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12};

    // Passphrase used with PBKDF2 for the v2 scheme. The random per-value
    // salt stored in the ciphertext prevents precomputation even though this
    // passphrase is in the source code.
    private static final char[] V2_PASSPHRASE = "hgleri21auty84fwe".toCharArray(); //NON-NLS

    private static final String V2_PREFIX = "v2:"; //NON-NLS
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;

    /**
     * Encrypt text using AES-256-GCM with PBKDF2WithHmacSHA256 key derivation.
     * The returned string starts with "v2:" to distinguish it from values
     * produced by the legacy scheme.
     *
     * @param property Input text string.
     *
     * @return Encrypted string.
     *
     * @throws org.sleuthkit.autopsy.coreutils.TextConverterException
     */
    public static String convertTextToHexText(String property) throws TextConverterException {
        try {
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(salt);
            new SecureRandom().nextBytes(iv);

            SecretKey key = deriveV2Key(salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); //NON-NLS
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(property.getBytes(StandardCharsets.UTF_8));

            // Store: salt || iv || ciphertext (includes GCM auth tag)
            byte[] combined = new byte[SALT_BYTES + IV_BYTES + ciphertext.length];
            System.arraycopy(salt, 0, combined, 0, SALT_BYTES);
            System.arraycopy(iv, 0, combined, SALT_BYTES, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, SALT_BYTES + IV_BYTES, ciphertext.length);

            return V2_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new TextConverterException(NbBundle.getMessage(TextConverter.class, "TextConverter.convert.exception.txt"));
        }
    }

    public static String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Decrypt a previously encrypted string. Accepts both the current v2
     * format ("v2:" prefix) and the legacy PBEWithMD5AndDES format (no prefix)
     * to allow backward compatibility with existing configuration files.
     *
     * @param property Input encrypted text string.
     *
     * @return Decrypted text string.
     *
     * @throws org.sleuthkit.autopsy.coreutils.TextConverterException
     */
    public static String convertHexTextToText(String property) throws TextConverterException {
        if (property != null && property.startsWith(V2_PREFIX)) {
            return decryptV2(property.substring(V2_PREFIX.length()));
        } else {
            return legacyDecrypt(property);
        }
    }

    public static byte[] base64Decode(String property) {
        return Base64.getDecoder().decode(property);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String decryptV2(String b64) throws TextConverterException {
        try {
            byte[] combined = Base64.getDecoder().decode(b64);
            if (combined.length < SALT_BYTES + IV_BYTES + GCM_TAG_BITS / 8) {
                throw new TextConverterException(NbBundle.getMessage(TextConverter.class, "TextConverter.convertFromHex.exception.txt"));
            }
            byte[] salt = Arrays.copyOfRange(combined, 0, SALT_BYTES);
            byte[] iv = Arrays.copyOfRange(combined, SALT_BYTES, SALT_BYTES + IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, SALT_BYTES + IV_BYTES, combined.length);

            SecretKey key = deriveV2Key(salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); //NON-NLS
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (TextConverterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TextConverterException(NbBundle.getMessage(TextConverter.class, "TextConverter.convertFromHex.exception.txt"));
        }
    }

    private static SecretKey deriveV2Key(byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256"); //NON-NLS
        KeySpec spec = new PBEKeySpec(V2_PASSPHRASE, salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES"); //NON-NLS
    }

    /**
     * Decrypts values produced by the legacy PBEWithMD5AndDES scheme.
     * Kept exclusively for backward compatibility with existing config files.
     */
    private static String legacyDecrypt(String property) throws TextConverterException {
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES"); //NON-NLS
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(LEGACY_KEY));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES"); //NON-NLS
            pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(LEGACY_SALT, 20));
            return new String(pbeCipher.doFinal(base64Decode(property)), "UTF-8"); //NON-NLS
        } catch (Exception ex) {
            throw new TextConverterException(NbBundle.getMessage(TextConverter.class, "TextConverter.convertFromHex.exception.txt"));
        }
    }

}
