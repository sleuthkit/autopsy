/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.services;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionsCategory_Name_TagsManager",
        iconBase = "org/sleuthkit/autopsy/casemodule/services/tags-manager.png",
        keywords = "#OptionsCategory_Keywords_TagsManager",
        keywordsCategory = "TagsManager",
        position = 8
)
public final class TagsManagerOptionsPanelController extends OptionsPanelController {

    private TagsManagerOptionsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;
    
    /**
     * Component should load its data here.
     */
    @Override
    public void update() {
        getPanel().load();
        changed = false;
    }

    /**
     * This method is called when both the Ok and Apply buttons are pressed. It
     * applies to any of the panels that have been opened in the process of
     * using the options pane.
     */
    @Override
    public void applyChanges() {
        if (changed) {
            getPanel().store();
            changed = false;
        }
    }

    /**
     * This method is called when the Cancel button is pressed. It applies to
     * any of the panels that have been opened in the process of using the
     * options pane.
     */
    @Override
    public void cancel() {
        if (changed) {
            getPanel().cancelChanges();
            changed = false;
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }

    /**
     * Used to determine whether any changes have been made to this controller's
     * panel.
     *
     * @return Whether or not a change has been made.
     */
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
    
    private TagsManagerOptionsPanel getPanel() {
        if (panel == null) {
            panel = new TagsManagerOptionsPanel();
            panel.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(OptionsPanelController.PROP_CHANGED)) {
                        changed();
                    }
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
