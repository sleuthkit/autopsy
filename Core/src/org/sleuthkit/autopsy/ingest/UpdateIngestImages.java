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
package org.sleuthkit.autopsy.ingest;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class is used to populate the list of open images to run ingest on them
 */
class UpdateIngestImages extends JMenuItem implements DynamicMenuContent {
    
    static boolean hasImages = false;
    
    /**
     * Creates main menu/popup menu items. It's called each time a popup menu 
     * is constructed and just once for the main menu.
     * Main menu updates happen through the synchMenuPresenters() method.
     *
     * @return
     */
    @Override
    public JComponent[] getMenuPresenters() {
        List<Image> images = new ArrayList<>();
        JComponent[] comps = new JComponent[1];
        
        try {
            SleuthkitCase sk = Case.getCurrentCase().getSleuthkitCase();    
            images = sk.getImages();
        } catch (IllegalStateException ex) {
            // No open Cases, create a disabled empty menu
            comps = new JComponent[1];
            JMenuItem emptyMenu = new JMenuItem(NbBundle.getMessage(UpdateIngestImages.class, "UpdateIngestImages.menuItem.empty"));
            comps[0] = emptyMenu;
            comps[0].setEnabled(false);
            return comps;
        } catch (TskCoreException e) {
            System.out.println("Exception getting images: " + e.getMessage());
	}
        comps = new JComponent[images.size()];

        // Add Images to the component list
        for (int i = 0; i < images.size(); i++) {
            String action = images.get(i).getName();
            JMenuItem menuItem = new JMenuItem(action);
            menuItem.setActionCommand(action.toUpperCase());
            menuItem.addActionListener(new MenuImageAction(images.get(i)));
            comps[i] = menuItem;
            hasImages = true;
        }
        // If no images are open, create a disabled empty menu
        if (!hasImages) {
            comps = new JComponent[1];
            JMenuItem emptyMenu = new JMenuItem(NbBundle.getMessage(UpdateIngestImages.class, "UpdateIngestImages.menuItem.empty"));
            comps[0] = emptyMenu;
            comps[0].setEnabled(false);    
        }
        return comps;
    }

    /**
     * Updates main menu presenters. This method is called only by the main menu
     * processing.
     *
     * @param jcs the previously used menu items returned by previous call to
     *            getMenuPresenters() or synchMenuPresenters()
     *
     * @return menu a new set of items to show in menu. Can be either an updated
     *         old set of instances or a completely new one.
     */
    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }


}
