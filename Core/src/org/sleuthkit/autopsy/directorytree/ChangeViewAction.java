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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;
import org.sleuthkit.autopsy.corecomponents.DataContentViewerHex;
import org.sleuthkit.autopsy.corecomponents.DataContentViewerString;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The actions to change between the "Hex View" and "String View".
 *
 * @author jantonius
 */
 class ChangeViewAction extends AbstractAction implements Presenter.Popup {

    private int type; // type 1 = hex view, 2 = string view
    private Node node;

    /** the constructor */
    public ChangeViewAction(String title, int viewType, Node node) {
        super(title);
        this.type = viewType;
        this.node = node;
    }

    /**
     * The action that this class performs. The action is divided into 2 type.
     * First if the the type is 1, it will change the active output top component
     * to "Hex View". Another one is if the type is 2, it will change the active
     * top component to "String View."
     *
     * @param e  the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Logger.noteAction(this.getClass());

        DataContentTopComponent dctc = DataContentTopComponent.findInstance();
        int totalTabs = dctc.getTabPanels().getTabCount();

        // change the output view to hex view
        if (type == 1) {
            // find the hex view top component
//            TopComponent hexWin = WindowManager.getDefault().findTopComponent("HexViewTopComponent"); // Note: HexViewTopComponent = the preffered ID of that top component
//            hexWin.requestActive(); // set it to become the active top component

            for (int i = 0; i < totalTabs; i++) {
                if (dctc.getTabPanels().getComponentAt(i) instanceof DataContentViewerHex) {
                    dctc.getTabPanels().setSelectedIndex(i);
                }
            }
        }
        // change the output view to string view
        if (type == 2) {
            // find the string view top component
//            TopComponent stringWin = WindowManager.getDefault().findTopComponent("StringViewTopComponent"); // Note: StringViewTopComponent = the preffered ID of that top component
//            stringWin.requestActive(); // set it to become the active top component

            for (int i = 0; i < totalTabs; i++) {
                if (dctc.getTabPanels().getComponentAt(i) instanceof DataContentViewerString) {
                    dctc.getTabPanels().setSelectedIndex(i);
                }
            }
        }
        // else do nothing
    }

    /**
     * To create the sub-menu for "Hex View" and "String View".
     *
     * @return menuItem  the menu items
     */
    @Override
    public JMenuItem getPopupPresenter() {
        JMenu item = new JMenu(NbBundle.getMessage(this.getClass(), "ChangeViewAction.menuItem.view"));
        item.add(new ChangeViewAction(NbBundle.getMessage(this.getClass(), "ChangeViewAction.menuItem.view.hex"), 1, node));
        item.add(new ChangeViewAction(NbBundle.getMessage(this.getClass(), "ChangeViewAction.menuItem.view.string"), 2, node));
        return item;
    }
}
