/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this content except in compliance with the License.
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

import org.sleuthkit.autopsy.directorytree.actionhelpers.ExtractActionHelper;
import java.awt.event.ActionEvent;
import java.util.Collection;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Extracts AbstractFiles to a location selected by the user.
 */
public final class ExtractAction extends AbstractAction {

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ExtractAction instance;

    public static synchronized ExtractAction getInstance() {
        if (null == instance) {
            instance = new ExtractAction();
        }
        return instance;
    }

    /**
     * Private constructor for the action.
     */
    private ExtractAction() {
        super(NbBundle.getMessage(ExtractAction.class, "ExtractAction.title.extractFiles.text"));
    }

    /**
     * Asks user to choose destination, then extracts content to destination
     * (recursing on directories).
     *
     * @param e The action event.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Lookup lookup = Utilities.actionsGlobalContext();
        Collection<? extends AbstractFile> selectedFiles =lookup.lookupAll(AbstractFile.class);
        ExtractActionHelper extractor = new ExtractActionHelper();
        extractor.extract(e, selectedFiles);

    }
}
