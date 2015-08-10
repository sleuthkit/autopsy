/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.events.db;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.timeline.events.type.RootEventType;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.DataSourceFilter;
import org.sleuthkit.autopsy.timeline.filters.DataSourcesFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;
import org.sleuthkit.autopsy.timeline.filters.HashHitsFilter;
import org.sleuthkit.autopsy.timeline.filters.HashSetFilter;
import org.sleuthkit.autopsy.timeline.filters.HideKnownFilter;
import org.sleuthkit.autopsy.timeline.filters.IntersectionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TagNameFilter;
import org.sleuthkit.autopsy.timeline.filters.TagsFilter;
import org.sleuthkit.autopsy.timeline.filters.TextFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.filters.UnionFilter;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import static org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD.FULL;
import static org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD.MEDIUM;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.DAYS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.HOURS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.MINUTES;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.MONTHS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.SECONDS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.YEARS;
import org.sleuthkit.datamodel.TskData;

/**
 * Static helper methods for converting between java data model objects and
 * sqlite queries.
 */
public class SQLHelper {

    static String useHashHitTablesHelper(RootFilter filter) {
        HashHitsFilter hashHitFilter = filter.getHashHitsFilter();
        return hashHitFilter.isSelected() && false == hashHitFilter.isDisabled() ? ", hash_set_hits" : "";
    }

    static String useTagTablesHelper(RootFilter filter) {
        TagsFilter tagsFilter = filter.getTagsFilter();
        return tagsFilter.isSelected() && false == tagsFilter.isDisabled() ? ", content_tags, blackboard_artifact_tags " : "";
    }

    static <X> Set<X> unGroupConcat(String s, Function<String, X> mapper) {
        return Stream.of(s.split(","))
                .map(mapper::apply)
                .collect(Collectors.toSet());
    }

    private static String getSQLWhere(IntersectionFilter<?> filter) {
        return filter.getSubFilters().stream()
                .filter(Filter::isSelected)
                .map(SQLHelper::getSQLWhere)
                .collect(Collectors.joining(" and ", "( ", ")"));
    }

    private static String getSQLWhere(UnionFilter<?> filter) {
        return filter.getSubFilters().stream()
                .filter(Filter::isSelected).map(SQLHelper::getSQLWhere)
                .collect(Collectors.joining(" or ", "( ", ")"));
    }

    static String getSQLWhere(RootFilter filter) {
        return getSQLWhere((IntersectionFilter) filter);
    }

    private static String getSQLWhere(Filter filter) {
        String result = "";
        if (filter == null) {
            return "1";
        } else if (filter instanceof TagsFilter) {
            result = getSQLWhere((TagsFilter) filter);
        } else if (filter instanceof HashHitsFilter) {
            result = getSQLWhere((HashHitsFilter) filter);
        } else if (filter instanceof DataSourceFilter) {
            result = getSQLWhere((DataSourceFilter) filter);
        } else if (filter instanceof DataSourcesFilter) {
            result = getSQLWhere((DataSourcesFilter) filter);
        } else if (filter instanceof HideKnownFilter) {
            result = getSQLWhere((HideKnownFilter) filter);
        } else if (filter instanceof HashHitsFilter) {
            result = getSQLWhere((HashHitsFilter) filter);
        } else if (filter instanceof TextFilter) {
            result = getSQLWhere((TextFilter) filter);
        } else if (filter instanceof TypeFilter) {
            result = getSQLWhere((TypeFilter) filter);
        } else if (filter instanceof IntersectionFilter) {
            result = getSQLWhere((IntersectionFilter) filter);
        } else if (filter instanceof UnionFilter) {
            result = getSQLWhere((UnionFilter) filter);
        } else {
            return "1";
        }
        result = StringUtils.deleteWhitespace(result).equals("(1and1and1)") ? "1" : result;
        result = StringUtils.deleteWhitespace(result).equals("()") ? "1" : result;
        return result;
    }

    private static String getSQLWhere(HideKnownFilter filter) {
        if (filter.isSelected()) {
            return "(known_state IS NOT '" + TskData.FileKnown.KNOWN.getFileKnownValue() + "')"; // NON-NLS
        } else {
            return "1";
        }
    }

    private static String getSQLWhere(TagsFilter filter) {
        if (filter.isSelected()
                && (false == filter.isDisabled())
                && (filter.getSubFilters().isEmpty() == false)) {
            String tagNameIDs = filter.getSubFilters().stream()
                    .filter((TagNameFilter t) -> t.isSelected() && !t.isDisabled())
                    .map((TagNameFilter t) -> String.valueOf(t.getTagName().getId()))
                    .collect(Collectors.joining(", ", "(", ")"));
            return "((blackboard_artifact_tags.artifact_id == events.artifact_id AND blackboard_artifact_tags.tag_name_id IN " + tagNameIDs + ") "
                    + "OR ( content_tags.obj_id == events.file_id  AND content_tags.tag_name_id IN " + tagNameIDs + "))";
        } else {
            return "1";
        }
    }

