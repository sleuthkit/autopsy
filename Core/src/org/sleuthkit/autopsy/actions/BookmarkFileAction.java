/*
* Autopsy Forensic Browser
*
* Copyright 2011-2016 Basis Technology Corp.
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
import java.util.Map;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

public class BookmarkFileAction extends AbstractAction {

    private static final String NO_COMMENT = "";
    private static final String BOOKMARK = NbBundle.getMessage(BookmarkFileAction.class, "BookmarkFileAction.bookmark.text");

    @Override
    public void actionPerformed(ActionEvent e) {
        Map<String, TagName> tagNamesMap = null;
        try {
            tagNamesMap = Case.getCurrentCase().getServices().getTagsManager().getDisplayNamesToTagNamesMap();
            TagName bookmarkTagName = tagNamesMap.get(BOOKMARK);
            if (bookmarkTagName == null) {
                bookmarkTagName = Case.getCurrentCase().getServices().getTagsManager().addTagName(BOOKMARK);
            }
            AddContentTagAction.getInstance().addTag(bookmarkTagName, NO_COMMENT);
        } catch (TskCoreException ex) {
            Logger.getLogger(BookmarkFileAction.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
        } catch (TagsManager.TagNameAlreadyExistsException ex) {
            Logger.getLogger(BookmarkFileAction.class.getName()).log(Level.SEVERE, BOOKMARK + " already exists in database.", ex); //NON-NLS
        }

    }
}
