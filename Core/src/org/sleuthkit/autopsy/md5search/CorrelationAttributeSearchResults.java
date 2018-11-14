/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 * //DLG:
 */
public class CorrelationAttributeSearchResults {
    List<CorrelationAttributeInstance> correlationInstances = new ArrayList<>();
    
    CorrelationAttributeSearchResults(List<CorrelationAttributeInstance> correlationInstances) {
        this.correlationInstances = correlationInstances;
    }
    
    List<CorrelationAttributeInstance> getCorrelationAttributeInstances() {
        return correlationInstances;
    }
}
