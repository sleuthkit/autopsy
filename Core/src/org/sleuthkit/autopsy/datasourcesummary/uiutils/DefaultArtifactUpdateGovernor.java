/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.Set;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;

/**
 *
 * @author gregd
 */
public interface DefaultArtifactUpdateGovernor extends DefaultUpdateGovernor {
  
    @Override
    default boolean isRefreshRequired(ModuleDataEvent evt) {
        return getArtifactTypeIdsForRefresh().contains(evt.getBlackboardArtifactType().getTypeID());
    }
    
    Set<Integer> getArtifactTypeIdsForRefresh();
}
