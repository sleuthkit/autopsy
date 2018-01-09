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

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * This action opens the TopComponent passed to the constructor
 */
class OpenTopComponentAction extends AbstractAction {

    private TopComponent tc;

    OpenTopComponentAction(TopComponent top) {
        this.tc = top;
    }

    OpenTopComponentAction(String tcId) {
        this.tc = WindowManager.getDefault().findTopComponent(tcId);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (tc == null) {
            return;
        }

        if (!this.tc.isOpened()) {
            this.tc.open();
        }
        this.tc.requestActive();
    }

}
