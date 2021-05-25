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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.Action;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataArtifact;

@ServiceProvider(service = ContextMenuActionsProvider.class)
public class HashDbContextMenuActionsProvider implements ContextMenuActionsProvider {

    @Override
    public List<Action> getActions() {
        ArrayList<Action> actions = new ArrayList<>();
        Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
        Collection<? extends DataArtifact> dataArtifacts = Utilities.actionsGlobalContext().lookupAll(DataArtifact.class);
        // don't show AddContentToHashDbAction for data artifacts but do if related abstract file
        if (!selectedFiles.isEmpty() && dataArtifacts.isEmpty()) {
            actions.add(AddContentToHashDbAction.getInstance());
        }
        return actions;
    }
}
