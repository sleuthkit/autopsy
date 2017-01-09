/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;

/**
 * A Filter Node responsible for conditionally filtering out Nodes that
 * represent known files.
 *
 * Filters known files IF the option to Filter Known files for the given
 * SelectionContext is set. Otherwise, does nothing.
 *
 * @author jwallace
 */
public class KnownFileFilterNode extends FilterNode {

    private static boolean filterFromDataSources = UserPreferences.hideKnownFilesInDataSourcesTree();
    private static boolean filterFromViews = UserPreferences.hideKnownFilesInViewsTree();

    static {
        UserPreferences.addChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                switch (evt.getKey()) {
                    case UserPreferences.HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE:
                        filterFromDataSources = UserPreferences.hideKnownFilesInDataSourcesTree();
                        break;
                    case UserPreferences.HIDE_KNOWN_FILES_IN_VIEWS_TREE:
                        filterFromViews = UserPreferences.hideKnownFilesInViewsTree();
                        break;
                }
            }
        });
    }

    public enum SelectionContext {

        DATA_SOURCES(NbBundle.getMessage(KnownFileFilterNode.class, "KnownFileFilterNode.selectionContext.dataSources")),
        VIEWS(NbBundle.getMessage(KnownFileFilterNode.class, "KnownFileFilterNode.selectionContext.views")),
        OTHER("");                      // Subnode of another node.

        private final String displayName;

        SelectionContext(String displayName) {
            this.displayName = displayName;
        }

        public static SelectionContext getContextFromName(String name) {
            if (name.equals(DATA_SOURCES.getName())) {
                return DATA_SOURCES;
            } else if (name.equals(VIEWS.getName())) {
                return VIEWS;
            } else {
                return OTHER;
            }
        }

        private String getName() {
            return displayName;
        }
    }

    /**
     * Create a KnownFileFilterNode from the given Node. Note that the Node
     * should be from the directory tree.
     *
     * @param arg
     * @param context
     */
    public KnownFileFilterNode(Node arg, SelectionContext context) {
        super(arg, new KnownFileFilterChildren(arg, context));
    }

    private KnownFileFilterNode(Node arg, boolean filter) {
        super(arg, new KnownFileFilterChildren(arg, filter));
    }

    /**
     * Get the selection context of a Node in the DirectoryTree.
     *
     * @param n
     *
     * @return
     */
    public static SelectionContext getSelectionContext(Node n) {
        if (n == null || n.getParentNode() == null) {
            // Parent of root node or root node. Occurs during case open / close.
            return SelectionContext.OTHER;
        } else if (n.getParentNode().getParentNode() == null) {
            // One level below root node. Should be one of DataSources, Views, or Results
            return SelectionContext.getContextFromName(n.getDisplayName());
        } else {
            return getSelectionContext(n.getParentNode());
        }
    }

    /**
     * Complementary class to KnownFileFilterNode.
     *
     * Filters out children Nodes that represent known files. Otherwise, returns
     * the original node wrapped in another instance of the KnownFileFilterNode.
     *
     * @author jwallace
     */
    private static class KnownFileFilterChildren extends FilterNode.Children {

        /**
         * True if this KnownFileFilterChildren should filter out known files.
         */
        private boolean filter;

        /**
         * Constructor used when the context has already been determined.
         *
         * @param arg
         * @param filter
         */
        private KnownFileFilterChildren(Node arg, boolean filter) {
            super(arg);
            this.filter = filter;
        }

        /**
         * Constructor used when the context has not been determined.
         *
         * @param arg
         * @param context
         */
        private KnownFileFilterChildren(Node arg, KnownFileFilterNode.SelectionContext context) {
            super(arg);

            switch (context) {
                case DATA_SOURCES:
                    filter = filterFromDataSources;
                    break;
                case VIEWS:
                    filter = filterFromViews;
                    break;
                default:
                    filter = false;
                    break;
            }
        }

        @Override
        protected Node[] createNodes(Node arg) {
            if (filter) {
                // Filter out child nodes that represent known files
                AbstractFile file = arg.getLookup().lookup(AbstractFile.class);
                if (file != null && (file.getKnown() == TskData.FileKnown.KNOWN)) {
                    return new Node[]{};
                }
            }
            return new Node[]{new KnownFileFilterNode(arg, filter)};
        }
    }
}
