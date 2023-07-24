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

public class MetadataUploadRequest {

    @JsonProperty("file_upload_url")
    private String fileUploadUrl;

    @JsonProperty("sha1")
    private String sha1;

    @JsonProperty("sha256")
    private String sha256;

    @JsonProperty("md5")
    private String md5;

    @JsonProperty("filePath")
    private String filePath;

    @JsonProperty("fileSize")
    private long fileSizeBytes;

    @JsonProperty("createdDate")
    private long createdDate;

    public String getFileUploadUrl() {
        return fileUploadUrl;
    }

    public MetadataUploadRequest setFileUploadUrl(String fileUploadUrl) {
        this.fileUploadUrl = fileUploadUrl;
        return this;
    }

    public String getSha1() {
        return sha1;
    }

    public MetadataUploadRequest setSha1(String sha1) {
        this.sha1 = sha1;
        return this;
    }

    public String getSha256() {
        return sha256;
    }

    public MetadataUploadRequest setSha256(String sha256) {
        this.sha256 = sha256;
        return this;
    }

    public String getMd5() {
        return md5;
    }

    public MetadataUploadRequest setMd5(String md5) {
        this.md5 = md5;
        return this;
    }

    public String getFilePath() {
        return filePath;
    }

    public MetadataUploadRequest setFilePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public MetadataUploadRequest setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
        return this;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public MetadataUploadRequest setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
        return this;
    }

}
