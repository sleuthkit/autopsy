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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 *
 * @author rishwanth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CTCloudBean {

    public static enum Status {
        FOUND,
        NOT_FOUND,
        ERROR,
        LIMITS_EXCEEDED,
        BEING_SCANNED;
    }

    @Nonnull
    @JsonProperty("malware")
    private MalwareResultBean malwareResult;

    @JsonProperty("correlation")
    private CorrelationResultBean correlationResult;

    @Nonnull
    @JsonProperty("md5_hash")
    private String md5HashValue;

    @Nullable
    @JsonProperty("sha1_hash")
    private String sha1HashValue;

    public String getMd5HashValue() {
        return md5HashValue;
    }

    public String getSha1HashValue() {
        return sha1HashValue;
    }

    public void setMd5HashValue(String md5HashValue) {
        this.md5HashValue = md5HashValue;
    }

    public void setSha1HashValue(String sha1HashValue) {
        this.sha1HashValue = sha1HashValue;
    }

    public MalwareResultBean getMalwareResult() {
        return malwareResult;
    }

    public void setMalwareResult(MalwareResultBean malwareResult) {
        this.malwareResult = malwareResult;
    }

    public CorrelationResultBean getCorrelationResult() {
        return correlationResult;
    }

    public void setCorrelationResult(CorrelationResultBean correlationResult) {
        this.correlationResult = correlationResult;
    }
    
    @Override
    public String toString() {
        return "CTCloudBean{"
                + "status=" + malwareResult.getStatus()
                + ", malwareDescription=" + malwareResult.getMalwareDescription()
                + ", score=" + malwareResult.getCTScore()
                + ", md5HashValue=" + md5HashValue
                + ", sha1HashValue=" + sha1HashValue
                + ", firstSeen=" + malwareResult.getFirstAnalyzedDate()
                + ", lastSeen=" + malwareResult.getLastAnalyzedDate()
                + ", statusDescription=" + malwareResult.getStatusDescription()
                + ", metadata=" + malwareResult.getMetadata()
                + '}';
    }

}
