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

import org.sleuthkit.autopsy.datamodel.AutopsyVisitableItem;
import org.sleuthkit.autopsy.datamodel.AutopsyItemVisitor;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Recent files node support NOTE: As of june '15 we do not display this in the
 * tree. It can be added back when we have filtering in the results area.
 */
public class RecentFiles implements AutopsyVisitableItem {

    SleuthkitCase skCase;

    public enum RecentFilesFilter implements AutopsyVisitableItem {

        AUT_0DAY_FILTER(0, "AUT_0DAY_FILTER", //NON-NLS
                NbBundle.getMessage(RecentFiles.class, "RecentFiles.aut0DayFilter.displayName.text"), 0),
        AUT_1DAY_FILTER(0, "AUT_1DAY_FILTER", //NON-NLS
                NbBundle.getMessage(RecentFiles.class, "RecentFiles.aut1dayFilter.displayName.text"), 1),
        AUT_2DAY_FILTER(0, "AUT_2DAY_FILTER", //NON-NLS
                NbBundle.getMessage(RecentFiles.class, "RecentFiles.aut2dayFilter.displayName.text"), 2),
        AUT_3DAY_FILTER(0, "AUT_3DAY_FILTER", //NON-NLS
                NbBundle.getMessage(RecentFiles.class, "RecentFiles.aut3dayFilter.displayName.text"), 3),
        AUT_4DAY_FILTER(0, "AUT_4DAY_FILTER", //NON-NLS
                NbBundle.getMessage(RecentFiles.class, "RecentFiles.aut4dayFilter.displayName.text"), 4),
        AUT_5DAY_FILTER(0, "AUT_5DAY_FILTER", //NON-NLS
                NbBundle.getMessage(RecentFiles.class, "RecentFiles.aut5dayFilter.displayName.text"), 5),
        AUT_6DAY_FILTER(0, "AUT_6DAY_FILTER", //NON-NLS
                NbBundle.getMessage(RecentFiles.class, "RecentFiles.aut6dayFilter.displayName.text"), 6);

        private int id;
        private String name;
        private String displayName;
        private int durationDays;

        private RecentFilesFilter(int id, String name, String displayName, int durationDays) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.durationDays = durationDays;
        }

        public String getName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public int getDurationDays() {
            return this.durationDays;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public RecentFiles(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public SleuthkitCase getSleuthkitCase() {
        return this.skCase;
    }

}
