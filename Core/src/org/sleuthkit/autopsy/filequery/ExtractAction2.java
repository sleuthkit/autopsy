/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import org.sleuthkit.autopsy.directorytree.actionhelpers.ExtractActionHelper;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.HashSet;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Extracts AbstractFiles to a location selected by the user.
 */
final class ExtractAction2 extends AbstractAction {

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ExtractAction2 instance;
    private final Collection<AbstractFile> selectedFiles = new HashSet<>();

    static synchronized ExtractAction2 getInstance(Collection<AbstractFile> files) {
        if (null == instance) {
            instance = new ExtractAction2();
        }
        instance.setSelectedFiles(files);
        return instance;
    }

    /**
     * Private constructor for the action.
     */
    @NbBundle.Messages({"ExtractAction2.title.extractFiles.text=Extract File"})
    private ExtractAction2() {
        super(NbBundle.getMessage(ExtractAction2.class, "ExtractAction2.title.extractFiles.text"));
    }

    /**
     * Asks user to choose destination, then extracts content to destination
     * (recursing on directories).
     *
     * @param e The action event.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (!selectedFiles.isEmpty()) {
            ExtractActionHelper extractor = new ExtractActionHelper();
            extractor.extract(e, selectedFiles);
        }

    }

    private void setSelectedFiles(Collection<AbstractFile> files) {
        selectedFiles.clear();
        selectedFiles.addAll(files);
    }
}
