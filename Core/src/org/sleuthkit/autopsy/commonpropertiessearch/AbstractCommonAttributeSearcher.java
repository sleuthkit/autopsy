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
package org.sleuthkit.autopsy.commonpropertiessearch;

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
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Prototype for an object which finds files with common attributes. Subclass
 * this and implement findMatchesByCount in order
 */
public abstract class AbstractCommonAttributeSearcher {

    private boolean filterByMedia;
    private boolean filterByDoc;
    final int frequencyPercentageThreshold;

    AbstractCommonAttributeSearcher(boolean filterByMedia, boolean filterByDoc, int percentageThreshold) {
        this.filterByDoc = filterByDoc;
        this.filterByMedia = filterByMedia;
        this.frequencyPercentageThreshold = percentageThreshold;
    }

    /**
     * Implement this to search for files with common attributes. Creates an
     * object (CommonAttributeCountSearchResults) which contains all of the
     * information required to display a tree view in the UI. The view will
     * contain 3 layers: a top level node, indicating the number matches each of
     * it's children possess, a mid level node indicating the matched attribute,
     *
     * @return
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException
     * @throws CentralRepoException
     */
    public abstract CommonAttributeCountSearchResults findMatchesByCount() throws TskCoreException, NoCurrentCaseException, SQLException, CentralRepoException;

    /**
     * Implement this to search for files with common attributes. Creates an
     * object (CommonAttributeCountSearchResults) which contains all of the
     * information required to display a tree view in the UI. The view will
     * contain 3 layers: a top level node, indicating the name of the case the
     * results were found in, a mid level node indicating what data source the
     * match was found in, and a bottom level which shows the files the match
     * was found in
     *
     * @return An object containing the results of the search
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException
     * @throws CentralRepoException
     */
    public abstract CommonAttributeCaseSearchResults findMatchesByCase() throws TskCoreException, NoCurrentCaseException, SQLException, CentralRepoException;

    /**
     * Implement this to create a descriptive string for the tab which will
     * display this data.
     *
     * @return an informative string
     */
    abstract String getTabTitle();

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

    /**
     * Get the portion of the title that will display the frequency percentage
     * threshold. Items that existed in over this percent of data sources were
     * ommited from the results.
     *
     * @return A string providing the frequency percentage threshold, or an
     *         empty string if no threshold was set
     */
    @NbBundle.Messages({
        "# {0} - threshold percent",
        "AbstractCommonFilesMetadataBuilder.getPercentFilter.thresholdPercent=, Threshold {0}%"})
    String getPercentThresholdString() {
        if (frequencyPercentageThreshold == 0) {
            return "";
        } else {
            return Bundle.AbstractCommonFilesMetadataBuilder_getPercentFilter_thresholdPercent(frequencyPercentageThreshold);
        }
    }

    static TreeMap<Integer, CommonAttributeValueList> collateMatchesByNumberOfInstances(Map<String, CommonAttributeValue> commonFiles) {
        //collate matches by number of matching instances - doing this in sql doesnt seem efficient
        TreeMap<Integer, CommonAttributeValueList> instanceCollatedCommonFiles = new TreeMap<>();

        for (CommonAttributeValue md5Metadata : commonFiles.values()) {
            Integer size = md5Metadata.getNumberOfDataSourcesInCurrentCase();

            if (instanceCollatedCommonFiles.containsKey(size)) {
                instanceCollatedCommonFiles.get(size).addMetadataToList(md5Metadata);
            } else {
                CommonAttributeValueList value = new CommonAttributeValueList();
                value.addMetadataToList(md5Metadata);
                instanceCollatedCommonFiles.put(size, value);
            }
        }
        return instanceCollatedCommonFiles;
    }

    /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_MEDIA. ".jpg", ".jpeg", ".png", ".psd", ".nef",
     * ".tiff", ".bmp", ".tec" ".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v",
     * //NON-NLS ".m4v", ".mp4", ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm",
     * ".wmv", ".mpv", ".flv", ".swf"
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
     * when checkType is ONLY_TEXT_FILES. ".doc", ".docx", ".odt", ".xls",
     * ".xlsx", ".ppt", ".pptx" ".txt", ".rtf", ".log", ".text", ".xml" ".html",
     * ".htm", ".css", ".js", ".php", ".aspx" ".pdf"
     * //ignore text/plain due to large number of results with that type 
     */
    static final Set<String> TEXT_FILES_MIME_TYPES = Stream.of(
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
