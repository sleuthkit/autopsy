/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.util.Collection;
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 */
public class RelaionshipSetNodeFactory extends ChildFactory<BlackboardArtifact> {

    private final Collection<BlackboardArtifact> artifacts;

    public RelaionshipSetNodeFactory(Collection<BlackboardArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    protected boolean createKeys(List<BlackboardArtifact> list) {
        list.addAll(artifacts);
        return true;
    }

    @Override
    protected Node createNodeForKey(BlackboardArtifact key) {
        return new RelationshipNode(key);
    }
}
