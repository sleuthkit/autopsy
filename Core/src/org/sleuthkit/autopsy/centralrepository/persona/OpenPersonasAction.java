/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.persona;

import javax.swing.JMenuItem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 * An Action that opens the Persona Search window.
 */

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.centralrepository.persona.OpenPersonasAction")
@ActionRegistration(displayName = "#CTL_OpenPersonas", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 1825, separatorBefore = 1824)
})
public final class OpenPersonasAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;

    private final JMenuItem menuItem;

    public OpenPersonasAction() {
        menuItem = super.getMenuPresenter();
        this.setEnabled(CentralRepository.isEnabled());
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void performAction() {
        final TopComponent topComponent = WindowManager.getDefault().findTopComponent("PersonasTopComponent");
        if (topComponent != null) {
            if (topComponent.isOpened() == false) {
                topComponent.open();
            }
            topComponent.toFront();
            topComponent.requestActive();
        }
    }

    @Override
    @NbBundle.Messages("OpenPersonasAction.displayName=Personas")
    public String getName() {
        return Bundle.OpenPersonasAction_displayName();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }

    @Override
    public void setEnabled(boolean enable) {
        super.setEnabled(enable);
        menuItem.setEnabled(enable);
    }
    
    @Override
    public boolean isEnabled() {
        return CentralRepository.isEnabled();
    }

    @Override
    public JMenuItem getMenuPresenter() {
        return menuItem;
    }
}
