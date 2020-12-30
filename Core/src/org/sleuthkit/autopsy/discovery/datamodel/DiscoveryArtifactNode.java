/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery.datamodel;

import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.datamodel.BlackboardArtifact;

public class DiscoveryArtifactNode extends BlackboardArtifactNode {

    public DiscoveryArtifactNode(BlackboardArtifact artifact) {
        super(artifact);
    }

    public DiscoveryArtifactNode(BlackboardArtifact artifact, String iconPath) {
        super(artifact, iconPath);
    }

}
