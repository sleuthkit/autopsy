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

import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil.InstantEpochMillisDeserializer;
import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil.MDYDateDeserializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * POJO for after encrypted boost license has been decrypted.
 */
public class DecryptedLicenseResponse {

    private final String boostLicenseId;
    private final String licenseHostId;
    private final ZonedDateTime expirationDate;
    private final Long hashLookups;
    private final Long fileUploads;
    private final Instant activationTime;
    private final String product;
    private final String limitType;

    @JsonCreator
    public DecryptedLicenseResponse(
            @JsonProperty("boostLicenseId") String boostLicenseId,
            @JsonProperty("licenseHostId") String licenseHostId,
            @JsonDeserialize(using=MDYDateDeserializer.class)
            @JsonProperty("expirationDate") ZonedDateTime expirationDate,
            @JsonProperty("hashLookups") Long hashLookups,
            @JsonProperty("fileUploads") Long fileUploads,
            @JsonDeserialize(using=InstantEpochMillisDeserializer.class)
            @JsonProperty("activationTime") Instant activationTime,
            @JsonProperty("product") String product,
            @JsonProperty("limitType") String limitType,
                    
                @JsonProperty("l4jLicenseId") String l4jlicenseId,
                @JsonProperty("ctLicenseId") String ctLicenseId
    ) {
        this.boostLicenseId = boostLicenseId;
        this.licenseHostId = licenseHostId;
        this.expirationDate = expirationDate;
        this.hashLookups = hashLookups;
        this.fileUploads = fileUploads;
        this.activationTime = activationTime;
        this.product = product;
        this.limitType = limitType;
    }

    public String getBoostLicenseId() {
        return boostLicenseId;
    }

    public String getLicenseHostId() {
        return licenseHostId;
    }

    public Long getHashLookups() {
        return hashLookups;
    }

    public Long getFileUploads() {
        return fileUploads;
    }

    public Instant getActivationTime() {
        return activationTime;
    }

    public String getProduct() {
        return product;
    }

    public String getLimitType() {
        return limitType;
    }

    public ZonedDateTime getExpirationDate() {
        return expirationDate;
    }

}
