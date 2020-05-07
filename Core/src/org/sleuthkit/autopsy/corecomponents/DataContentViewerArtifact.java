/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.contentviewers.ArtifactContentViewer;
import org.sleuthkit.autopsy.contentviewers.MessageContentViewer;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

@ServiceProvider(service = DataContentViewer.class, position = 7)
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DataContentViewerArtifact extends javax.swing.JPanel implements DataContentViewer {

    private static final long serialVersionUID = 1L;

    private Node currentNode; // @@@ Remove this when the redundant setNode() calls problem is fixed. 
    private ArtifactContentViewer lastViewer;

    private final static Logger logger = Logger.getLogger(DataContentViewerArtifact.class.getName());

    // TBD: This hardcoded list of viewers should be replaced with a dynamic lookup
    private final Collection<ArtifactContentViewer> KNOWN_ARTIFACT_VIEWERS
            = Arrays.asList(
                    new MessageContentViewer()
            );

    private final ArtifactContentViewer defaultArtifactContentViewer;

    /**
     * Creates new form DataContentViewerArtifact
     */
    public DataContentViewerArtifact() {
        initComponents();

        defaultArtifactContentViewer = new DefaultArtifactContentViewer();
    }

    @Override
    public void setNode(Node selectedNode) {

        if (currentNode == selectedNode) {
            return;
        }
        currentNode = selectedNode;

        // Make sure there is a node. Null might be passed to reset the viewer.
        if (selectedNode == null) {
            resetComponents();
            return;
        }

        // Make sure the node is of the correct type.
        BlackboardArtifact artifact = selectedNode.getLookup().lookup(BlackboardArtifact.class);
        if (artifact == null) {
            return;
        }

        ArtifactContentViewer viewer = getSupportingViewer(selectedNode);

        lastViewer = viewer;
        viewer.setNode(selectedNode);

        // Get and overlay the panel from the ArtifactContentViewer engaged
        this.removeAll();
        this.add(viewer.getComponent());

        this.revalidate();
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerArtifact.title");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerArtifact.toolTip");
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerArtifact();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        resetComponents();
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        for (Content content : node.getLookup().lookupAll(Content.class)) {
            if ((content != null) && (!(content instanceof BlackboardArtifact))) {
                try {
                    return content.getAllArtifactsCount() > 0;
                } catch (TskException ex) {
                    logger.log(Level.SEVERE, "Couldn't get count of BlackboardArtifacts for content", ex); //NON-NLS
                }
            }
        }
        return false;
    }

    @Override
    public int isPreferred(Node node) {
        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
        // low priority if node doesn't have an artifact (meaning it was found from normal directory
        // browsing, or if the artifact is something that means the user really wants to see the original
        // file and not more details about the artifact
        if ((artifact == null)
                || (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID())
                || (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID())
                || (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID())
                || (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID())
                || (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID())
                || (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID())) {
            return 3;
        } else {
            return 6;
        }
    }

    private void resetComponents() {
        currentNode = null;
        lastViewer = null;
    }

    /**
     * Get the ArtifactContentViewer that supports the given node.
     *
     * @param node Node to check.
     *
     * @return ArtifactContentViewer the artifact content viewer that supports
     * this node.
     */
    private ArtifactContentViewer getSupportingViewer(Node node) {

        return KNOWN_ARTIFACT_VIEWERS.stream()
                .filter(knownViewer -> knownViewer.isSupported(node))
                .findAny()
                .orElse(defaultArtifactContentViewer);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new javax.swing.OverlayLayout(this));
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
