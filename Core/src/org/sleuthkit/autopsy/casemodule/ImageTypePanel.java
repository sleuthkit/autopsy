/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.beans.PropertyChangeListener;
import javax.swing.JPanel;

abstract class ImageTypePanel extends JPanel {
    
    /**
     * Returns a list off all the panels extending ImageTypePanel.
     * @return list of all ImageTypePanels
     */
    public static ImageTypePanel[] getPanels() {
        return new ImageTypePanel[] {ImageFilePanel.getDefault(), LocalDiskPanel.getDefault()};
    }
    
    /**
     * Return the path of the selected image in this panel.
     * @return path to selected image
     */
    abstract public String getImagePath();
    
    /**
     * Set the selected image in this panel to the provided path.
     * This function is optional.
     * @param s path to selected image
     */
    abstract public void setImagePath(String s);
    
    /**
     * Returns if the next button should be enabled in the current wizard.
     * @return true if the next button should be enabled, false otherwise
     */
    abstract public boolean enableNext();
    
    /**
     * Tells this panel it has been selected.
     */
    abstract public void select();
    
    @Override
    abstract public void addPropertyChangeListener(PropertyChangeListener pcl);
    @Override
    abstract public void removePropertyChangeListener(PropertyChangeListener pcl);
    
}
