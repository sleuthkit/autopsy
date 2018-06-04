/*
 * //DLG: Change this!
 */
package org.sleuthkit.autopsy.centralrepository;

import java.awt.event.ActionEvent;
import java.nio.file.Paths;
import javax.swing.AbstractAction;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * //DLG: Change this!
 */
public final class AddEditCentralRepoCommentAction extends AbstractAction {

    private CorrelationAttribute correlationAttribute;
    
    private AddEditCentralRepoCommentAction(CorrelationAttribute correlationAttribute, String displayName) {
        super(displayName);
        this.correlationAttribute = correlationAttribute;
    }
    
    private AddEditCentralRepoCommentAction(AbstractFile file, String displayName) {
        
        super(displayName);
        try {
            CorrelationAttribute.Type type = EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
            CorrelationDataSource correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, file.getDataSource());
            String value = file.getMd5Hash();
            String filePath = Paths.get(file.getParentPath(), file.getName()).toString().replace('\\', '/').toLowerCase();
            
            correlationAttribute = EamDb.getInstance().getCorrelationAttribute(type, correlationCase, correlationDataSource, value, filePath);
        } catch (TskCoreException ex) {
            //DLG:
            Exceptions.printStackTrace(ex);
        } catch (EamDbException ex) {
            //DLG:
            Exceptions.printStackTrace(ex);
        } catch (NoCurrentCaseException ex) {
            //DLG:
            Exceptions.printStackTrace(ex);
        }
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        addEditCentralRepoComment();
    }
    
    //DLG:
    public void addEditCentralRepoComment() {
        CentralRepoCommentDialog centralRepoCommentDialog = new CentralRepoCommentDialog(correlationAttribute);
        centralRepoCommentDialog.display();
    }
    
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditCentralRepoComment=Add/Edit Central Repository Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCentralRepoCommentAction(AbstractFile file) {
        return new AddEditCentralRepoCommentAction(file,
                Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditCentralRepoComment());
    }
    
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditComment=Add/Edit Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCommentAction(CorrelationAttribute correlationAttribute) {
        return new AddEditCentralRepoCommentAction(correlationAttribute,
                Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditComment());
    }
}
