/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.url.analytics;

import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 *
 * @author gregd
 */
@OptionsPanelController.TopLevelRegistration(categoryName = "#WebCategoryOptionsController_title",
        iconBase = "org/sleuthkit/autopsy/corecomponents/checkbox32.png",
        position = 21,
        keywords = "#WebCategoryOptionsController_keywords",
        keywordsCategory = "Custom Web Categories")
public class WebCategoriesOptionsController extends OptionsPanelController {
    private final WebCategoriesDataModel dataModel = new WebCategoriesDataModel();
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
