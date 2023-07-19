/*
 * Central Repository
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.Action;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * This creates a single context menu item for adding or editing a Central
 * Repository comment.
 */
@ServiceProvider(service = ContextMenuActionsProvider.class)
public class CentralRepoContextMenuActionsProvider implements ContextMenuActionsProvider {

    @Override
    public List<Action> getActions() {
        ArrayList<Action> actionsList = new ArrayList<>();
        Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);

        if (selectedFiles.size() != 1) {
            return actionsList;
        }

        for (AbstractFile file : selectedFiles) {
            if (CentralRepository.isEnabled() && CorrelationAttributeUtil.isSupportedAbstractFileType(file) && file.isFile()) {
                AddEditCentralRepoCommentAction action = new AddEditCentralRepoCommentAction(file);
                if (action.getCorrelationAttribute() == null) {
                    action.setEnabled(false);
                }
                actionsList.add(action);
            }
        }

        return actionsList;
    }
}
