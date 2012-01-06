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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Log;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Class for Children of all ContentNodes. Handles creating child ContentNodes.
 */
class ContentChildren extends AbstractContentChildren {

    private Content parent;

    ContentChildren(Content parent) {
        this.parent = parent;
    }
    private static CreateKeysVisitor createKeys = new CreateKeysVisitor();

    @Override
    protected void addNotify() {
        setKeys(createKeys.getChildrenKeys(parent));
    }

    @Override
    protected void removeNotify() {
        setKeys(Collections.EMPTY_SET);
    }

    static private class CreateKeysVisitor extends ContentVisitor.Default<List<? extends Content>> {

        List<Content> getChildrenKeys(Content parent) {
            List<Content> keys = new ArrayList<Content>();

            List<Content> children;

            try {
                children = parent.getChildren();
            } catch (TskException ex) {
                Log.get(CreateKeysVisitor.class).log(Level.WARNING, "Error getting Content children.", ex);
                children = Collections.EMPTY_LIST;
            }

            for (Content c : children) {
                keys.addAll(c.accept(this));
            }

            return keys;
        }

        @Override
        protected List<Content> defaultVisit(org.sleuthkit.datamodel.Content c) {
            return Collections.singletonList(c);
        }

        @Override
        public List<Content> visit(VolumeSystem vs) {
            return getChildrenKeys(vs);
        }

        @Override
        public List<Content> visit(FileSystem fs) {
            return getChildrenKeys(fs);
        }

        @Override
        public List<? extends Content> visit(Directory dir) {
            if (dir.isRoot()) {
                return getChildrenKeys(dir);
            } else {
                return Collections.singletonList(dir);
            }
        }
    }
}
