/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.util.Calendar;
import java.util.Locale;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.RecentFiles.RecentFilesFilter;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Node for recent files filter
 */
public class RecentFilesFilterNode extends DisplayableItemNode {

    SleuthkitCase skCase;
    RecentFilesFilter filter;
    private final static Logger logger = Logger.getLogger(RecentFilesFilterNode.class.getName());

    RecentFilesFilterNode(SleuthkitCase skCase, RecentFilesFilter filter, Calendar lastDay) {
        super(Children.create(new RecentFilesFilterChildren(filter, skCase, lastDay), true), Lookups.singleton(filter.getDisplayName()));
        super.setName(filter.getName());
        this.skCase = skCase;
        this.filter = filter;
        Calendar prevDay = (Calendar) lastDay.clone();
        prevDay.add(Calendar.DATE, -filter.getDurationDays());
        String tooltip = prevDay.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) + " "
                + prevDay.get(Calendar.DATE) + ", "
                + prevDay.get(Calendar.YEAR);
        this.setShortDescription(tooltip);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/recent_files.png"); //NON-NLS

        //get count of children without preloading all children nodes
        final long count = new RecentFilesFilterChildren(filter, skCase, lastDay).calculateItems();
        super.setDisplayName(filter.getDisplayName() + " (" + count + ")");
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "RecentFilesFilterNode.createSheet.filterType.name"),
                NbBundle.getMessage(this.getClass(), "RecentFilesFilterNode.createSheet.filterType.displayName"),
                NbBundle.getMessage(this.getClass(), "RecentFilesFilterNode.createSheet.filterType.desc"),
                filter.getDisplayName()));

        return sheet;
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        if (filter == null) {
            return getClass().getName();
        } else {
            return getClass().getName() + filter.getName();
        }
    }
}
