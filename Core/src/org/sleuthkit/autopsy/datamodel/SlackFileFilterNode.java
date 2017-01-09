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
 * represent slack files.
 *
 * Filters known files IF the option to Filter Slack files for the given
 * SelectionContext is set. Otherwise, does nothing.
 *
 */
public class SlackFileFilterNode extends FilterNode {

    private static boolean filterFromDataSources = UserPreferences.hideSlackFilesInDataSourcesTree();
    private static boolean filterFromViews = UserPreferences.hideSlackFilesInViewsTree();

    static {
        UserPreferences.addChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                switch (evt.getKey()) {
                    case UserPreferences.HIDE_SLACK_FILES_IN_DATA_SRCS_TREE:
                        filterFromDataSources = UserPreferences.hideSlackFilesInDataSourcesTree();
                        break;
                    case UserPreferences.HIDE_SLACK_FILES_IN_VIEWS_TREE:
                        filterFromViews = UserPreferences.hideSlackFilesInViewsTree();
                        break;
                }
            }
        });
    }

    public enum SelectionContext {

        DATA_SOURCES(NbBundle.getMessage(SlackFileFilterNode.class, "SlackFileFilterNode.selectionContext.dataSources")),
        VIEWS(NbBundle.getMessage(SlackFileFilterNode.class, "SlackFileFilterNode.selectionContext.views")),
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
     * Create a SlackFileFilterNode from the given Node. Note that the Node
     * should be from the directory tree.
     *
     * @param arg
     * @param context
     */
    public SlackFileFilterNode(Node arg, SelectionContext context) {
        super(arg, new SlackFileFilterChildren(arg, context));
    }

    private SlackFileFilterNode(Node arg, boolean filter) {
        super(arg, new SlackFileFilterChildren(arg, filter));
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
     * Complementary class to SlackFileFilterNode.
     *
     * Filters out children Nodes that represent slack files. Otherwise, returns
     * the original node wrapped in another instance of the SlackFileFilterNode.
     *
     * @author jwallace
     */
    private static class SlackFileFilterChildren extends FilterNode.Children {

        /**
         * True if this SlackFileFilterChildren should filter out slack files.
         */
        private boolean filter;

        /**
         * Constructor used when the context has already been determined.
         *
         * @param arg
         * @param filter
         */
        private SlackFileFilterChildren(Node arg, boolean filter) {
            super(arg);
            this.filter = filter;
        }

        /**
         * Constructor used when the context has not been determined.
         *
         * @param arg
         * @param context
         */
        private SlackFileFilterChildren(Node arg, SlackFileFilterNode.SelectionContext context) {
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
                // Filter out child nodes that represent slack files
                AbstractFile file = arg.getLookup().lookup(AbstractFile.class);
                if ((file != null) && file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)) {
                    return new Node[]{};
                }
            }
            return new Node[]{new SlackFileFilterNode(arg, filter)};
        }
    }
}