/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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

import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitItemVisitor;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.UnsupportedContent;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/**
 * Creates appropriate Node for each sub-class of Content
 */
public class CreateSleuthkitNodeVisitor extends SleuthkitItemVisitor.Default<AbstractContentNode<? extends Content>> {

    @Override
    public AbstractContentNode<? extends Content> visit(Directory drctr) {
        return new DirectoryNode(drctr);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(File file) {
        return new FileNode(file);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(Image image) {
        return new ImageNode(image);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(Volume volume) {
        return new VolumeNode(volume);
    }
    
    @Override
    public AbstractContentNode<? extends Content> visit(Pool pool) {
        return new PoolNode(pool);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(LayoutFile lf) {
        return new LayoutFileNode(lf);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(DerivedFile df) {
        return new LocalFileNode(df);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(LocalFile lf) {
        return new LocalFileNode(lf);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(VirtualDirectory ld) {
        return new VirtualDirectoryNode(ld);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(LocalDirectory ld) {
        return new LocalDirectoryNode(ld);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(SlackFile sf) {
        return new SlackFileNode(sf);
    }

    @Override
    public AbstractContentNode<? extends Content> visit(BlackboardArtifact art) {
        return new BlackboardArtifactNode(art);
    }
    
    @Override
    public AbstractContentNode<? extends Content> visit(UnsupportedContent uc) {
        return new UnsupportedContentNode(uc);
    }

    @Override
    protected AbstractContentNode<? extends Content> defaultVisit(SleuthkitVisitableItem di) {
        throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(),
                "AbstractContentChildren.CreateTSKNodeVisitor.exception.noNodeMsg"));
    }
    
    @Override
    public AbstractContentNode<? extends Content> visit(LocalFilesDataSource ld) {
        return new LocalFilesDataSourceNode(ld);
    }
}
