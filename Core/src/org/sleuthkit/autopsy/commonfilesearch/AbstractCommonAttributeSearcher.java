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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Prototype for an object which finds files with common attributes. Subclass
 * this and implement findMatches in order
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
     * object (CommonAttributeSearchResults) which contains all of the
     * information required to display a tree view in the UI. The view will
     * contain 3 layers: a top level node, indicating the number matches each of
     * it's children possess, a mid level node indicating the matched attribute,
     *
     * @return
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException
     * @throws EamDbException
     */
    public abstract CommonAttributeSearchResults findMatches() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException;

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

    static Map<Integer, CommonAttributeValueList> collateMatchesByNumberOfInstances(Map<String, CommonAttributeValue> commonFiles) {
        //collate matches by number of matching instances - doing this in sql doesnt seem efficient
        Map<Integer, CommonAttributeValueList> instanceCollatedCommonFiles = new TreeMap<>();

        for (CommonAttributeValue md5Metadata : commonFiles.values()) {
            Integer size = md5Metadata.getInstanceCount();

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
    
    
    Set<String> getMimeTypesToFilterOn() {
        Set<String> mimeTypesToFilterOn = new HashSet<>();
        if (isFilterByMedia()) {
            mimeTypesToFilterOn.addAll(FileTypeUtils.FileTypeCategory.VISUAL.getMediaTypes());
        }
        if (isFilterByDoc()) {
            mimeTypesToFilterOn.addAll(FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes());
        }
        return mimeTypesToFilterOn;
    }
}
