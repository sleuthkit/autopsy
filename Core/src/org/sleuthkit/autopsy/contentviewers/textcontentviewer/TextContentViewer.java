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
package org.sleuthkit.autopsy.contentviewers.textcontentviewer;

import java.awt.Component;
import java.util.logging.Level;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A DataContentViewer that displays text with the TextViewers available.
 */
@ServiceProvider(service = DataContentViewer.class, position = 2)
public class TextContentViewer implements DataContentViewer {

    private final TextContentViewerPanel panel;
    private volatile Node currentNode = null;
    private static final Logger logger = Logger.getLogger(TextContentViewer.class.getName());

    /**
     * No arg constructor for creating the main instance of this Content Viewer.
     */
    public TextContentViewer() {
        this(true);
    }

    /**
     * Private constructor for creating instances of this content viewer.
     *
     * @param isMain
     */
    private TextContentViewer(boolean isMain) {
        panel = new TextContentViewerPanel(isMain);
    }

    @Override
    public void setNode(Node selectedNode) {
        if ((selectedNode == null) || (!isSupported(selectedNode))) {
            resetComponent();
            return;
        }
        currentNode = selectedNode;
        panel.setNode(currentNode);

    }

    @Messages({"TextContentViewer.title=Text"})
    @Override
    public String getTitle() {
        return Bundle.TextContentViewer_title();
    }

    @Messages({"TextContentViewer.tooltip=Displays text associated with the selected item"})
    @Override
    public String getToolTip() {
        return Bundle.TextContentViewer_tooltip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new TextContentViewer(false);
    }

    @Override
    public Component getComponent() {
        return panel;
    }

    @Override
    public void resetComponent() {
        currentNode = null;
        panel.setupTabs(currentNode);
    }

    @Override
    public boolean isSupported(Node node) {
        //if any of the subvewiers are supported then this is supported
        if (node == null) {
            return false;
        }
        // get the node's File, if it has one
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        if (file == null) {
            return false;
        }
        if (node instanceof BlackboardArtifactNode) {
            BlackboardArtifact theArtifact = ((BlackboardArtifactNode) node).getArtifact();
            //disable the content viewer when a download or cached file does not exist instead of displaying its parent
            try {
                if ((theArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()
                        || theArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID())
                        && file.getId() == theArtifact.getParent().getId()) {
                    return false;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Error getting parent of artifact with type %s and objID = %d can not confirm file with name %s and objId = %d is not the parent. Text content viewer will not be supported.",
                        theArtifact.getArtifactTypeName(), theArtifact.getObjectID(), file.getName(), file.getId()), ex);
                return false;
            }
        }
        // disable the text content viewer for directories and empty files
        if (file.isDir() || file.getSize() == 0) {
            return false;
        }

        return panel.isSupported(node);
    }

    @Override
    public int isPreferred(Node node) {
        //return max of supported TextViewers isPreferred methods
        return panel.isPreffered(node);
    }

}
