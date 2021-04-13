/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.configuration;

import java.awt.Component;
import java.awt.Dialog;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;

/**
 * Configuration Logical Imager
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.logicalimager.configuration.CreateLogicalImagerAction")
@ActionRegistration(displayName = "#CTL_CreateLogicalImagerAction", lazy = false)
@ActionReference(path = "Menu/Tools", position = 2000, separatorBefore = 1999)
@Messages("CTL_CreateLogicalImagerAction=Create Logical Imager")
public final class CreateLogicalImagerAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static final String LOGICAL_IMAGER_DIR = "tsk_logical_imager"; // NON-NLS
    private static final String LOGICAL_IMAGER_EXE = "tsk_logical_imager.exe"; // NON-NLS
    private static final Path LOGICAL_IMAGER_EXE_PATH = Paths.get(LOGICAL_IMAGER_DIR, LOGICAL_IMAGER_EXE);

    /**
     * Finds the installed logical imager executable.
     *
     * @return A File object for the executable or null if it is not found.
     */
    static File getLogicalImagerExe() {
        return InstalledFileLocator.getDefault().locate(LOGICAL_IMAGER_EXE_PATH.toString(), CreateLogicalImagerAction.class.getPackage().getName(), false);
    }

    @NbBundle.Messages({
        "CreateLogicalImagerAction.title=Create Logical Imager"
    })
    @Override
    public void performAction() {
        List<WizardDescriptor.Panel<WizardDescriptor>> panels = new ArrayList<>();
        panels.add(new ConfigWizardPanel1());
        panels.add(new ConfigWizardPanel2());
        panels.add(new ConfigWizardPanel3());
        String[] steps = new String[panels.size()];
        for (int i = 0; i < panels.size(); i++) {
            Component c = panels.get(i).getComponent();
            // Default step name to component name of panel.
            steps[i] = c.getName();
            if (c instanceof JComponent) { // assume Swing components
                JComponent jc = (JComponent) c;
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
                jc.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
            }
        }
        WizardDescriptor wiz = new WizardDescriptor(new WizardDescriptor.ArrayIterator<>(panels));
        // {0} will be replaced by WizardDesriptor.Panel.getComponent().getName()
        wiz.setTitleFormat(new MessageFormat("{0}")); // NON-NLS
        wiz.setTitle(Bundle.CreateLogicalImagerAction_title());
        Dialog dialog = DialogDisplayer.getDefault().createDialog(wiz);
        dialog.setVisible(true);
    }

    @Override
    public String getName() {
        return Bundle.CTL_CreateLogicalImagerAction();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isEnabled() {
        File logicalImagerExe = getLogicalImagerExe();
        return logicalImagerExe != null && logicalImagerExe.exists();
    }

}
