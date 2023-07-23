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

import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil.InstantEpochSecsDeserializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;

/**
 * POJO for an auth token response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthTokenResponse {

    private final Long hashLookupCount;
    private final Long hashLookupLimit;
    private final Long fileUploadLimit;
    private final Long fileUploadCount;
    private final String fileUploadUrl;
    private final Instant expiration;
    private final String token;
    private final String apiKey;

    @JsonCreator
    public AuthTokenResponse(
            @JsonProperty("token") String token,
            @JsonProperty("api_key") String apiKey,
            @JsonProperty("hashLookupCount") Long hashLookupCount,
            @JsonProperty("hashLookupLimit") Long hashLookupLimit,
            @JsonProperty("fileUploadLimit") Long fileUploadLimit,
            @JsonProperty("fileUploadCount") Long fileUploadCount,
            @JsonProperty("fileUploadUrl") String fileUploadUrl,
            @JsonDeserialize(using = InstantEpochSecsDeserializer.class)
            @JsonProperty("expiration") Instant expiration
    ) {
        this.token = token;
        this.apiKey = apiKey;
        this.hashLookupCount = hashLookupCount;
        this.hashLookupLimit = hashLookupLimit;
        this.fileUploadLimit = fileUploadLimit;
        this.fileUploadCount = fileUploadCount;
        this.fileUploadUrl = fileUploadUrl;
        this.expiration = expiration;
    }

    public Long getHashLookupCount() {
        return hashLookupCount;
    }

    public Long getHashLookupLimit() {
        return hashLookupLimit;
    }

    public Long getFileUploadLimit() {
        return fileUploadLimit;
    }

    public Long getFileUploadCount() {
        return fileUploadCount;
    }

    public String getFileUploadUrl() {
        return fileUploadUrl;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public String getToken() {
        return token;
    }

    public String getApiKey() {
        return apiKey;
    }

}
