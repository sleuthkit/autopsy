/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.openide.nodes.Children.Keys;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Abstract subclass for ContentChildren and RootContentChildren implementations
 * that handles creating Nodes from Content objects.
*/
abstract class AbstractContentChildren extends Keys<Content> {
    private static CreateNodeVisitor createNode = new CreateNodeVisitor();

    /**
     * Uses lazy Content.Keys 
     */
    AbstractContentChildren() {
        super(true); // use lazy behavior
    }

    @Override
    protected Node[] createNodes(Content key) {
        return new Node[]{key.accept(createNode)};
    }
    
    @Override
    abstract protected void addNotify();
    
    @Override
    abstract protected void removeNotify();
   
    
    /**
     * Creates appropriate Node for each sub-class of Content
     */
    static class CreateNodeVisitor implements ContentVisitor<AbstractContentNode> {
        
        @Override
        public AbstractContentNode visit(Directory drctr) {
            return new DirectoryNode(drctr);
        }

        @Override
        public AbstractContentNode visit(File file) {
            return new FileNode(file);
        }

        @Override
        public AbstractContentNode visit(FileSystem fs) {
            throw new UnsupportedOperationException("No Node defined for FileSystems.");
        }

        @Override
        public AbstractContentNode visit(Image image) {
            return new ImageNode(image);
        }

        @Override
        public AbstractContentNode visit(Volume volume) {
            return new VolumeNode(volume);
        }

        @Override
        public AbstractContentNode visit(VolumeSystem vs) {
            throw new UnsupportedOperationException("No Node defined for VolumeSystems.");
        }
    }
    
}
