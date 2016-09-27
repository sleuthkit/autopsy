/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
