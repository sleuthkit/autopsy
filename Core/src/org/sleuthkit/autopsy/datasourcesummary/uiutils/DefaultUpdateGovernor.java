/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.Set;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;

/**
 *
 * @author gregd
 */
public interface DefaultUpdateGovernor extends UpdateGovernor {

    @Override
    default boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt) {
        return false;
    }

    @Override
    default boolean isRefreshRequired(ModuleContentEvent evt) {
        return false;
    }

    @Override
    default boolean isRefreshRequired(ModuleDataEvent evt) {
        return false;
    }

    @Override
    default Set<Case.Events> getCaseEventUpdates() {
        return Collections.emptySet();
    }

}
