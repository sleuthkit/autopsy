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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for an auth token request.
 */
public class AuthTokenRequest {

    @JsonProperty("autopsy_version")
    private String autopsyVersion;

    @JsonProperty("boost_license_id")
    private String boostLicenseId;

    @JsonProperty("requestFileUpload")
    private boolean requestFileUpload;

    @JsonProperty("fileUploadSize")
    private Long fileUploadSize;

    @JsonProperty("host_id")
    private String hostId;

    public String getAutopsyVersion() {
        return autopsyVersion;
    }

    public AuthTokenRequest setAutopsyVersion(String autopsyVersion) {
        this.autopsyVersion = autopsyVersion;
        return this;
    }

    public String getBoostLicenseId() {
        return boostLicenseId;
    }

    public AuthTokenRequest setBoostLicenseId(String boostLicenseId) {
        this.boostLicenseId = boostLicenseId;
        return this;
    }

    public boolean isRequestFileUpload() {
        return requestFileUpload;
    }

    public AuthTokenRequest setRequestFileUpload(boolean requestFileUpload) {
        this.requestFileUpload = requestFileUpload;
        return this;
    }

    public Long getFileUploadSize() {
        return fileUploadSize;
    }

    public AuthTokenRequest setFileUploadSize(Long fileUploadSize) {
        this.fileUploadSize = fileUploadSize;
        return this;
    }

    
    public String getHostId() {
        return hostId;
    }

    public AuthTokenRequest setHostId(String hostId) {
        this.hostId = hostId;
        return this;
    }

}
