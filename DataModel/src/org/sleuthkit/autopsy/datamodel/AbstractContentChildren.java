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

import java.util.Arrays;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children.Keys;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsRootNode;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.SleuthkitItemVisitor;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Abstract subclass for ContentChildren and RootContentChildren implementations
 * that handles creating Nodes from Content objects.
*/
abstract class AbstractContentChildren extends Keys<Object> {

    /**
     * Uses lazy Content.Keys 
     */
    AbstractContentChildren() {
        super(true); // use lazy behavior
    }

    @Override
    protected Node[] createNodes(Object key) {
        if(key instanceof SleuthkitVisitableItem)
            return new Node[]{((SleuthkitVisitableItem) key).accept(new CreateSleuthkitNodeVisitor())};
        else
            return new Node[]{((AutopsyVisitableItem) key).accept(new CreateAutopsyNodeVisitor())};
    }
    
    @Override
    abstract protected void addNotify();
    
    @Override
    abstract protected void removeNotify();
   
    
    /**
     * Creates appropriate Node for each sub-class of Content
     */
    static class CreateSleuthkitNodeVisitor extends SleuthkitItemVisitor.Default<AbstractContentNode> {
        
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
            return defaultVisit(fs);
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
            return defaultVisit(vs);
        }

        @Override
        public AbstractContentNode visit(BlackboardArtifact.ARTIFACT_TYPE a) {
            return defaultVisit(a);
        }

        @Override
        public AbstractContentNode visit(BlackboardArtifact ba) {
            return defaultVisit(ba);
        }

        @Override
        protected AbstractContentNode defaultVisit(SleuthkitVisitableItem di) {
            throw new UnsupportedOperationException("No Node defined for the given DisplayableItem");
        }
    }
    
    /**
     * Creates appropriate Node for each sub-class of Content
     */
    static class CreateAutopsyNodeVisitor extends AutopsyItemVisitor.Default<AbstractNode> {
        
        @Override
        public ExtractedContentNode visit(ExtractedContent ec) {
            return new ExtractedContentNode(ec.getSleuthkitCase());
        }
        
        @Override
        public AbstractNode visit(SearchFilters sf) {
            return new SearchFiltersNode(sf.getSleuthkitCase(), true);
        }
        
        @Override
        public AbstractNode visit(RecentFiles rf) {
            return new RecentFilesNode(rf.getSleuthkitCase());
        }
        
        @Override
        public AbstractNode visit(KeywordHits kh) {
            return kh.new KeywordHitsRootNode();
        }
        
        @Override
        public AbstractNode visit(Images i) {
            try {
                return new ImagesNode(i.getSleuthkitCase().getRootObjects());
            } catch (TskException ex) {
                return defaultVisit(i);
            }
        }
        
        @Override
        public AbstractNode visit(Views v) {
            return new ViewsNode(v.getSleuthkitCase());
        }
        
        @Override
        public AbstractNode visit(Results r) {
            return new ResultsNode(r.getSleuthkitCase());
        }

        @Override
        protected AbstractNode defaultVisit(AutopsyVisitableItem di) {
            throw new UnsupportedOperationException("No Node defined for the given DisplayableItem");
        }
    }
    
}
