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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response POJO for request for license.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
