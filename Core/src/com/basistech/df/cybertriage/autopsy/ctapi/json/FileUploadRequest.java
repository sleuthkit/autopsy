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

import java.io.InputStream;

/**
 * Data for a file upload request.
 */
public class FileUploadRequest {

    private String fullUrlPath;
    private String fileName;
    private InputStream fileInputStream;
    private Long contentLength;

    public String getFullUrlPath() {
        return fullUrlPath;
    }

    public FileUploadRequest setFullUrlPath(String fullUrlPath) {
        this.fullUrlPath = fullUrlPath;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public FileUploadRequest setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public InputStream getFileInputStream() {
        return fileInputStream;
    }

    public FileUploadRequest setFileInputStream(InputStream fileInputStream) {
        this.fileInputStream = fileInputStream;
        return this;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public FileUploadRequest setContentLength(Long contentLength) {
        this.contentLength = contentLength;
        return this;
    }

}
