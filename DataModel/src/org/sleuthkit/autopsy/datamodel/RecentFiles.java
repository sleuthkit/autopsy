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
        AUT_1DAY_FILTER(0, "AUT_1DAY_FILTER", "Last 24 Hours", 60*60*24),
        AUT_1WEEK_FILTER(1, "AUT_1WEEK_FILTER", "Last 7 Days", 60*60*24*7),
        AUT_1MONTH_FILTER(2, "AUT_1MONTH_FILTER", "Last 30 Days", 60*60*24*30);
        
        int id;
        String name;
        String displayName;
        int duration;
        
        private RecentFilesFilter(int id, String name, String displayName, int duration){
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.duration = duration;
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
        
        public int getDuration() {
            return this.duration;
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
