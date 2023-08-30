/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.ctapi.util;

import com.basistech.df.cybertriage.autopsy.ctapi.json.BoostLicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.DecryptedLicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseInfo;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.MessageFormat;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Decrypts the payload of boost license.
 */
public class LicenseDecryptorUtil {

    private static final LicenseDecryptorUtil instance = new LicenseDecryptorUtil();

    public static LicenseDecryptorUtil getInstance() {
        return instance;
    }

    private final ObjectMapper objectMapper = ObjectMapperUtil.getInstance().getDefaultObjectMapper();

    private LicenseDecryptorUtil() {
    }

    public LicenseInfo createLicenseInfo(LicenseResponse licenseResponse) throws JsonProcessingException, InvalidLicenseException {
        if (licenseResponse == null) {
            throw new InvalidLicenseException("License is null");
        }

        DecryptedLicenseResponse decrypted = parseLicenseJSON(licenseResponse.getBoostLicense());
        return new LicenseInfo(licenseResponse, decrypted);
    }

    /**
     * Decrypts a boost license response.
     *
     * @param licenseResponse The boost license response.
     * @return The decrypted license response.
     * @throws JsonProcessingException
     * @throws
     * com.basistech.df.cybertriage.autopsy.ctapi.util.LicenseDecryptorUtil.InvalidLicenseException
     */
    public DecryptedLicenseResponse parseLicenseJSON(BoostLicenseResponse licenseResponse) throws JsonProcessingException, InvalidLicenseException {
        if (licenseResponse == null) {
            throw new InvalidLicenseException("Boost license is null");
        }

        String decryptedJsonResponse;
        try {
            decryptedJsonResponse = decryptLicenseString(
                    licenseResponse.getEncryptedJson(),
                    licenseResponse.getIv(),
                    licenseResponse.getEncryptedKey(),
                    licenseResponse.getVersion()
            );
        } catch (IOException | GeneralSecurityException ex) {
            throw new InvalidLicenseException("An exception occurred while parsing the license string", ex);
        }

        DecryptedLicenseResponse decryptedLicense = objectMapper.readValue(decryptedJsonResponse, DecryptedLicenseResponse.class);
        if (!"AUTOPSY".equalsIgnoreCase(decryptedLicense.getProduct())) {
            // license file is expected to contain product of "CYBERTRIAGE"
            throw new InvalidLicenseException("Not a valid Autopsy license");
        }

        return decryptedLicense;
    }

    private String decryptLicenseString(String encryptedJson, String ivBase64, String encryptedKey, String version) throws IOException, GeneralSecurityException, InvalidLicenseException {
        if (ObjectUtils.anyNull(encryptedJson, ivBase64, encryptedKey, version)) {
            throw new InvalidLicenseException(MessageFormat.format(
                    "encryptedJson: {0}, iv: {1}, encryptedKey: {2}, version: {3} must all be non-null",
                    encryptedJson, ivBase64, encryptedKey, version));
        }

        if (!"1.0".equals(version)) {
            throw new InvalidLicenseException("Unexpected file version: " + version);
        }

        byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedKey);
        byte[] keyBytes = decryptKey(encryptedKeyBytes);
        SecretKey key = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");

        byte[] ivBytes = Base64.getDecoder().decode(ivBase64);
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        byte[] encryptedLicenseJsonBytes = Base64.getDecoder().decode(encryptedJson);

        String algorithm = "AES/CBC/PKCS5Padding";
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        byte[] licenseJsonBytes = cipher.doFinal(encryptedLicenseJsonBytes);

        return new String(licenseJsonBytes, StandardCharsets.UTF_8);
    }

    private PublicKey getPublicKey() throws InvalidKeySpecException, NoSuchAlgorithmException {

        String publicKeyString = """
                                 -----BEGIN PUBLIC KEY-----
                                 MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwIKulLyaLQ2WeO0gIW2G
                                 3jQqny3Y/7VUevBKulAEywaUbvECvZ4zGsnaMyACjXxMNkA1xU2WeSMP/WqC03wz
                                 4d71liUeAqOYKMdGHXFN2qswWz/ufK6An0pTEqYaoiUfcwSBVo2ZTUcMQexScKaS
                                 ghmaWqBHBYx+lBkVMcLG2PtLDRZbqgJvJr2QCzMSVUpEGGQEWs7YolIq46KCgqsq
                                 pTdfrdqd59x6oRhTLegswzxwLyouvrKbRqKR2ZRbVvlGtUnnnlLDuhEfd0flMxuv
                                 W98Siw6dWe1K3x45nDu5py2G9Q9fZS8/2KHUC6QcLLstLIoPnZjCl9Lcur1U6s9N
                                 f5aLI9mwMfmSJsoVOuwx2/MC98uHvPoPbG4ZjiT0aaGg4JccTGD6pssDA35zPhkk
                                 1l6wktEYtyF2A7zjzuFxioQz8fHBzIbHPCxzu4S2gh3qOVFf7c9COmX9MsnB70o2
                                 EZ1rxlFIJ7937IGJNwWOQuiMKTpEeT6BwTdQNZQPqCUGvZ5eEjhrm57yCF4zuyrt
                                 AR8DG7ahK2YAarADHRyxTuxH1qY7E5/CTQKYk9tIYsV4O05CKj7B8rBMtjVNjb4b
                                 d7JwPW43Z3J6jo/gLlVdGSPg8vQDNVLl6sdDM4Pm1eJEzgR2JlqXDCRDUGNNsXH2
                                 qt9Ru8ykX7PAfF2Q3/qg1jkCAwEAAQ==
                                 -----END PUBLIC KEY-----
                                 """;

        publicKeyString = publicKeyString.replaceAll("-----BEGIN PUBLIC KEY-----", "").replaceAll("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);

        KeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        return publicKey;
    }

    private byte[] decryptKey(byte[] encryptedKeyBytes) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PublicKey publicKey = getPublicKey();

        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, publicKey);

        byte[] decryptedBytes = decryptCipher.doFinal(encryptedKeyBytes);

        return decryptedBytes;
    }

    public class InvalidLicenseException extends Exception {

        public InvalidLicenseException(String message) {
            super(message);
        }

        public InvalidLicenseException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
