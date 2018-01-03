/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.openide.nodes.Node;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Opens new ContentViewer pane in a detached window
 */
public class NewWindowViewAction extends AbstractAction {

    private static Logger logger = Logger.getLogger(NewWindowViewAction.class.getName());

    private Node contentNode;

    public NewWindowViewAction(String title, Node contentNode) {
        super(title);
        this.contentNode = contentNode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String name = "DataContent"; //NON-NLS
        String s = contentNode.getLookup().lookup(String.class);
        if (s != null) {
            name = s;
        } else {
            Content c = contentNode.getLookup().lookup(Content.class);
            if (c != null) {
                try {
                    name = c.getUniquePath();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + c); //NON-NLS
                }
            }
        }

        final DataContentTopComponent dctc = DataContentTopComponent.createUndocked(name, null);

        Mode m = WindowManager.getDefault().findMode("outputFloat"); //NON-NLS
        m.dockInto(dctc);
        dctc.open();

        // Queue setting the node on the EDT thread to be done later so the dctc
        // can completely initialize.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dctc.setNode(contentNode);
                dctc.toFront();
                dctc.requestActive();
            }
        });
    }

}
