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

/**
 * POJO for a boost license response object that is a part of the license
 * response.
 */
public class BoostLicenseResponse {

    private final String version;
    private final String iv;
    private final String encryptedKey;
    private final String encryptedJson;

    @JsonCreator
    public BoostLicenseResponse(
            @JsonProperty("version") String version,
            @JsonProperty("iv") String iv,
            @JsonProperty("encryptedKey") String encryptedKey,
            @JsonProperty("encryptedJson") String encryptedJson) {

        this.version = version;
        this.iv = iv;
        this.encryptedKey = encryptedKey;
        this.encryptedJson = encryptedJson;
    }

    public String getVersion() {
        return version;
    }

    public String getIv() {
        return iv;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public String getEncryptedJson() {
        return encryptedJson;
    }

}
