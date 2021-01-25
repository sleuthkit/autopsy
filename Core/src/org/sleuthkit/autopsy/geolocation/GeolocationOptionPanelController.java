/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.geolocation;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionsCategory_Name_Geolocation",
        iconBase = "org/sleuthkit/autopsy/images/blueGeo32.png",
        keywords = "#OptionsCategory_Keywords_Geolocation",
        keywordsCategory = "Geolcoation",
        position = 18
)
/**
 * Controller for the Geolocation options pane in the Options dialog.
 */
public final class GeolocationOptionPanelController extends OptionsPanelController {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;
    private GeolocationSettingsPanel panel;

    /**
     * Returns the GeolcoationSettingPanel.
     *
     * @return
     */
    GeolocationSettingsPanel getPanel() {
        if (panel == null) {
            panel = new GeolocationSettingsPanel();
            panel.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(OptionsPanelController.PROP_CHANGED)) {
                        updateChanged();
                    }
                }

            });
        }
        return panel;
    }

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
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Helper function for updating the change state.
     */
    void updateChanged() {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
        pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }

    @Override
    public void cancel() {
        getPanel().cancelChanges();
    }

}
