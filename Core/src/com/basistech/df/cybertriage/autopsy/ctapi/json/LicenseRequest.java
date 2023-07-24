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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for license request information.
 */
public class LicenseRequest {
    @JsonProperty("host_id")
    private String hostId;
    
    @JsonProperty("boost_license_code")
    private String boostLicenseCode;
    
    @JsonProperty("product")
    private String product;
    
    @JsonProperty("time_zone_id")
    private String timeZoneId;

    public String getHostId() {
        return hostId;
    }

    public LicenseRequest setHostId(String hostId) {
        this.hostId = hostId;
        return this;
    }

    public String getBoostLicenseCode() {
        return boostLicenseCode;
    }

    public LicenseRequest setBoostLicenseCode(String boostLicenseCode) {
        this.boostLicenseCode = boostLicenseCode;
        return this;
    }

    public String getProduct() {
        return product;
    }

    public LicenseRequest setProduct(String product) {
        this.product = product;
        return this;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public LicenseRequest setTimeZoneId(String timeZoneId) {
        this.timeZoneId = timeZoneId;
        return this;
    }
    
    
    
}
