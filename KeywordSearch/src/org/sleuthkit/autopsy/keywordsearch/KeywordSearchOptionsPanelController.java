/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

@OptionsPanelController.TopLevelRegistration(
    categoryName = "#OptionsCategory_Name_KeywordSearchOptions",
iconBase = "org/sleuthkit/autopsy/keywordsearch/options-icon.png",
keywords = "#OptionsCategory_Keywords_KeywordSearchOptions",
keywordsCategory = "KeywordSearchOptions")
@org.openide.util.NbBundle.Messages({"OptionsCategory_Name_KeywordSearchOptions=Keyword Search", "OptionsCategory_Keywords_KeywordSearchOptions=Keyword Search"})
public final class KeywordSearchOptionsPanelController extends OptionsPanelController {

    private KeywordSearchOptionsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;

    public void update() {
        getPanel().load();
        changed = false;
    }

    public void applyChanges() {
        getPanel().store();
        changed = false;
    }

    public void cancel() {
        // need not do anything special, if no changes have been persisted yet
    }

    public boolean isValid() {
        return getPanel().valid();
    }

    public boolean isChanged() {
        return changed;
    }

    public HelpCtx getHelpCtx() {
        return null; // new HelpCtx("...ID") if you have a help set
    }

    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private KeywordSearchOptionsPanel getPanel() {
        if (panel == null) {
            panel = new KeywordSearchOptionsPanel();//this);
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
