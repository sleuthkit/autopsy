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
package org.sleuthkit.autopsy.communications.relationships;

import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * An AbstractNode subclass which wraps a MessageNode object.  Doing this allows
 * for the reuse of the createSheet and other function from MessageNode, but
 * also some customizing of how a ThreadNode is shown.
 */
final class ThreadNode extends AbstractNode{
    
    final private MessageNode messageNode;
    
    ThreadNode(BlackboardArtifact artifact, String threadID, Action preferredAction) {
        super(Children.LEAF);
            messageNode = new MessageNode(artifact, threadID, preferredAction);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/communications/images/threaded.png" );
    }
    
    @Override
    protected Sheet createSheet() {
        return messageNode.createSheet();
    }
    
    String getThreadID() {
        return messageNode.getThreadID();
    }
    
    @Override
    public Action getPreferredAction() {
        return messageNode.getPreferredAction();
    }
    
    @Override
    public String getDisplayName() {
        return messageNode.getDisplayName();
    }
}
