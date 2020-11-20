/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.yara.ui;

import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * Yara rule set option panel controller.
 *
 */
@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionCategory_Name_YaraRuleSetOption",
        iconBase = "org/sleuthkit/autopsy/images/yara_32.png",
        keywords = "#OptionCategory_Keywords_YaraRuleSetOption",
        keywordsCategory = "YaraRuleSets",
        position = 20
)
public class YaraRuleSetsOptionPanelController extends OptionsPanelController {

    private YaraRuleSetOptionPanel panel = null;

    @Override
    public void update() {
        if (panel != null) {
            panel.updatePanel();
        }
    }

    @Override
    public void applyChanges() {

    }

    @Override
    public void cancel() {

    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        panel = new YaraRuleSetOptionPanel();
        return panel;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener pl) {

    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener pl) {

    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return false;
    }

}
