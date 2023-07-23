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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CTCloudCorrelationResultBean {

    @JsonProperty("correlation")
    private CorrelationResultBean correlationResult;

    @Nonnull
    @JsonProperty("signature")
    private String signature;

    public CorrelationResultBean getCorrelationResult() {
        return correlationResult;
    }

    public void setCorrelationResult(CorrelationResultBean correlationResult) {
        this.correlationResult = correlationResult;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "CTCloudCorrelationResultBean{"
                + "correlationResult=" + correlationResult
                + ", signature=" + signature
                + '}';
    }
}
