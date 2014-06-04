/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Implements the Reports subtree of the Autopsy tree.
 */
public class Reports implements AutopsyVisitableItem {

    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        
    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        // The CreateAutopsyNodeVisitor constructs a ReportsListNode when it
        // visits.
        return visitor.visit(this);
    }

    /**
     * The root node of the Reports subtree of the Autopsy tree.
     */
    public static class ReportsListNode extends DisplayableItemNode {

        private static final String DISPLAY_NAME = NbBundle.getMessage(ReportsListNode.class, "ReportsListNode.displayName");
        private static final String ICON_PATH = "org/sleuthkit/autopsy/images/report_16.png"; //NON-NLS

        public ReportsListNode() {
            super(Children.create(new ReportNodeFactory(), true));
            setName(DISPLAY_NAME);
            setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension(ICON_PATH);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            // The GetPopupActionsDisplayableItemNodeVisitor gets the Actions for this class.
            // The GetPreferredActionsDisplayableItemNodeVisitor RJCTODO?
            // The IsLeafItemVisitor returns false.
            // The ShowItemVisitor always returns true.
            return visitor.visit(this);
        }
    }

    /**
     * The child node factory that creates ReportNode children for a
     * ReportsListNode.
     */
    private static class ReportNodeFactory extends ChildFactory<Report> {

        ReportNodeFactory() {
            Case.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String eventType = evt.getPropertyName();
                    if (eventType.equals(Case.Events.REPORT_ADDED.toString())) {
                        ReportNodeFactory.this.refresh(true);
                    }
                }
            });
        }

        @Override
        protected boolean createKeys(List<Report> keys) {
            try {
                keys.addAll(Case.getCurrentCase().getAllReports());
            } catch (TskCoreException ex) {
                Logger.getLogger(Reports.ReportNodeFactory.class.getName()).log(Level.SEVERE, "Failed to get reports", ex); //NON-NLS
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(Report key) {
            return new ReportNode(key);
        }
    }

    /**
     * A leaf node in the Reports subtree of the Autopsy tree, wraps a Report
     * object.
     */
    public static class ReportNode extends DisplayableItemNode {

        private static final String ICON_PATH = "org/sleuthkit/autopsy/images/report_16.png"; //NON-NLS
        private final Report report;

        ReportNode(Report report) {
            super(Children.LEAF, Lookups.fixed(report));
            this.report = report;
            super.setName(this.report.getDisplayName());
            super.setDisplayName(this.report.getDisplayName());            
            this.setIconBaseWithExtension(ICON_PATH);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            // The GetPopupActionsDisplayableItemNodeVisitor gets the Actions for this class.
            // The GetPreferredActionsDisplayableItemNodeVisitor RJCTODO
            // The IsLeafItemVisitor returns true.
            // The ShowItemVisitor always returns true.
            return visitor.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set propertiesSet = sheet.get(Sheet.PROPERTIES);
            if (propertiesSet == null) {
                propertiesSet = Sheet.createPropertiesSet();
                sheet.put(propertiesSet);
            }
            propertiesSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ReportNode.displayNameProperty.name"),
                    NbBundle.getMessage(this.getClass(), "ReportNode.displayNameProperty.displayName"),
                    NbBundle.getMessage(this.getClass(), "ReportNode.displayNameProperty.desc"),
                    this.report.getDisplayName()));
            propertiesSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ReportNode.createdTimeProperty.name"),
                    NbBundle.getMessage(this.getClass(), "ReportNode.createdTimeProperty.displayName"),
                    NbBundle.getMessage(this.getClass(), "ReportNode.createdTimeProperty.desc"),
                    dateFormatter.format(new java.util.Date(this.report.getCreatedTime() * 1000)).toString()));                    
            propertiesSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ReportNode.pathProperty.name"),
                    NbBundle.getMessage(this.getClass(), "ReportNode.pathProperty.displayName"),
                    NbBundle.getMessage(this.getClass(), "ReportNode.pathProperty.desc"),
                    this.report.getPath()));
            return sheet;
        }
    }
}