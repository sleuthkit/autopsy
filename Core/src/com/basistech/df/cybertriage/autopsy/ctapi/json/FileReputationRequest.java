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
import java.util.List;

/**
 * Request for file reputation results.
 */
public class FileReputationRequest extends AuthenticatedRequestData {

    @JsonProperty("hashes")
    private List<String> hashes;

    @JsonProperty("host_id")
    private String hostId;

    public List<String> getHashes() {
        return hashes;
    }

    public FileReputationRequest setHashes(List<String> hashes) {
        this.hashes = hashes;
        return this;
    }

    public FileReputationRequest setToken(String token) {
        this.token = token;
        return this;
    }

    public FileReputationRequest setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getHostId() {
        return hostId;
    }

    public FileReputationRequest setHostId(String hostId) {
        this.hostId = hostId;
        return this;
    }

}
