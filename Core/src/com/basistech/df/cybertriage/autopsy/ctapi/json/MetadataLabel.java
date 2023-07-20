/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.basistech.df.cybertriage.autopsy.ctapi.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author gregd
 */
public class MetadataLabel {

    private final String key;
    private final String value;
    private final String extendedInfo;
    
    @JsonCreator
    public MetadataLabel(
            @JsonProperty("key") String key, 
            @JsonProperty("value") String value, 
            @JsonProperty("info") String extendedInfo
    ) {
        this.key = key;
        this.value = value;
        this.extendedInfo = extendedInfo;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
    
    public String getExtendedInfo() {
        return extendedInfo;
    }
    
}
