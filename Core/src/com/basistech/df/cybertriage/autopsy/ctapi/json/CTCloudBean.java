/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2020 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
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
