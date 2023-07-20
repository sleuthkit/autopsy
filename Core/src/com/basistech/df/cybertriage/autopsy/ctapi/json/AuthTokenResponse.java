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

/**
 * POJO for an auth token response.
 */
public class AuthTokenResponse {
    private final String token;
    private final String apiKey;
    private final Long hashLookupCount;
    private final Long hashLookupLimit;
    private final Long fileUploadLimit;
    private final Long fileUploadCount;
    private final String fileUploadUrl;
    private final ZonedDateTime expiration;

    @JsonCreator
    public AuthTokenResponse(
            @JsonProperty("token") String token,
            @JsonProperty("api_key") String apiKey,
            @JsonProperty("hashLookupCount") Long hashLookupCount,
            @JsonProperty("hashLookupLimit") Long hashLookupLimit,
            @JsonProperty("fileUploadLimit") Long fileUploadLimit,
            @JsonProperty("fileUploadCount") Long fileUploadCount,
            @JsonProperty("fileUploadUrl") String fileUploadUrl,
            @JsonProperty("expiration") ZonedDateTime expiration
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

    public String getToken() {
        return token;
    }

    public String getApiKey() {
        return apiKey;
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

    public ZonedDateTime getExpiration() {
        return expiration;
    }
}
