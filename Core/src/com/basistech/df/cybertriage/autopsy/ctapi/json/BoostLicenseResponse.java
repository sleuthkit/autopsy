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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for a boost license response object that is a part of the license
 * response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BoostLicenseResponse {

    private final String version;
    private final String iv;
    private final String encryptedKey;
    private final String encryptedJson;

    @JsonCreator
    public BoostLicenseResponse(
            @JsonProperty("version") String version,
            @JsonProperty("iv") String iv,
            @JsonProperty("encryptedKey") String encryptedKey,
            @JsonProperty("encryptedJson") String encryptedJson) {

        this.version = version;
        this.iv = iv;
        this.encryptedKey = encryptedKey;
        this.encryptedJson = encryptedJson;
    }

    public String getVersion() {
        return version;
    }

    public String getIv() {
        return iv;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public String getEncryptedJson() {
        return encryptedJson;
    }

}
