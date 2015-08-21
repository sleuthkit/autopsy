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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;

/**
 * This class is used to add the action to the run ingest modules menu item. 
 * When the image is pressed, it should open the wizard for ingest modules.
 */
class MenuImageAction implements ActionListener {

    Image image;

    /**
     * the constructor
     */
    public MenuImageAction(Image image) {
        this.image = image;
    }

    /**
     * Runs the ingest modules wizard on the image.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        final RunIngestModulesDialog ingestDialog = new RunIngestModulesDialog(Collections.<Content>singletonList(image));
        ingestDialog.display();
    }
}
