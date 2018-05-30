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

    private String value;
    private String filePath;
    private CorrelationAttribute.Type type;
    private CorrelationCase correlationCase;
    private CorrelationDataSource correlationDataSource;
    
    private AddEditCentralRepoCommentAction(CorrelationAttribute.Type type, CorrelationCase correlationCase, AbstractFile file,
            String displayName) {
        
        super(displayName);
        try {
            this.type = type;
            this.correlationCase = correlationCase;
            this.correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, file.getDataSource());
            this.value = file.getMd5Hash();
            this.filePath = Paths.get(file.getParentPath(), file.getName()).toString().replace('\\', '/').toLowerCase();
        } catch (TskCoreException ex) {
            //DLG:
            Exceptions.printStackTrace(ex);
        } catch (EamDbException ex) {
            //DLG:
            Exceptions.printStackTrace(ex);
        }
    }
    
    private AddEditCentralRepoCommentAction(CorrelationAttribute.Type type, CorrelationCase correlationCase,
            CorrelationDataSource correlationDataSource, String value, String filePath, String displayName) {
        
        super(displayName);
        this.type = type;
        this.correlationCase = correlationCase;
        this.correlationDataSource = correlationDataSource;
        this.value = value;
        this.filePath = filePath;
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        addEditCentralRepoComment();
    }
    
    //DLG:
    public void addEditCentralRepoComment() {
        try {
            final CorrelationAttribute correlationAttribute = EamDb.getInstance().getCorrelationAttribute(type, correlationCase, correlationDataSource, value, filePath);
            CentralRepoCommentDialog centralRepoCommentDialog = new CentralRepoCommentDialog(correlationAttribute);
            centralRepoCommentDialog.display();
        } catch (EamDbException ex) {
            //DLG:
            Exceptions.printStackTrace(ex);
        }
    }
    
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditCentralRepoComment=Add/Edit Central Repository Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCentralRepoCommentAction(
            CorrelationAttribute.Type type, CorrelationCase correlationCase, AbstractFile file) {
        return new AddEditCentralRepoCommentAction(type, correlationCase, file,
                Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditCentralRepoComment());
    }
    
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditComment=Add/Edit Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCommentAction(
            CorrelationAttribute.Type type, CorrelationCase correlationCase, CorrelationDataSource correlationDataSource, String value, String filePath) {
        return new AddEditCentralRepoCommentAction(type, correlationCase, correlationDataSource, value, filePath,
                Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditComment());
    }
}
