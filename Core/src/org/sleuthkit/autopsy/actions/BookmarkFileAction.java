/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

public class BookmarkFileAction extends AbstractAction {
     
    private static final String NO_COMMENT = "";
    private static final String BOOKMARK = "Bookmark";

    @Override
    public void actionPerformed(ActionEvent e) {
        TagName tagName = null;
        try {
            tagName = Case.getCurrentCase().getServices().getTagsManager().addTagName(BOOKMARK);
        } catch (TagsManager.TagNameAlreadyExistsException ex) {
            try {
                tagName = Case.getCurrentCase().getServices().getTagsManager().getTagName(BOOKMARK);
            } catch (TagsManager.TagNameDoesNotExistException ex1) {
                // already confirmed that tag name does exist
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(BookmarkFileAction.class.getName()).log(Level.SEVERE, "Error tagging file", ex); //NON-NLS
        } finally {
            AddContentTagAction.getInstance().addTag(tagName, NO_COMMENT);
        }
    }
    
}
