/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.keywordsearch.multicase;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.core.UserPreferences;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.experimental.autoingest.MultiCaseKeywordSearchOpenAction")
@ActionReference(path = "Menu/Tools", position = 202)
@ActionRegistration(displayName = "#CTL_MultiCaseKeywordSearchOpenAction", lazy = false)
@Messages({"CTL_MultiCaseKeywordSearchOpenAction=Multi-case Keyword Search"})
/**
 * Action to open the top level component for the multi-case keyword search.
 */
public final class MultiCaseKeywordSearchOpenAction extends CallableSystemAction {

    private static final String DISPLAY_NAME = Bundle.CTL_MultiCaseKeywordSearchOpenAction();
    private static final long serialVersionUID = 1L;

    @Override
    public boolean isEnabled() {
        return UserPreferences.getIsMultiUserModeEnabled();
    }

    @Override
    public void performAction() {
        MultiCaseKeywordSearchTopComponent.openTopComponent();
    }

    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }

}
