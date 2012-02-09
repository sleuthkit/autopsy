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

/**
 *
 * @author dfickling
 */
public interface AutopsyItemVisitor<T> {
    
    T visit(ExtractedContent ec);
    T visit(SearchFilters sf);
    T visit(SearchFilters.FileSearchFilter fsf);
    T visit(RecentFiles rf);
    T visit(RecentFiles.RecentFilesFilter rff);
    
    static abstract public class Default<T> implements AutopsyItemVisitor<T> {

        protected abstract T defaultVisit(AutopsyVisitableItem ec);
        
        @Override
        public T visit(ExtractedContent ec) {
            return defaultVisit(ec);
        }
    
        @Override
        public T visit(SearchFilters sf) {
            return defaultVisit(sf);
        }
        
        @Override
        public T visit(SearchFilters.FileSearchFilter fsf) {
            return defaultVisit(fsf);
        }
        
        @Override
        public T visit(RecentFiles rf) {
            return defaultVisit(rf);
        }
        
        @Override
        public T visit(RecentFiles.RecentFilesFilter rff) {
            return defaultVisit(rff);
        }
    }
}
