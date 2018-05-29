/*
 * //DLG: Change this!
 */
package org.sleuthkit.autopsy.centralrepository;

import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.AbstractAction;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * //DLG: Change this!
 */
public final class AddEditCentralRepoCommentAction extends AbstractAction {

    private final AbstractFile file;
    
    private AddEditCentralRepoCommentAction(AbstractFile file, String displayName) {
        super(displayName);
        this.file = file;
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        final CorrelationAttribute correlationAttribute = EamArtifactUtil.makeCorrelationAttributeFromContent(file);
        try {
            String comment = EamDb.getInstance().getAttributeInstanceComment(
                    correlationAttribute.getCorrelationType(), correlationAttribute.getCorrelationValue());
            correlationAttribute.getInstances().get(0).setComment(comment);
            CentralRepoCommentDialog centralRepoCommentDialog = new CentralRepoCommentDialog(correlationAttribute);
            centralRepoCommentDialog.display();
        } catch (EamDbException ex) {
            //DLG:
            Exceptions.printStackTrace(ex);
        }
    }
    
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditCentralRepoComment=Add/Edit Central Repository Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCentralRepoCommentAction(AbstractFile file) {
        return new AddEditCentralRepoCommentAction(file, Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditCentralRepoComment());
    }
    
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditComment=Add/Edit Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCommentAction(AbstractFile file) {
        return new AddEditCentralRepoCommentAction(file, Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditComment());
    }
}
