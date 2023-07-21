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
 *
 * @author rishwanth
 */
class CTCloudCostBean {

    private final String provider;

    private final Integer units;

    public CTCloudCostBean(@JsonProperty("provider") String provider, @JsonProperty("units") Integer units) {
        this.provider = provider;
        this.units = units;
    }

    public String getProvider() {
        return provider;
    }

    public Integer getUnits() {
        return units;
    }

}
