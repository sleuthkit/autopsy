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

    public enum FileSearchFilter implements AutopsyVisitableItem {
        TSK_IMAGE_FILTER(0, "TSK_IMAGE_FILTER", "Images", Arrays.asList(".jpg", ".jpeg", ".png", ".psd", ".nef")),
        TSK_VIDEO_FILTER(1, "TSK_VIDEO_FILTER", "Videos", Arrays.asList(".mov", ".avi", ".m4v")),
        TSK_AUDIO_FILTER(2, "TSK_AUDIO_FILTER", "Audio", Arrays.asList(".mp3", ".aac", ".wav", ".ogg", ".wma", ".m4a")),
        TSK_DOCUMENT_FILTER(3, "TSK_DOCUMENT_FILTER", "Documents", Arrays.asList(".doc", ".docx", ".pdf", ".xls"));

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

        public String getName(){
            return this.name;
        }

        public int getId(){
            return this.id;
        }

        public String getDisplayName(){
            return this.displayName;
        }

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
}
