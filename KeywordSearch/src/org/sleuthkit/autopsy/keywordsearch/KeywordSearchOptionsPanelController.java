/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2014 Basis Technology Corp.
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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

@OptionsPanelController.TopLevelRegistration(
    categoryName = "#OptionsCategory_Name_KeywordSearchOptions",
iconBase = "org/sleuthkit/autopsy/keywordsearch/options-icon.png",
position = 2,
keywords = "#OptionsCategory_Keywords_KeywordSearchOptions",
keywordsCategory = "KeywordSearchOptions")
public final class KeywordSearchOptionsPanelController extends OptionsPanelController {

    private KeywordSearchConfigurationPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;
    private static final Logger logger = Logger.getLogger(KeywordSearchConfigurationPanel.class.getName());
    
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

    private KeywordSearchConfigurationPanel getPanel() {
        if (panel == null) {
            panel = new KeywordSearchConfigurationPanel();
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
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchOptionsPanelController updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        }
            try {
                pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchOptionsPanelController listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchOptionsPanelController updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }   
    }
}
