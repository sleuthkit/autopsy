/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
import java.util.prefs.Preferences;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbPreferences;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;

/**
 * A Filter Node responsible for filtering out Nodes that represent known files
 * if the filter known files option is set.
 * 
 * @author jwallace
 */
public class KnownFileFilterNode extends FilterNode {
    
    public enum SelectionContext {
        DATA_SOURCES("Data Sources"),   // Subnode of DataSources
        VIEWS("Views"),                 // Subnode of Views
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
    
    public KnownFileFilterNode(Node arg, SelectionContext context) {
        super(arg, new KnownFileFilterChildren(arg, context));
    }
    
    KnownFileFilterNode(Node arg, boolean filter) {
        super(arg, new KnownFileFilterChildren(arg, filter));
    }
    
    public static SelectionContext getSelectionContext(Node n) {
        if (n == null) {
            // Parent of root node. Should never get here.
            return SelectionContext.OTHER;
        } else if (n.getParentNode().getParentNode() == null) {
            // One level below root node. Should be one of DataSources, Views, or Results
            return SelectionContext.getContextFromName(n.getDisplayName());
        } else {
            return getSelectionContext(n.getParentNode());
        }
    }
}

/**
 * Complementary class to KnownFileFilterNode. 
 * 
 * Listens for changes to the Filter Known Files option. Filters out children
 * the Nodes which represent known files. Otherwise, returns the original node
 * wrapped in another instance of the KnownFileFilterNode.
 * 
 * @author jwallace
 */
class KnownFileFilterChildren extends FilterNode.Children {
    
    /** Preference key values. */
    private static final String DS_HIDE_KNOWN = "dataSourcesHideKnown"; // Default false
    private static final String VIEWS_HIDE_KNOWN = "viewsHideKnown"; // Default true
    
    /** True if Nodes selected from the Views Node should filter Known Files. */
    private static boolean filterFromViews = true;
    
    /** True if Nodes selected from the DataSources Node should filter Known Files. */
    private static boolean filterFromDataSources = false;
    
    /** True if a listener has not been added to the preferences. */
    private static boolean addListener = true;
    
    /** True if this KnownFileFilterChildren should filter out known files. */
    private boolean filter;
    
    /** 
     * Constructor used when the context has already been determined.
     * Should only be used internally by KnownFileFilerNode.
     * @param arg
     * @param filter 
     */
    KnownFileFilterChildren(Node arg, boolean filter) {
        super(arg);
        this.filter = filter;
    }

    /**
     * Constructor used when the context has not been determined.
     * @param arg
     * @param context 
     */
    KnownFileFilterChildren(Node arg, KnownFileFilterNode.SelectionContext context) {
        super(arg);
        
        if (addListener) {
            Preferences prefs = NbPreferences.root().node("/org/sleuthkit/autopsy/core");
            // Initialize with values stored in preferences
            filterFromViews = prefs.getBoolean(VIEWS_HIDE_KNOWN, filterFromViews);
            filterFromDataSources = prefs.getBoolean(DS_HIDE_KNOWN, filterFromDataSources);
            
            // Add listener
            prefs.addPreferenceChangeListener(new PreferenceChangeListener() {
                @Override
                public void preferenceChange(PreferenceChangeEvent evt) {
                    switch (evt.getKey()) {
                        case VIEWS_HIDE_KNOWN:
                            filterFromViews = evt.getNode().getBoolean(VIEWS_HIDE_KNOWN, filterFromViews);
                            break;
                        case DS_HIDE_KNOWN:
                            filterFromDataSources = evt.getNode().getBoolean(DS_HIDE_KNOWN, filterFromDataSources);
                            break;
                    }
                }
            });
            addListener = false;
        }
        
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
            if (file!= null && file.getKnown() == TskData.FileKnown.KNOWN) {
                return new Node[]{};
            }
        }
        return new Node[] { new KnownFileFilterNode(arg, filter) };
    }
}
