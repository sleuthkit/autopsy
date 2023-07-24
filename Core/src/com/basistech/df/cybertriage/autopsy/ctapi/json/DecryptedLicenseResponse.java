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

import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil.InstantEpochMillisDeserializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;

/**
 * POJO for after encrypted boost license has been decrypted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecryptedLicenseResponse {

    private final String boostLicenseId;
    private final String licenseHostId;
    private final Instant expirationDate;
    private final Long hashLookups;
    private final Long fileUploads;
    private final Instant activationTime;
    private final String product;
    private final String limitType;
    private final String timezone;
    private final String customerEmail;

    @JsonCreator
    public DecryptedLicenseResponse(
            @JsonProperty("boostLicenseId") String boostLicenseId,
            @JsonProperty("licenseHostId") String licenseHostId,
            @JsonDeserialize(using = InstantEpochMillisDeserializer.class)
            @JsonProperty("expirationDate") Instant expirationDate,
            @JsonProperty("hashLookups") Long hashLookups,
            @JsonProperty("fileUploads") Long fileUploads,
            @JsonDeserialize(using = InstantEpochMillisDeserializer.class)
            @JsonProperty("activationTime") Instant activationTime,
            @JsonProperty("product") String product,
            @JsonProperty("limitType") String limitType,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("customerEmail") String customerEmail
    ) {
        this.boostLicenseId = boostLicenseId;
        this.licenseHostId = licenseHostId;
        this.expirationDate = expirationDate;
        this.hashLookups = hashLookups;
        this.fileUploads = fileUploads;
        this.activationTime = activationTime;
        this.product = product;
        this.limitType = limitType;
        this.timezone = timezone;
        this.customerEmail = customerEmail;
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

    public Instant getExpirationDate() {
        return expirationDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }
}
