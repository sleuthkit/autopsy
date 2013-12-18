/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.fileextmismatch;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

@OptionsPanelController.TopLevelRegistration(
    categoryName = "#OptionsCategory_Name_FileExtMismatchOptions",
iconBase = "org/sleuthkit/autopsy/fileextmismatch/options-icon.png",
position = 4,
keywords = "#OptionsCategory_FileExtMismatch",
keywordsCategory = "KeywordSearchOptions")
@org.openide.util.NbBundle.Messages({"OptionsCategory_Name_FileExtMismatchOptions=File Ext Mismatch", "OptionsCategory_FileExtMismatch=File Ext Mismatch"})
public final class FileExtMismatchOptionsPanelController extends OptionsPanelController {

    private FileExtMismatchConfigPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;

    @Override
    public void update() {
        getPanel().load();
        changed = false;
    }

    @Override
    public void applyChanges() {
        getPanel().store();
        changed = false;
    }

    @Override
    public void cancel() {
        getPanel().cancel();
    }

    @Override
    public boolean isValid() {
        return getPanel().valid();
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null; // new HelpCtx("...ID") if you have a help set
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private FileExtMismatchConfigPanel getPanel() {
        if (panel == null) {
            panel = new FileExtMismatchConfigPanel();
        }
        return panel;
    }

    void changed() {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
        pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }
}
