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

import java.util.Arrays;
import java.util.List;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Filters database results by file extension.
 */
public class SearchFilters implements AutopsyVisitableItem{

    SleuthkitCase skCase;

    public enum FileSearchFilter implements AutopsyVisitableItem,SearchFilterInterface {
        TSK_IMAGE_FILTER(0, "TSK_IMAGE_FILTER", "Images", Arrays.asList(".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff")),
        TSK_VIDEO_FILTER(1, "TSK_VIDEO_FILTER", "Videos",
            Arrays.asList(".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v", ".m4v",
            ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm", ".wmv", ".mpv")),
        TSK_AUDIO_FILTER(2, "TSK_AUDIO_FILTER", "Audio", 
            Arrays.asList(".aiff", ".aif", ".flac", ".wav", ".m4a", ".ape", ".wma", ".mp2",
            ".mp1", ".mp3", ".aac", ".mp4", ".m4p", ".m1a", ".m2a", ".m4r", ".mpa",
            ".m3u", ".mid", ".midi", ".ogg")),
        TSK_DOCUMENT_FILTER(3, "TSK_DOCUMENT_FILTER", "Documents", Arrays.asList(".doc", ".docx", ".pdf", ".xls", ".rtf", ".txt"));

        int id;
        String name;
        String displayName;
        List<String> filter;

        private FileSearchFilter(int id, String name, String displayName, List<String> filter){
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getName(){
            return this.name;
        }

        @Override
        public int getId(){
            return this.id;
        }

        @Override
        public String getDisplayName(){
            return this.displayName;
        }

        @Override
        public List<String> getFilter(){
            return this.filter;
        }
    }
    
    public enum DocumentFilter implements AutopsyVisitableItem,SearchFilterInterface {
        AUT_DOC_HTML(0, "AUT_DOC_HTML", "HTML", Arrays.asList(".htm", ".html")),
        AUT_DOC_OFFICE(1, "AUT_DOC_OFFICE", "Office", Arrays.asList(".doc", ".docx", 
                ".odt", ".xls", ".xlsx", ".ppt", ".pptx")),
        AUT_DOC_PDF(2, "AUT_DOC_PDF", "PDF", Arrays.asList(".pdf")),
        AUT_DOC_TXT(3, "AUT_DOC_TXT", "Plain Text", Arrays.asList(".txt")),
        AUT_DOC_RTF(4, "AUT_DOC_RTF", "Rich Text", Arrays.asList(".rtf"));

        int id;
        String name;
        String displayName;
        List<String> filter;

        private DocumentFilter(int id, String name, String displayName, List<String> filter){
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getName(){
            return this.name;
        }

        @Override
        public int getId(){
            return this.id;
        }

        @Override
        public String getDisplayName(){
            return this.displayName;
        }

        @Override
        public List<String> getFilter(){
            return this.filter;
        }
    }

    public SearchFilters(SleuthkitCase skCase){
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    public SleuthkitCase getSleuthkitCase(){
        return this.skCase;
    }
    
    interface SearchFilterInterface {
        public String getName();

        public int getId();

        public String getDisplayName();

        public List<String> getFilter();
    }
}
