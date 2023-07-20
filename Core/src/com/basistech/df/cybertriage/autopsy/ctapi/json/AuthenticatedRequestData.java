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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data required for an authenticated request.
 */
public abstract class AuthenticatedRequestData {

    @JsonProperty("token")
    protected String token;
    @JsonProperty("api_key")
    protected String apiKey;

    public String getToken() {
        return token;
    }

    public String getApiKey() {
        return apiKey;
    }

}
