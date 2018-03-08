/*
* Autopsy Forensic Browser
*
* Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

public class AddBookmarkTagAction extends AbstractAction {

    public static final KeyStroke BOOKMARK_SHORTCUT = KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_MASK);
    private static final String NO_COMMENT = "";
    private static final String BOOKMARK = NbBundle.getMessage(AddBookmarkTagAction.class, "AddBookmarkTagAction.bookmark.text");

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Map<String, TagName> tagNamesMap = Case.getOpenCase().getServices().getTagsManager().getDisplayNamesToTagNamesMap();
            TagName bookmarkTagName = tagNamesMap.get(BOOKMARK);

            /*
             * Both AddContentTagAction.addTag and
             * AddBlackboardArtifactTagAction.addTag do their own lookup
             * If the selection is a BlackboardArtifact wrapped around an
             * AbstractFile, tag the artifact by default.
             */
            final Collection<BlackboardArtifact> artifacts = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (!artifacts.isEmpty()) {
                AddBlackboardArtifactTagAction.getInstance().addTag(bookmarkTagName, NO_COMMENT);
            } else {
                AddContentTagAction.getInstance().addTag(bookmarkTagName, NO_COMMENT);
            }

        } catch (TskCoreException | NoCurrentCaseException ex) {
            Logger.getLogger(AddBookmarkTagAction.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
        }
    }
}
