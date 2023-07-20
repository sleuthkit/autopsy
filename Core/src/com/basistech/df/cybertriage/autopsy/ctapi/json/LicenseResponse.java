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
 * Response POJO for request for license.
 */
public class LicenseResponse {
    private final Boolean success;
    private final Boolean hostChanged;
    private final Long hostChangesRemaining;
    private final BoostLicenseResponse boostLicense;

    @JsonCreator
    public LicenseResponse(
            @JsonProperty("success") Boolean success, 
            @JsonProperty("hostChanged") Boolean hostChanged, 
            @JsonProperty("hostChangesRemaining") Long hostChangesRemaining, 
            @JsonProperty("boostLicense") BoostLicenseResponse boostLicense
    ) {
        this.success = success;
        this.hostChanged = hostChanged;
        this.hostChangesRemaining = hostChangesRemaining;
        this.boostLicense = boostLicense;
    }

    public Boolean isSuccess() {
        return success;
    }

    public Boolean isHostChanged() {
        return hostChanged;
    }

    public Long getHostChangesRemaining() {
        return hostChangesRemaining;
    }

    public BoostLicenseResponse getBoostLicense() {
        return boostLicense;
    }

    
    
}