    private static String getSQLWhere(HashHitsFilter filter) {
        if (filter.isSelected()
                && (false == filter.isDisabled())
                && (filter.getSubFilters().isEmpty() == false)) {
            String hashSetIDs = filter.getSubFilters().stream()
                    .filter((HashSetFilter t) -> t.isSelected() && !t.isDisabled())
                    .map((HashSetFilter t) -> String.valueOf(t.getHashSetID()))
                    .collect(Collectors.joining(", ", "(", ")"));
            return "(hash_set_hits.hash_set_id IN " + hashSetIDs + " AND hash_set_hits.event_id == events.event_id)";
        } else {
            return "1";
        }
    }

    private static String getSQLWhere(DataSourceFilter filter) {
        return (filter.isSelected()) ? "(datasource_id = '" + filter.getDataSourceID() + "')" : "1";
    }

    private static String getSQLWhere(DataSourcesFilter filter) {
        return (filter.isSelected()) ? "(datasource_id in ("
                + filter.getSubFilters().stream()
                .filter(AbstractFilter::isSelected)
                .map((dataSourceFilter) -> String.valueOf(dataSourceFilter.getDataSourceID()))
                .collect(Collectors.joining(", ")) + "))" : "1";
    }

    private static String getSQLWhere(TextFilter filter) {
        if (filter.isSelected()) {
            if (StringUtils.isBlank(filter.getText())) {
                return "1";
            }
            String strippedFilterText = StringUtils.strip(filter.getText());
            return "((med_description like '%" + strippedFilterText + "%')"
                    + " or (full_description like '%" + strippedFilterText + "%')"
                    + " or (short_description like '%" + strippedFilterText + "%'))";
        } else {
            return "1";
        }
    }

    /**
     * generate a sql where clause for the given type filter, while trying to be
     * as simple as possible to improve performance.
     *
     * @param typeFilter
     *
     * @return
     */
    private static String getSQLWhere(TypeFilter typeFilter) {
        if (typeFilter.isSelected() == false) {
            return "0";
        } else if (typeFilter.getEventType() instanceof RootEventType) {
            if (typeFilter.getSubFilters().stream()
                    .allMatch(subFilter -> subFilter.isSelected() && subFilter.getSubFilters().stream().allMatch(Filter::isSelected))) {
                return "1"; //then collapse clause to true
            }
        }
        return "(sub_type IN (" + StringUtils.join(getActiveSubTypes(typeFilter), ",") + "))";
    }

    private static List<Integer> getActiveSubTypes(TypeFilter filter) {
        if (filter.isSelected()) {
            if (filter.getSubFilters().isEmpty()) {
                return Collections.singletonList(RootEventType.allTypes.indexOf(filter.getEventType()));
            } else {
                return filter.getSubFilters().stream().flatMap((Filter t) -> getActiveSubTypes((TypeFilter) t).stream()).collect(Collectors.toList());
            }
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * get a sqlite strftime format string that will allow us to group by the
     * requested period size. That is, with all info more granular that that
     * requested dropped (replaced with zeros).
     *
     * @param info the {@link RangeDivisionInfo} with the requested period size
     *
     * @return a String formatted according to the sqlite strftime spec
     *
     * @see https://www.sqlite.org/lang_datefunc.html
     */
    static String getStrfTimeFormat(@Nonnull RangeDivisionInfo info) {
        switch (info.getPeriodSize()) {
            case YEARS:
                return "%Y-01-01T00:00:00"; // NON-NLS
            case MONTHS:
                return "%Y-%m-01T00:00:00"; // NON-NLS
            case DAYS:
                return "%Y-%m-%dT00:00:00"; // NON-NLS
            case HOURS:
                return "%Y-%m-%dT%H:00:00"; // NON-NLS
            case MINUTES:
                return "%Y-%m-%dT%H:%M:00"; // NON-NLS
            case SECONDS:
            default:    //seconds - should never happen
                return "%Y-%m-%dT%H:%M:%S"; // NON-NLS  
        }
    }

    static String getDescriptionColumn(DescriptionLOD lod) {
        switch (lod) {
            case FULL:
                return "full_description";
            case MEDIUM:
                return "med_description";
            case SHORT:
            default:
                return "short_description";
        }
    }

    private SQLHelper() {
    }
}
