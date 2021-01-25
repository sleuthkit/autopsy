/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.discovery.ui;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Create a node containing the children to display in the
 * DataResultViewerThumbnail
 */
class DiscoveryThumbnailChildren extends Children.Keys<AbstractFile> {

    private final List<AbstractFile> files;

    /*
     * Creates the list of thumbnails from the given list of AbstractFiles.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    DiscoveryThumbnailChildren(List<AbstractFile> files) {
        super(false);
        this.files = files;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    protected Node[] createNodes(AbstractFile t) {
        return new Node[]{new ThumbnailNode(t)};
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    protected void addNotify() {
        super.addNotify();
        Set<AbstractFile> thumbnails = new TreeSet<>((AbstractFile file1, AbstractFile file2) -> {
            int result = Long.compare(file1.getSize(), file2.getSize());
            if (result == 0) {
                result = file1.getName().compareTo(file2.getName());
            }
            return result;
        });
        thumbnails.addAll(files);
        setKeys(thumbnails);
    }

    /**
     * A node for representing a thumbnail.
     */
    static class ThumbnailNode extends FileNode {

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        ThumbnailNode(AbstractFile file) {
            super(file, false);
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Set<String> keepProps = new HashSet<>(Arrays.asList(
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.score.name"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.comment.name"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.count.name"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.sizeColLbl"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.knownColLbl")));

            //Remove all other props except for the  ones above
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            for (Node.Property<?> p : sheetSet.getProperties()) {
                if (!keepProps.contains(p.getName())) {
                    sheetSet.remove(p.getName());
                }
            }

            return sheet;
        }
    }
}
