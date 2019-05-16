/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.configurelogicalimager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Tools",
        id = "org.sleuthkit.autopsy.configurelogicalimager.ConfigureLogicalImager"
)
@ActionRegistration(
        displayName = "#CTL_ConfigureLogicalImager"
)
@ActionReference(path = "Menu/Tools", position = 2000, separatorAfter = 2050)
@Messages("CTL_ConfigureLogicalImager=Configure Logical Imager")
public final class ConfigureLogicalImager implements ActionListener {
    
    private ConfigureLogicalImagerDialog dialog;
    
    @Override
    public void actionPerformed(ActionEvent e) {
        JFrame frame = new javax.swing.JFrame();
        dialog = new ConfigureLogicalImagerDialog(frame, true);
        dialog.setLocationRelativeTo(frame);
        ImageIcon imageIcon = new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/icon.png"));
        dialog.setIconImage(imageIcon.getImage());
        dialog.setVisible(true);
    }
}
