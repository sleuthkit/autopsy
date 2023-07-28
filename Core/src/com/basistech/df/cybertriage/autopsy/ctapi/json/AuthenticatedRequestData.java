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
 * Data required for an authenticated request.
 */
public class AuthenticatedRequestData {

    private final String token;
    private final String apiKey;
    private final String hostId;
    
    public AuthenticatedRequestData(DecryptedLicenseResponse decrypted, AuthTokenResponse authResp) {
        this(authResp.getToken(), authResp.getApiKey(), decrypted.getLicenseHostId());
    }

    public AuthenticatedRequestData(String token, String apiKey, String hostId) {
        this.token = token;
        this.apiKey = apiKey;
        this.hostId = hostId;
    }

    public String getToken() {
        return token;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getHostId() {
        return hostId;
    }

}
