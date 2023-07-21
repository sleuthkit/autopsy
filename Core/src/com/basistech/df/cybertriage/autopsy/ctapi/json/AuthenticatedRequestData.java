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

/**
 * Data required for an authenticated request.
 */
public class AuthenticatedRequestData {

    private final String token;
    private final String apiKey;
    private final String hostId;
    
    public AuthenticatedRequestData(DecryptedLicenseResponse decrypted, AuthTokenResponse authResp) {
        this(authResp.getToken(), authResp.getApiKey(), decrypted.getLicenseHostId());
    }

    public AuthenticatedRequestData(String token, String apiKey, String hostId) {
        this.token = token;
        this.apiKey = apiKey;
        this.hostId = hostId;
    }

    public String getToken() {
        return token;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getHostId() {
        return hostId;
    }

}
