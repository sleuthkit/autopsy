/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.url.analytics.domaincategorization;

import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * The options panel controller that registers and displays the option panel for
 * custom web categories.
 */
@OptionsPanelController.TopLevelRegistration(categoryName = "#WebCategoryOptionsController_title",
        iconBase = "org/sleuthkit/autopsy/images/domain-32.png",
        position = 21,
        keywords = "#WebCategoryOptionsController_keywords",
        keywordsCategory = "Custom Web Categories")
public class WebCategoriesOptionsController extends OptionsPanelController {

    private final WebCategoriesDataModel dataModel = WebCategoriesDataModel.getInstance();
    private final WebCategoriesOptionsPanel panel = new WebCategoriesOptionsPanel(dataModel);

    @Override
    public void update() {
        panel.refresh();
    }

    @Override
    public void applyChanges() {
        // NO OP since saves happen whenever there is a change.
    }

    @Override
    public void cancel() {
        // NO OP since saves happen whenever there is a change.
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return false;
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return panel;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        // NO OP since saves happen whenever there is a change.
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        // NO OP since saves happen whenever there is a change.
    }

}
