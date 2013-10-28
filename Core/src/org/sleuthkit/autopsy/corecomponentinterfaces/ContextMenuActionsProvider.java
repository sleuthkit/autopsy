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
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.util.List;
import javax.swing.Action;

/**
 * Implementers of this interface provide Actions that will be added to context
 * menus in Autopsy.
 */
public interface ContextMenuActionsProvider {
    /**
     * Gets context menu Actions appropriate to the org.sleuthkit.datamodel 
     * objects in the NetBeans Lookup for the active TopComponent. 
     * Implementers can discover the data model objects by calling 
     * org.openide.util.Utilities.actionsGlobalContext().lookupAll().
     * @return A list, possibly empty, of Action objects.
     */
    List<Action> getActions();
}
