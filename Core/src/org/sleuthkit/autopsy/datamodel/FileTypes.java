/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * File Types node support
 */
public final class FileTypes implements AutopsyVisitableItem {

    private final static Logger logger = Logger.getLogger(FileTypes.class.getName());

    private final SleuthkitCase skCase;

    FileTypes(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    SleuthkitCase getSleuthkitCase() {
        return skCase;
    }

    @NbBundle.Messages("FileTypes.name.text=File Types")
    static private final String NAME = Bundle.FileTypes_name_text();

    /**
     * Node which will contain By Mime Type and By Extension nodes.
     */
    public final class FileTypesNode extends DisplayableItemNode {

        FileTypesNode() {
            super(new RootContentChildren(Arrays.asList(
                    new FileTypesByExtension(skCase),
                    new FileTypesByMimeType(skCase))),
                    Lookups.singleton(NAME));
            setName(NAME);
            setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        @NbBundle.Messages({
            "FileTypes.createSheet.name.name=Name",
            "FileTypes.createSheet.name.displayName=Name",
            "FileTypes.createSheet.name.desc=no description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(Bundle.FileTypes_createSheet_name_name(),
                    Bundle.FileTypes_createSheet_name_displayName(),
                    Bundle.FileTypes_createSheet_name_desc(),
                    NAME
            ));
            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

    }

    static class FileNodeCreationVisitor extends ContentVisitor.Default<AbstractNode> {

        FileNodeCreationVisitor() {
        }

        @Override
        public FileNode visit(File f) {
            return new FileNode(f, false);
        }

        @Override
        public DirectoryNode visit(Directory d) {
            return new DirectoryNode(d);
        }

        @Override
        public LayoutFileNode visit(LayoutFile lf) {
            return new LayoutFileNode(lf);
        }

        @Override
        public LocalFileNode visit(DerivedFile df) {
            return new LocalFileNode(df);
        }

        @Override
        public LocalFileNode visit(LocalFile lf) {
            return new LocalFileNode(lf);
        }

        @Override
        public SlackFileNode visit(SlackFile sf) {
            return new SlackFileNode(sf, false);
        }

        @Override
        protected AbstractNode defaultVisit(Content di) {
            throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(), "FileTypeChildren.exception.notSupported.msg", di.toString()));
        }
    }

    static abstract class BGCountUpdatingNode extends DisplayableItemNode implements Observer {

        private long childCount = -1;

        BGCountUpdatingNode(Children children) {
            this(children, null);
        }

        BGCountUpdatingNode(Children children, Lookup lookup) {
            super(children, lookup);
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        abstract String getDisplayNameBase();

        /**
         * Calculate the number of children of this node, possibly by querying
         * the DB.
         *
         * @return @throws TskCoreException if there was an error querying the
         *         DB to calculate the number of children.
         */
        abstract long calculateChildCount() throws TskCoreException;

        /**
         * Updates the display name of the mediaSubTypeNode to include the count
         * of files which it represents.
         */
        @NbBundle.Messages("FileTypes.bgCounting.placeholder= (counting...)")
        void updateDisplayName() {
            //only show "(counting...)" the first time, otherwise it is distracting.
            setDisplayName(getDisplayNameBase() + ((childCount < 0) ? Bundle.FileTypes_bgCounting_placeholder()
                    : ("(" + childCount + ")"))); //NON-NLS
            new SwingWorker<Long, Void>() {
                @Override
                protected Long doInBackground() throws Exception {
                    return calculateChildCount();
                }

                @Override
                protected void done() {
                    try {
                        childCount = get();
                        setDisplayName(getDisplayNameBase() + " (" + childCount + ")"); //NON-NLS
                    } catch (InterruptedException | ExecutionException ex) {
                        setDisplayName(getDisplayNameBase());
                        logger.log(Level.WARNING, "Failed to get count of files for " + getDisplayNameBase(), ex); //NON-NLS
                    }
                }
            }.execute();
        }
    }
}
