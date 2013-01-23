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
    T visit(SearchFilters.DocumentFilter df);
    T visit(RecentFiles rf);
    T visit(RecentFiles.RecentFilesFilter rff);
    T visit(KeywordHits kh);
    T visit(HashsetHits hh);
    T visit(EmailExtracted ee);
    T visit(Tags t);
    T visit(Images i);
    T visit(Views v);
    T visit(Results r);
    
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
        public T visit(SearchFilters.DocumentFilter df) {
            return defaultVisit(df);
        }
        
        @Override
        public T visit(RecentFiles rf) {
            return defaultVisit(rf);
        }
        
        @Override
        public T visit(RecentFiles.RecentFilesFilter rff) {
            return defaultVisit(rff);
        }
        
        @Override
        public T visit(KeywordHits kh) {
            return defaultVisit(kh);
        }
        
        @Override
        public T visit(HashsetHits hh) {
            return defaultVisit(hh);
        }
        
        @Override
        public T visit(EmailExtracted ee) {
            return defaultVisit(ee);
        }
        
        
        @Override
        public T visit(Tags t) {
            return defaultVisit(t);
        }
        
        @Override
        public T visit(Images i) {
            return defaultVisit(i);
        }
        
        @Override
        public T visit(Views v) {
            return defaultVisit(v);
        }
        
        @Override
        public T visit(Results r) {
            return defaultVisit(r);
        }
    }
}
