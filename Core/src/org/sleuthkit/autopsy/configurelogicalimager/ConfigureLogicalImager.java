/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.configurelogicalimager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    @Override
    public void actionPerformed(ActionEvent e) {
        // TODO implement action body
    }
}
