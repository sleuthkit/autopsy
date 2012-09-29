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

import java.util.Calendar;
import java.util.Locale;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
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
        super.setDisplayName(filter.getDisplayName());
        this.skCase = skCase;
        this.filter = filter;
        Calendar prevDay = (Calendar) lastDay.clone();
        prevDay.add(Calendar.DATE, -filter.getDurationDays());
        String tooltip = prevDay.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) + " "
                + prevDay.get(Calendar.DATE) + ", "
                + prevDay.get(Calendar.YEAR);
        this.setShortDescription(tooltip);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/recent_files.png");
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty("Filter Type",
                "Filter Type",
                "no description",
                filter.getDisplayName()));

        return s;
    }

    @Override
    public TYPE getDisplayableItemNodeType() {
        return TYPE.META;
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}
