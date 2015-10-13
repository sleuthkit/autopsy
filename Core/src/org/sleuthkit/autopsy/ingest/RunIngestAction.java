/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
import javax.swing.JMenuItem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;

@ActionID(
        category = "Tools",
        id = "org.sleuthkit.autopsy.ingest.RunIngestAction"
)
@ActionRegistration(
        displayName = "#CTL_RunIngestAction"
)
@Messages("CTL_RunIngestAction=Run Ingest")
public final class RunIngestAction extends CallableSystemAction implements Presenter.Menu, ActionListener {

    static public RunIngestAction getInstance() {
        return new RunIngestAction();
    }

    /**
     * Call getMenuPresenters to create images sublist
     */
    @Override
    public void performAction() {
        getMenuPresenter();
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     *
     * @return actionName
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(RunIngestAction.class, "RunIngestModulesMenu.getName.text");
    }

    /**
     * Gets the HelpCtx associated with implementing object
     *
     * @return HelpCtx or HelpCtx.DEFAULT_HELP
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Create a sublist of images updated by RunIngestSubMenu Each has an action
     * to perform Ingest Modules on it.
     *
     * @return the images sublist created.
     */
    @Override
    public JMenuItem getMenuPresenter() {
        JMenuItem sublist = new RunIngestSubMenu();
        sublist.setVisible(true);
        return sublist;
    }

    /**
     * This method does nothing, use performAction instead.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
    }
}
