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
import javax.swing.JSeparator;
import org.openide.awt.DynamicMenuContent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class is used to populate the list of open images to run ingest on them
 */
class UpdateIngestImages extends JMenuItem implements DynamicMenuContent {

     @Override
    public JComponent[] getMenuPresenters() {
        List<Image> images = new ArrayList<>();
        JComponent[] comps = new JComponent[1];
        SleuthkitCase sk = null;
        try {
            sk = Case.getCurrentCase().getSleuthkitCase();
        } catch (IllegalStateException ex) {
            // create a disabled empty menu
            comps = new JComponent[1];
            JMenuItem emptyMenu = new JMenuItem("unempty");
            comps[0] = emptyMenu;
            comps[0].setEnabled(false);
            return comps;
        }
        try {     
            images = sk.getImages();
        } catch (TskCoreException e) {
            System.out.println("Exception getting images: " + e.getMessage());
	}
        comps = new JComponent[images.size()]; // + 2 for separator and clear menu

        // if it has the recent menus, add them to the component list
        for (int i = 0; i < images.size(); i++) {
            String action = images.get(i).getName();
            JMenuItem menuItem = new JMenuItem(action);
            menuItem.setActionCommand(action.toUpperCase());
            comps[i] = menuItem;
        }

//        if (true) {
//            comps[images.size()] = new JSeparator();
//            JMenuItem clearMenu = new JMenuItem("unclear");
//            comps[images.size() + 1] = clearMenu;
//        }
        return comps;
    }

    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }


}
