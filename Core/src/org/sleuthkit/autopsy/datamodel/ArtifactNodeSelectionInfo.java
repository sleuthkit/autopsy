/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.Node;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Stores sufficient information to identify a blackboard artifact node that is
 * intended to be selected in a view.
 */
public class ArtifactNodeSelectionInfo implements NodeSelectionInfo {

    private final long artifactId;

    /**
     * Constructs an object that stores sufficient information to identify a
     * blackboard artifact node that is intended to be selected in a view.
     *
     * @param artifact The artifact represented by the node to be selected.
     */
    public ArtifactNodeSelectionInfo(BlackboardArtifact artifact) {
        this.artifactId = artifact.getArtifactID();
    }

    /**
     * Determines whether or not a given node satisfies the stored node
     * selection criteria.
     *
     * @param candidateNode A node to evaluate.
     *
     * @return True or false.
     */
    @Override
    public boolean matches(Node candidateNode) {
        BlackboardArtifact artifact = candidateNode.getLookup().lookup(BlackboardArtifact.class);
        return artifact.getArtifactID() == artifactId;
    }

}
