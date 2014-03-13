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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
@OptionsPanelController.TopLevelRegistration(
    categoryName = "#OptionsCategory_Name_KeywordSearchOptions",
iconBase = "org/sleuthkit/autopsy/keywordsearch/options-icon.png",
position = 2,
keywords = "#OptionsCategory_Keywords_KeywordSearchOptions",
keywordsCategory = "KeywordSearchOptions")
// moved messages to Bundle.properties
//@org.openide.util.NbBundle.Messages({"OptionsCategory_Keywords_KeywordSearchOptions=Keyword Search", "OptionsCategory_Keywords_KeywordSearchOptions=Keyword Search"})
public final class KeywordSearchOptionsPanelController extends OptionsPanelController {

    private KeywordSearchConfigurationPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;
    private static final Logger logger = Logger.getLogger(KeywordSearchConfigurationPanel.class.getName());
    
    public void update() {
        getPanel().load();
        changed = false;
    }

    public void applyChanges() {
        getPanel().store();
        changed = false;
    }

    public void cancel() {
        getPanel().cancel();
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

    private KeywordSearchConfigurationPanel getPanel() {
        if (panel == null) {
            panel = new KeywordSearchConfigurationPanel();//this);
        }
        return panel;
    }

    void changed() {
        if (!changed) {
            changed = true;
            
            try {
                pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchOptionsPanelController listener threw exception", e);
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr.msg1"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
            try {
                pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchOptionsPanelController listener threw exception", e);
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr.msg2"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        
    }
}
