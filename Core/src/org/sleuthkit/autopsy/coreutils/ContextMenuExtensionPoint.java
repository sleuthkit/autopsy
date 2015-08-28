/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 - 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import org.openide.util.Lookup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.Action;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;

/**
 * This class implements the ContextMenuActionsProvider extension point.
 */
public class ContextMenuExtensionPoint {

    /**
     * Gets all of the Actions provided by registered implementers of the
     * ContextMenuActionsProvider interface.
     *
     * @return A list, possibly empty, of Action objects.
     */
    static public List<Action> getActions() {
        ArrayList<Action> actions = new ArrayList<>();
        Collection<? extends ContextMenuActionsProvider> actionProviders = Lookup.getDefault().lookupAll(ContextMenuActionsProvider.class);
        for (ContextMenuActionsProvider provider : actionProviders) {
            List<Action> providerActions = provider.getActions();
            if (!providerActions.isEmpty()) {
                actions.add(null); // Separator to set off this provider's actions.
                actions.addAll(provider.getActions());
                actions.add(null); // Separator to set off this provider's actions.
            }
        }
        return actions;
    }
}
