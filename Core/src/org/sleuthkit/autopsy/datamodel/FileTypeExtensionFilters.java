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

import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Filters database results by file extension.
 */
 class FileTypeExtensionFilters implements AutopsyVisitableItem {

    private SleuthkitCase skCase;

    // root node filters
    public enum RootFilter implements AutopsyVisitableItem,SearchFilterInterface {
        TSK_IMAGE_FILTER(0, "TSK_IMAGE_FILTER",
                         NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.tskImgFilter.text"),
                         FileTypeExtensions.getImageExtensions()),
        TSK_VIDEO_FILTER(1, "TSK_VIDEO_FILTER",
                         NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.tskVideoFilter.text"),
                         FileTypeExtensions.getVideoExtensions()),
        TSK_AUDIO_FILTER(2, "TSK_AUDIO_FILTER",
                         NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.tskAudioFilter.text"),
                         FileTypeExtensions.getAudioExtensions()),
        TSK_ARCHIVE_FILTER(3, "TSK_ARCHIVE_FILTER",
                           NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.tskArchiveFilter.text"),
                           FileTypeExtensions.getArchiveExtensions()),
        TSK_DOCUMENT_FILTER(3, "TSK_DOCUMENT_FILTER",
                            NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.tskDocumentFilter.text"),
                            Arrays.asList(".doc", ".docx", ".pdf", ".xls", ".rtf", ".txt")),
        TSK_EXECUTABLE_FILTER(3, "TSK_EXECUTABLE_FILTER",
                              NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.tskExecFilter.text"),
                              Arrays.asList(".exe", ".dll", ".bat", ".cmd", ".com"));

        private int id;
        private String name;
        private String displayName;
        private List<String> filter;

        private RootFilter(int id, String name, String displayName, List<String> filter){
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
    
    // document sub-node filters
    public enum DocumentFilter implements AutopsyVisitableItem,SearchFilterInterface {
        AUT_DOC_HTML(0, "AUT_DOC_HTML",
                     NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.autDocHtmlFilter.text"),
                     Arrays.asList(".htm", ".html")),
        AUT_DOC_OFFICE(1, "AUT_DOC_OFFICE",
                       NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.autDocOfficeFilter.text"),
                       Arrays.asList(".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx")),
        AUT_DOC_PDF(2, "AUT_DOC_PDF",
                    NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.autoDocPdfFilter.text"),
                    Arrays.asList(".pdf")),
        AUT_DOC_TXT(3, "AUT_DOC_TXT",
                    NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.autDocTxtFilter.text"),
                    Arrays.asList(".txt")),
        AUT_DOC_RTF(4, "AUT_DOC_RTF",
                    NbBundle.getMessage(FileTypeExtensionFilters.class, "FileTypeExtensionFilters.autDocRtfFilter.text"),
                    Arrays.asList(".rtf"));

        private int id;
        private String name;
        private String displayName;
        private List<String> filter;

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
    
    
    // executable sub-node filters
    public enum ExecutableFilter implements AutopsyVisitableItem,SearchFilterInterface {
        ExecutableFilter_EXE(0, "ExecutableFilter_EXE", ".exe", Arrays.asList(".exe")),
        ExecutableFilter_DLL(1, "ExecutableFilter_DLL", ".dll", Arrays.asList(".dll")),
        ExecutableFilter_BAT(2, "ExecutableFilter_BAT", ".bat", Arrays.asList(".bat")),
        ExecutableFilter_CMD(3, "ExecutableFilter_CMD", ".cmd", Arrays.asList(".cmd")),
        ExecutableFilter_COM(4, "ExecutableFilter_COM", ".com", Arrays.asList(".com"));
        
        private int id;
        private String name;
        private String displayName;
        private List<String> filter;

        private ExecutableFilter(int id, String name, String displayName, List<String> filter){
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

    public FileTypeExtensionFilters(SleuthkitCase skCase){
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
