/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.awt.event.ActionEvent;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;

/**
 * Encapsulates a menu action which triggers the common files search dialog.
 */
final class CommonFilesSearchAction extends CallableSystemAction {

    private static CommonFilesSearchAction instance = null;
    private static final long serialVersionUID = 1L;

    CommonFilesSearchAction() {
        super();
        this.setEnabled(true);

    }

    public static synchronized CommonFilesSearchAction getDefault() {
        if (instance == null) {
            instance = new CommonFilesSearchAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        new CommonFilesDialog().setVisible(true);
    }

    @Override
    public void performAction() {
        new CommonFilesDialog().setVisible(true);
    }

    @NbBundle.Messages({
        "CommonFilesAction.getName.text=Common Files Search"})
    @Override
    public String getName() {
        return Bundle.CommonFilesAction_getName_text();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
