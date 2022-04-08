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

import org.openide.nodes.Node;
import org.sleuthkit.datamodel.BlackboardArtifact;
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
import org.sleuthkit.datamodel.UnsupportedContent;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/**
 * Produces legacy version of content nodes.
 */
public class RootContentChildren {
    /**
     * Creates a node for one of the known object keys that is not a sleuthkit
     * item.
     *
     * @param key The node key.
     *
     * @return The generated node or null if no match found.
     */
    public static Node createNode(Object key) {
        if (key instanceof Directory) {
            Directory drctr = (Directory) key;
            return new DirectoryNode(drctr);
        } else if (key instanceof File) {
            File file = (File) key;
            return new FileNode(file);
        } else if (key instanceof Image) {
            Image image = (Image) key;
            return new ImageNode(image);
        } else if (key instanceof Volume) {
            Volume volume = (Volume) key;
            return new VolumeNode(volume);
        } else if (key instanceof Pool) {
            Pool pool = (Pool) key;
            return new PoolNode(pool);
        } else if (key instanceof LayoutFile) {
            LayoutFile lf = (LayoutFile) key;
            return new LayoutFileNode(lf);
        } else if (key instanceof DerivedFile) {
            DerivedFile df = (DerivedFile) key;
            return new LocalFileNode(df);
        } else if (key instanceof LocalFile) {
            LocalFile lf = (LocalFile) key;
            return new LocalFileNode(lf);
        } else if (key instanceof VirtualDirectory) {
            VirtualDirectory ld = (VirtualDirectory) key;
            return new VirtualDirectoryNode(ld);
        } else if (key instanceof LocalDirectory) {
            LocalDirectory ld = (LocalDirectory) key;
            return new LocalDirectoryNode(ld);
        } else if (key instanceof SlackFile) {
            SlackFile sf = (SlackFile) key;
            return new SlackFileNode(sf);
        } else if (key instanceof BlackboardArtifact) {
            BlackboardArtifact art = (BlackboardArtifact) key;
            return new BlackboardArtifactNode(art);
        } else if (key instanceof UnsupportedContent) {
            UnsupportedContent uc = (UnsupportedContent) key;
            return new UnsupportedContentNode(uc);
        } else if (key instanceof LocalFilesDataSource) {
            LocalFilesDataSource ld = (LocalFilesDataSource) key;
            return new LocalFilesDataSourceNode(ld);
        } else {
            return null;
        }
    }
}
