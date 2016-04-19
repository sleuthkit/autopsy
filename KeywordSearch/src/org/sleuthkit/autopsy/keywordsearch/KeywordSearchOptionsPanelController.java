/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Level;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionsCategory_Name_KeywordSearchOptions",
        iconBase = "org/sleuthkit/autopsy/keywordsearch/options-icon.png",
        position = 3,
        keywords = "#OptionsCategory_Keywords_KeywordSearchOptions",
        keywordsCategory = "KeywordSearchOptions")
public final class KeywordSearchOptionsPanelController extends OptionsPanelController {

    private KeywordSearchGlobalSettingsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;
    private static final Logger logger = Logger.getLogger(KeywordSearchGlobalSettingsPanel.class.getName());

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
        getPanel().store();
        changed = false;
    }

    /**
     * This method is called when the Cancel button is pressed. It applies to any
     * of the panels that have been opened in the process of using the options
     * pane.
     */
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
        return null;
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

    private KeywordSearchGlobalSettingsPanel getPanel() {
        if (panel == null) {
            panel = new KeywordSearchGlobalSettingsPanel();
            panel.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(OptionsPanelController.PROP_CHANGED)) {
                        changed = true;
                    }
                }
            });
        }
        return panel;
    }

    void changed() {
        if (!changed) {
            changed = true;

            try {
                pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchOptionsPanelController listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr.msg1"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
        try {
            pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "KeywordSearchOptionsPanelController listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(
                    NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchOptionsPanelController.moduleErr.msg2"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }
}
