/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.util.ArrayList;
import java.util.Collection;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.UnsupportedContent;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * Finds top level objects such as file system root directories, layout files
 * and virtual directories.
 */
final class GetRootDirectoryVisitor extends GetFilesContentVisitor {

    @Override
    public Collection<AbstractFile> visit(VirtualDirectory ld) {
        //case when we hit a layout directory or local file container, not under a real FS
        //or when root virt dir is scheduled
        Collection<AbstractFile> ret = new ArrayList<>();
        ret.add(ld);
        return ret;
    }
    
    @Override
    public Collection<AbstractFile> visit(LocalDirectory ld) {
        //case when we hit a local directory
        Collection<AbstractFile> ret = new ArrayList<>();
        ret.add(ld);
        return ret;
    }

    @Override
    public Collection<AbstractFile> visit(LayoutFile lf) {
        //case when we hit a layout file, not under a real FS
        Collection<AbstractFile> ret = new ArrayList<>();
        ret.add(lf);
        return ret;
    }

    @Override
    public Collection<AbstractFile> visit(Directory drctr) {
        //we hit a real directory, a child of real FS
        Collection<AbstractFile> ret = new ArrayList<>();
        ret.add(drctr);
        return ret;
    }

    @Override
    public Collection<AbstractFile> visit(FileSystem fs) {
        return getAllFromChildren(fs);
    }

    @Override
    public Collection<AbstractFile> visit(File file) {
        //can have derived files
        return getAllFromChildren(file);
    }

    @Override
    public Collection<AbstractFile> visit(DerivedFile derivedFile) {
        //can have derived files
        //TODO test this and overall scheduler with derived files
        return getAllFromChildren(derivedFile);
    }

    @Override
    public Collection<AbstractFile> visit(LocalFile localFile) {
        //can have local files
        //TODO test this and overall scheduler with local files
        return getAllFromChildren(localFile);
    }

    @Override
    public Collection<AbstractFile> visit(SlackFile slackFile) {
        //can have slack files
        //TODO test this and overall scheduler with local files
        return getAllFromChildren(slackFile);
    }

    @Override
    public Collection<AbstractFile> visit(BlackboardArtifact art) {
        return getAllFromChildren(art);
    }
    
    @Override
    public Collection<AbstractFile> visit(OsAccount art) {
        return getAllFromChildren(art);
    }
    
    @Override
    public Collection<AbstractFile> visit(UnsupportedContent uc) {
        return getAllFromChildren(uc);
    }
}
