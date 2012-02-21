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

import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author dfickling
 */
public class RecentFiles implements AutopsyVisitableItem {
    
    SleuthkitCase skCase;
    
    public enum RecentFilesFilter implements AutopsyVisitableItem {
        AUT_1DAY_FILTER(0, "AUT_1DAY_FILTER", "Last 1 Day", 1),
        AUT_2DAY_FILTER(0, "AUT_2DAY_FILTER", "Last 2 Days", 2),
        AUT_3DAY_FILTER(0, "AUT_3DAY_FILTER", "Last 3 Days", 3),
        AUT_4DAY_FILTER(0, "AUT_4DAY_FILTER", "Last 4 Days", 4),
        AUT_5DAY_FILTER(0, "AUT_5DAY_FILTER", "Last 5 Days", 5),
        AUT_10DAY_FILTER(0, "AUT_10DAY_FILTER", "Last 10 Days", 10),
        AUT_15DAY_FILTER(0, "AUT_15DAY_FILTER", "Last 15 Days", 15);
        
        int id;
        String name;
        String displayName;
        int durationDays;
        int durationSeconds;
        
        private RecentFilesFilter(int id, String name, String displayName, int durationDays){
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.durationDays = durationDays;
            this.durationSeconds = durationDays*60*60*24;
        }
        
        public String getName(){
            return this.name;
        }

        public int getId(){
            return this.id;
        }

        public String getDisplayName(){
            return this.displayName;
        }
        
        public int getDurationSeconds() {
            return this.durationSeconds;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> v) {
            return v.visit(this);
        }
        
    }
    
    public RecentFiles(SleuthkitCase skCase){
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }
    public SleuthkitCase getSleuthkitCase(){
        return this.skCase;
    }
    
}
