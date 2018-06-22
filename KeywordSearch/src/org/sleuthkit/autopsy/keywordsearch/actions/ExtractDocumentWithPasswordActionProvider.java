/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.keywordsearch.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action provider for the ExtractDocumentWithPasswordAction
 */
@ServiceProvider(service = ContextMenuActionsProvider.class)
public final class ExtractDocumentWithPasswordActionProvider implements ContextMenuActionsProvider {

    //supported document extensions 
    private static final List<String> DOCUMENT_EXTENSIONS = Arrays.asList("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf");
    private static final Logger logger = Logger.getLogger(ExtractDocumentWithPasswordActionProvider.class.getName());

    @Override
    public List<Action> getActions() {
        List<Action> actions = new ArrayList<>();
        final Collection<? extends AbstractFile> selectedContents = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
        if (selectedContents.size() == 1) {
            //when there is an AbstractFile selected get the name of the first one to translate
            AbstractFile content = selectedContents.toArray(new AbstractFile[selectedContents.size()])[0];
            try {
                if (content.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0
                        && DOCUMENT_EXTENSIONS.contains(content.getNameExtension().toLowerCase())) {
                    actions.add(new ExtractDocumentWithPasswordAction(content));
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to to add Extract Content with Password action to context menus", ex);
            }
        }
        return actions;
    }

}
