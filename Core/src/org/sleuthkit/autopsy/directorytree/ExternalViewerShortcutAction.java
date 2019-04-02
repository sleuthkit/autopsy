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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.HashSet;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Extracts a File object to a temporary file in the case directory, and then
 * tries to open it in the user's system with the default or user specified
 * associated application.
 */
@Messages({"ExternalViewerShortcutAction.title.text=Open in External Viewer  Ctrl+E"})
public class ExternalViewerShortcutAction extends AbstractAction {

    public static final KeyStroke EXTERNAL_VIEWER_SHORTCUT = KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_MASK);

    private ExternalViewerShortcutAction() {
        super(Bundle.ExternalViewerShortcutAction_title_text());
    }

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ExternalViewerShortcutAction instance;

    public static synchronized ExternalViewerShortcutAction getInstance() {
        if (null == instance) {
            instance = new ExternalViewerShortcutAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Collection<AbstractFile> selectedFiles = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (!selectedFiles.isEmpty()) {
            for (AbstractFile file : selectedFiles) {
                ExternalViewerAction action = new ExternalViewerAction(Bundle.ExternalViewerShortcutAction_title_text(), file, false);
                action.actionPerformed(e);
            }
        }
    }
}
