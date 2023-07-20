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
 * POJO for license request information.
 */
public class LicenseRequest {
    @JsonProperty("host_id")
    private String hostId;
    
    @JsonProperty("boost_license_code")
    private String boostLicenseCode;
    
    @JsonProperty("product")
    private String product;

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
    
    
}
