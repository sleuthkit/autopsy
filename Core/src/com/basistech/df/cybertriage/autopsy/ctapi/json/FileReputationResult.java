/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2023 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.ctapi.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * A file reputation result regarding malware status.
 */
public class FileReputationResult {

    public static enum Status {
        FOUND,
        NOT_FOUND,
        ERROR,
        LIMITS_EXCEEDED,
        BEING_SCANNED;
    }

    public enum CorrelationFrequency {
        UNIQUE,
        RARE,
        COMMON;
    }

    private final String malwareDescription;
    private final Status status;
    private final CTScore score;
    private final String md5Hash;
    private final String sha1Hash;
    private final ZonedDateTime firstScanDate;
    private final ZonedDateTime lastScanDate;
    private final List<MetadataLabel> metadata;
    private final String statusDescription;
    private final CorrelationFrequency frequency;
    private final String frequencyDescription;

    @JsonCreator
    public FileReputationResult(
            @JsonProperty("malwareDescription") String malwareDescription,
            @JsonProperty("status") Status status,
            @JsonProperty("score") CTScore score,
            @JsonProperty("md5Hash") String md5Hash,
            @JsonProperty("sha1Hash") String sha1Hash,
            @JsonProperty("firstScanDate") ZonedDateTime firstScanDate,
            @JsonProperty("lastScanDate") ZonedDateTime lastScanDate,
            @JsonProperty("metadata") List<MetadataLabel> metadata,
            @JsonProperty("statusDescription") String statusDescription,
            @JsonProperty("frequency") CorrelationFrequency frequency,
            @JsonProperty("frequencyDescription") String frequencyDescription
    ) {
        this.malwareDescription = malwareDescription;
        this.status = status;
        this.score = score;
        this.md5Hash = md5Hash;
        this.sha1Hash = sha1Hash;
        this.firstScanDate = firstScanDate;
        this.lastScanDate = lastScanDate;
        this.metadata = metadata;
        this.statusDescription = statusDescription;
        this.frequency = frequency;
        this.frequencyDescription = frequencyDescription;
    }

    public String getMalwareDescription() {
        return malwareDescription;
    }

    public Status getStatus() {
        return status;
    }

    public CTScore getScore() {
        return score;
    }

    public String getMd5Hash() {
        return md5Hash;
    }

    public String getSha1Hash() {
        return sha1Hash;
    }

    public ZonedDateTime getFirstScanDate() {
        return firstScanDate;
    }

    public ZonedDateTime getLastScanDate() {
        return lastScanDate;
    }

    public List<MetadataLabel> getMetadata() {
        return metadata;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public CorrelationFrequency getFrequency() {
        return frequency;
    }

    public String getFrequencyDescription() {
        return frequencyDescription;
    }

}
