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
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import javax.swing.JPanel;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;

/**
 * This class is used to add the action to the recent case menu item. When the
 * the recent case menu is pressed, it should open that selected case.
 */
class MenuImageAction implements ActionListener {

    Image image;
    private JPanel caller; // for error handling

    /**
     * the constructor
     */
    public MenuImageAction(Image image) {
        this.image = image;
    }

    /**
     * Opens the recent case.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        final RunIngestModulesDialog ingestDialog = new RunIngestModulesDialog(Collections.<Content>singletonList(image));
        ingestDialog.display();
    }
}
