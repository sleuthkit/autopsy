/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.openide.util.NbBundle.Messages;

/**
 *
 * A row result providing a category and a count for that category.
 */
@Messages({
    "CountsRowResultDTO_columns_displayName_name=displayName",
    "CountsRowResultDTO_columns_displayName_displayName=Name",
    "CountsRowResultDTO_columns_displayName_description=Name",
    "CountsRowResultDTO_columns_count_name=displayName",
    "CountsRowResultDTO_columns_count_displayName=Name",
    "CountsRowResultDTO_columns_count_description=Name"
})
public class CountsRowDTO<T> extends BaseRowDTO {
    private static final ColumnKey DISPLAY_NAME_COL = new ColumnKey(
            Bundle.CountsRowResultDTO_columns_displayName_name(),
            Bundle.CountsRowResultDTO_columns_displayName_displayName(),
            Bundle.CountsRowResultDTO_columns_displayName_description());

    private static final ColumnKey COUNT_COL = new ColumnKey(
            Bundle.CountsRowResultDTO_columns_count_name(),
            Bundle.CountsRowResultDTO_columns_count_displayName(),
            Bundle.CountsRowResultDTO_columns_count_description());

    private static final List<ColumnKey> DEFAULT_KEYS = ImmutableList.of(DISPLAY_NAME_COL, COUNT_COL);

    /**
     * @return The default column keys to be displayed for a counts row (display
     *         name and count).
     */
    public static List<ColumnKey> getDefaultColumnKeys() {
        return DEFAULT_KEYS;
    }

    private final String displayName;
    private final long count;
    private final T typeData;

    /**
     * Main constructor.
     *
     * @param typeId      The string id for the type of result.
     * @param typeData    Data for this particular row's type (i.e.
     *                    BlackboardArtifact.Type for counts of a particular
     *                    artifact type).
     * @param id          The numerical id of this row.
     * @param displayName The display name of this row.
     * @param count       The count of results for this row.
     */
    public CountsRowDTO(String typeId, T typeData, long id, String displayName, long count) {
        super(ImmutableList.of(Arrays.asList(displayName, count)), typeId, id);
        this.displayName = displayName;
        this.count = count;
        this.typeData = typeData;
    }

    /**
     * @return The display name of this row.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return The count of results for this row.
     */
    public long getCount() {
        return count;
    }

    /**
     *
     * @return Data for this particular row's type (i.e. BlackboardArtifact.Type
     *         for counts of a particular artifact type).
     */
    public T getTypeData() {
        return typeData;
    }
}
