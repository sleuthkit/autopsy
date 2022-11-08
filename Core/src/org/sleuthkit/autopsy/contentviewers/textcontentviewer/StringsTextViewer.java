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
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.contentviewers.utils.ViewerPriority;
import org.sleuthkit.autopsy.corecomponentinterfaces.TextViewer;
import org.sleuthkit.autopsy.corecomponents.DataContentViewerUtility;
import org.sleuthkit.autopsy.datamodel.StringContent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * A text viewer that displays the strings extracted from contents.
 */
@ServiceProvider(service = TextViewer.class, position = 1)
public class StringsTextViewer implements TextViewer {

    private StringsContentPanel panel;

    @Override
    public void setNode(Node selectedNode) {
        if ((selectedNode == null) || (!isSupported(selectedNode))) {
            resetComponent();
            return;
        }
        Content content = DataContentViewerUtility.getDefaultContent(selectedNode);
        if (content != null) {
            panel.setDataView(content, 0);
            return;
        } else {
            StringContent scontent = selectedNode.getLookup().lookup(StringContent.class);
            if (scontent != null) {
                panel.setDataView(scontent);
                return;
            }
        }
        resetComponent();
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "StringsTextViewer.title");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "StringsTextViewer.toolTip");
    }

    @Override
    public TextViewer createInstance() {
        return new StringsTextViewer();
    }

    @Override
    public void resetComponent() {
        panel.resetDisplay();
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }
        Content content = DataContentViewerUtility.getDefaultContent(node);
        return (content != null && !(content instanceof BlackboardArtifact) && content.getSize() > 0);
    }

    @Override
    public int isPreferred(Node node) {
        return ViewerPriority.viewerPriority.LevelOne.getFlag();
    }

    @Override
    public Component getComponent() {
        if (panel == null) {
            panel = new StringsContentPanel();
        }
        return panel;
    }

}
