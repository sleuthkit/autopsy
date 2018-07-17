/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Prototype for an object which finds files with common attributes.
 * Subclass this and implement findFiles in order 
 */
abstract class AbstractCommonAttributeSearcher {
    
    private boolean filterByMedia;
    private boolean filterByDoc;
    
    AbstractCommonAttributeSearcher(boolean filterByMedia, boolean filterByDoc){
        this.filterByDoc = filterByDoc;
        this.filterByMedia = filterByMedia;
    }
    
    abstract CommonAttributeSearchResults findFiles() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException;
    
    @NbBundle.Messages({
        "AbstractCommonFilesMetadataBuilder.buildTabTitle.titleIntraAll=Common Files (All Data Sources, %s)",
        "AbstractCommonFilesMetadataBuilder.buildTabTitle.titleIntraSingle=Common Files (Data Source: %s, %s)",
        "AbstractCommonFilesMetadataBuilder.buildTabTitle.titleInterAll=Common Files (All Central Repository Cases, %s)",
        "AbstractCommonFilesMetadataBuilder.buildTabTitle.titleInterSingle=Common Files (Central Repository Case: %s, %s)",
    })
    abstract String buildTabTitle();
    
    @NbBundle.Messages({
        "AbstractCommonFilesMetadataBuilder.buildCategorySelectionString.doc=Documents",
        "AbstractCommonFilesMetadataBuilder.buildCategorySelectionString.media=Media",
        "AbstractCommonFilesMetadataBuilder.buildCategorySelectionString.all=All File Categories"
    })
    String buildCategorySelectionString() {
        if (!this.isFilterByDoc() && !this.isFilterByMedia()) {
            return Bundle.AbstractCommonFilesMetadataBuilder_buildCategorySelectionString_all();
        } else {
            List<String> filters = new ArrayList<>();
            if (this.isFilterByDoc()) {
                filters.add(Bundle.AbstractCommonFilesMetadataBuilder_buildCategorySelectionString_doc());
            }
            if (this.isFilterByMedia()) {
                filters.add(Bundle.AbstractCommonFilesMetadataBuilder_buildCategorySelectionString_media());
            }
            return String.join(", ", filters);
        }
    }
    
    static Map<Integer, List<CommonAttributeValue>> collateMatchesByNumberOfInstances(Map<String, CommonAttributeValue> commonFiles) {
        //collate matches by number of matching instances - doing this in sql doesnt seem efficient
        Map<Integer, List<CommonAttributeValue>> instanceCollatedCommonFiles = new TreeMap<>();
        for(CommonAttributeValue md5Metadata : commonFiles.values()){
            Integer size = md5Metadata.size();
            
            if(instanceCollatedCommonFiles.containsKey(size)){
                instanceCollatedCommonFiles.get(size).add(md5Metadata);
            } else {
                ArrayList<CommonAttributeValue> value = new ArrayList<>();
                value.add(md5Metadata);
                instanceCollatedCommonFiles.put(size, value);
            }
        }
        return instanceCollatedCommonFiles;
    }
    
    /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_MEDIA.
     * ".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff", ".bmp", ".tec"
     * ".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v", //NON-NLS
     * ".m4v", ".mp4", ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm", ".wmv", ".mpv", ".flv", ".swf"
     */
    static final Set<String> MEDIA_PICS_VIDEO_MIME_TYPES = Stream.of(
            "image/bmp", //NON-NLS
            "image/gif", //NON-NLS
            "image/jpeg", //NON-NLS
            "image/png", //NON-NLS
            "image/tiff", //NON-NLS
            "image/vnd.adobe.photoshop", //NON-NLS
            "image/x-raw-nikon", //NON-NLS
            "image/x-ms-bmp", //NON-NLS
            "image/x-icon", //NON-NLS
            "video/webm", //NON-NLS
            "video/3gpp", //NON-NLS
            "video/3gpp2", //NON-NLS
            "video/ogg", //NON-NLS
            "video/mpeg", //NON-NLS
            "video/mp4", //NON-NLS
            "video/quicktime", //NON-NLS
            "video/x-msvideo", //NON-NLS
            "video/x-flv", //NON-NLS
            "video/x-m4v", //NON-NLS
            "video/x-ms-wmv", //NON-NLS
            "application/vnd.ms-asf", //NON-NLS
            "application/vnd.rn-realmedia", //NON-NLS
            "application/x-shockwave-flash" //NON-NLS
    ).collect(Collectors.toSet());

    /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_TEXT_FILES.
     * ".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx"
     * ".txt", ".rtf", ".log", ".text", ".xml"
     * ".html", ".htm", ".css", ".js", ".php", ".aspx"
     * ".pdf"
     */
    static final Set<String> TEXT_FILES_MIME_TYPES = Stream.of(
            "text/plain", //NON-NLS
            "application/rtf", //NON-NLS
            "application/pdf", //NON-NLS
            "text/css", //NON-NLS
            "text/html", //NON-NLS
            "text/csv", //NON-NLS
            "application/json", //NON-NLS
            "application/javascript", //NON-NLS
            "application/xml", //NON-NLS
            "text/calendar", //NON-NLS
            "application/x-msoffice", //NON-NLS
            "application/x-ooxml", //NON-NLS
            "application/msword", //NON-NLS
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //NON-NLS
            "application/vnd.ms-powerpoint", //NON-NLS
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", //NON-NLS
            "application/vnd.ms-excel", //NON-NLS
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", //NON-NLS
            "application/vnd.oasis.opendocument.presentation", //NON-NLS
            "application/vnd.oasis.opendocument.spreadsheet", //NON-NLS
            "application/vnd.oasis.opendocument.text" //NON-NLS
    ).collect(Collectors.toSet());

    /**
     * @return the filterByMedia
     */
    boolean isFilterByMedia() {
        return filterByMedia;
    }

    /**
     * @param filterByMedia the filterByMedia to set
     */
    void setFilterByMedia(boolean filterByMedia) {
        this.filterByMedia = filterByMedia;
    }

    /**
     * @return the filterByDoc
     */
    boolean isFilterByDoc() {
        return filterByDoc;
    }

    /**
     * @param filterByDoc the filterByDoc to set
     */
    void setFilterByDoc(boolean filterByDoc) {
        this.filterByDoc = filterByDoc;
    }
}
