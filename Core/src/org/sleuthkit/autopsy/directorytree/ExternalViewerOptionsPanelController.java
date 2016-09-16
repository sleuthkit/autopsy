/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
 */
package org.sleuthkit.autopsy.directorytree;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * Controller for the ExternalViewerGlobalSettingsPanel
 */
@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionsCategory_Name_ExternalViewer",
        iconBase = "org/sleuthkit/autopsy/modules/filetypeid/user-defined-file-types-settings.png",
        keywords = "#OptionsCategory_Keywords_ExternalViewer",
        keywordsCategory = "ExternalViewer",
        position = 9
)
public final class ExternalViewerOptionsPanelController extends OptionsPanelController {

    private ExternalViewerGlobalSettingsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;

    @Override
    public void update() {
        getPanel().load();
        changed = false;
    }

    @Override
    public void applyChanges() {
        if (changed) {
            getPanel().store();
            changed = false;
        }
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public JComponent getComponent(Lookup lkp) {
        return getPanel();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private ExternalViewerGlobalSettingsPanel getPanel() {
        if (panel == null) {
            panel = new ExternalViewerGlobalSettingsPanel();
            panel.addPropertyChangeListener((PropertyChangeEvent evt) -> {
                if (evt.getPropertyName().equals(OptionsPanelController.PROP_CHANGED)) {
                    changed();
                }
            });
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
