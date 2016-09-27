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
import java.util.List;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.TagName;

class BookmarkFileAction extends AbstractAction {

    private static final String NO_COMMENT = "";
    private static final String BOOKMARK = NbBundle.getMessage(BookmarkFileAction.class, "BookmarkFileAction.bookmark.text");

    @Override
    public void actionPerformed(ActionEvent e) {
        List<TagName> tagNames = Case.getCurrentCase().getServices().getTagsManager().getPredefinedTagNames();
        for (TagName tagName : tagNames) {
            if (tagName.getDisplayName().equals(BOOKMARK)) {
                AddContentTagAction.getInstance().addTag(tagName, NO_COMMENT);
                return;
            }
        }
    }

}
