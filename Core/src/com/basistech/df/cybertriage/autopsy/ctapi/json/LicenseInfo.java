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
package com.basistech.df.cybertriage.autopsy.ctapi.json;

/**
 * Contains license info and decrypted boost license.
 */
public class LicenseInfo {
    private final LicenseResponse licenseResponse;
    private final DecryptedLicenseResponse decryptedLicense;

    public LicenseInfo(LicenseResponse licenseResponse, DecryptedLicenseResponse decryptedLicense) {
        this.licenseResponse = licenseResponse;
        this.decryptedLicense = decryptedLicense;
    }

    public LicenseResponse getLicenseResponse() {
        return licenseResponse;
    }

    public DecryptedLicenseResponse getDecryptedLicense() {
        return decryptedLicense;
    }
}
