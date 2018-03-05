/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 * The action associated with assorted Run Ingest Modules menu items.
 * 
 * This action should only be invoked in the event dispatch thread (EDT).
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.ingest.RunIngestAction")
@ActionRegistration(displayName = "#CTL_RunIngestAction", lazy = false)
@Messages("CTL_RunIngestAction=Run Ingest")
public final class RunIngestAction extends CallableSystemAction implements Presenter.Menu, ActionListener {

    private static final long serialVersionUID = 1L;
    private static RunIngestAction action;

    static public RunIngestAction getInstance() {
        if (action == null) {
            action = new RunIngestAction();
        }
        return action;
    }

    private RunIngestAction() {
    }
    
    @Override
    public void performAction() {
        getMenuPresenter();
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(RunIngestAction.class, "RunIngestModulesMenu.getName.text");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public JMenuItem getMenuPresenter() {
        JMenuItem sublist = new RunIngestSubMenu();
        sublist.setVisible(true);
        return sublist;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        performAction();
    }
    
    @Override
    public boolean isEnabled() {
        try {
            Case openCase = Case.getOpenCase();
            return openCase.hasData();
        } catch (NoCurrentCaseException ex) {
            return false;
        }
    }
}
