/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.hashdatabase;

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
    categoryName = "#OptionsCategory_Name_HashDatabase",
iconBase = "org/sleuthkit/autopsy/hashdatabase/options_icon.png",
position = 3,
keywords = "#OptionsCategory_Keywords_HashDatabase",
keywordsCategory = "HashDatabase",
id = "HashDatabase")
// moved messages to Bundle.properties
//@org.openide.util.NbBundle.Messages({"OptionsCategory_Name_HashDatabase=Hash Database", "OptionsCategory_Keywords_HashDatabase=Hash Database"})
public final class HashDatabaseOptionsPanelController extends OptionsPanelController {

    private HashDbConfigPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;
     private static final Logger logger = Logger.getLogger(HashDatabaseOptionsPanelController.class.getName());
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

    private HashDbConfigPanel getPanel() {
        if (panel == null) {
            panel = new HashDbConfigPanel();
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
                logger.log(Level.SEVERE, "HashDatabaseOptionsPanelController listener threw exception", e);
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "HashDatabaseOptionsPanelController.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "HashDatabaseOptionsPanelController.moduleErrMsg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
        
            try {
                pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "HashDatabaseOptionsPanelController listener threw exception", e);
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "HashDatabaseOptionsPanelController.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "HashDatabaseOptionsPanelController.moduleErrMsg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
    }
}
