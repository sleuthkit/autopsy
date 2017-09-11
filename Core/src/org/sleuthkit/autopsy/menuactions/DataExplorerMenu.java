/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.menuactions;

import javax.swing.JMenuItem;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;

/**
 * Menu item that tracks available DataExplorers.
 *
 * @author jantonius
 */
class DataExplorerMenu extends CallableSystemAction implements Presenter.Menu {

    @Override
    public JMenuItem getMenuPresenter() {
        return new DataExplorerDynamicMenu();
    }

    @Override
    public void performAction() {

    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "DataExplorerMenu.getName.text");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
