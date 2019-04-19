/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.db;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.RootEventType;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.DataSourceFilter;
import org.sleuthkit.autopsy.timeline.filters.DataSourcesFilter;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
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
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import static org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD.FULL;
import static org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD.MEDIUM;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.DAYS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.HOURS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.MINUTES;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.MONTHS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.SECONDS;
import static org.sleuthkit.autopsy.timeline.zooming.TimeUnits.YEARS;
import org.sleuthkit.datamodel.TskData;

/**
 * Static helper methods for converting between java "data model" objects and
 * sqlite queries.
 */
class SQLHelper {

    static String useHashHitTablesHelper(RootFilter filter) {
        HashHitsFilter hashHitFilter = filter.getHashHitsFilter();
        return hashHitFilter.isActive() ? " LEFT JOIN hash_set_hits " : " "; //NON-NLS
    }

    static String useTagTablesHelper(RootFilter filter) {
        TagsFilter tagsFilter = filter.getTagsFilter();
        return tagsFilter.isActive() ? " LEFT JOIN tags " : " "; //NON-NLS
    }

    /**
     * take the result of a group_concat SQLite operation and split it into a
     * set of X using the mapper to to convert from string to X
     *
     * @param <X>         the type of elements to return
     * @param groupConcat a string containing the group_concat result ( a comma
     *                    separated list)
     * @param mapper      a function from String to X
     *
     * @return a Set of X, each element mapped from one element of the original
     *         comma delimited string
     */
    static <X> List<X> unGroupConcat(String groupConcat, Function<String, X> mapper) {
        return StringUtils.isBlank(groupConcat) ? Collections.emptyList()
                : Stream.of(groupConcat.split(","))
                .map(mapper::apply)
                .collect(Collectors.toList());
    }

    /**
     * get the SQL where clause corresponding to an intersection filter ie
     * (sub-clause1 and sub-clause2 and ... and sub-clauseN)
     *
     * @param filter the filter get the where clause for
     *
     * @return an SQL where clause (without the "where") corresponding to the
     *         filter
     */
    private static String getSQLWhere(IntersectionFilter<?> filter) {
        String join = String.join(" and ", filter.getSubFilters().stream()
                .filter(Filter::isActive)
                .map(SQLHelper::getSQLWhere)
                .collect(Collectors.toList()));
        return "(" + StringUtils.defaultIfBlank(join, "1") + ")";
    }

    /**
     * get the SQL where clause corresponding to a union filter ie (sub-clause1
     * or sub-clause2 or ... or sub-clauseN)
     *
     * @param filter the filter get the where clause for
     *
     * @return an SQL where clause (without the "where") corresponding to the
     *         filter
     */
    private static String getSQLWhere(UnionFilter<?> filter) {
        String join = String.join(" or ", filter.getSubFilters().stream()
                .filter(Filter::isActive)
                .map(SQLHelper::getSQLWhere)
                .collect(Collectors.toList()));
        return "(" + StringUtils.defaultIfBlank(join, "1") + ")";
    }

    static String getSQLWhere(RootFilter filter) {
        return getSQLWhere((Filter) filter);
    }

    /**
     * get the SQL where clause corresponding to the given filter
     *
     * uses instance of to dispatch to the correct method for each filter type.
     * NOTE: I don't like this if-else instance of chain, but I can't decide
     * what to do instead -jm
     *
     * @param filter a filter to generate the SQL where clause for
     *
     * @return an SQL where clause (without the "where") corresponding to the
     *         filter
     */
    private static String getSQLWhere(Filter filter) {
        String result = "";
        if (filter == null) {
            return "1";
        } else if (filter instanceof DescriptionFilter) {
            result = getSQLWhere((DescriptionFilter) filter);
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
            throw new IllegalArgumentException("getSQLWhere not defined for " + filter.getClass().getCanonicalName());
        }
        result = StringUtils.deleteWhitespace(result).equals("(1and1and1)") ? "1" : result; //NON-NLS
        result = StringUtils.deleteWhitespace(result).equals("()") ? "1" : result;
        return result;
    }

    private static String getSQLWhere(HideKnownFilter filter) {
        if (filter.isActive()) {
            return "(known_state IS NOT '" + TskData.FileKnown.KNOWN.getFileKnownValue() + "')"; // NON-NLS
        } else {
            return "1";
        }
    }

    private static String getSQLWhere(DescriptionFilter filter) {
        if (filter.isActive()) {
            String likeOrNotLike = (filter.getFilterMode() == DescriptionFilter.FilterMode.INCLUDE ? "" : " NOT") + " LIKE '"; //NON-NLS
            return "(" + getDescriptionColumn(filter.getDescriptionLoD()) + likeOrNotLike + filter.getDescription() + "'  )"; // NON-NLS
        } else {
            return "1";
        }
    }

    private static String getSQLWhere(TagsFilter filter) {
        if (filter.isActive()
                && (filter.getSubFilters().isEmpty() == false)) {
            String tagNameIDs = filter.getSubFilters().stream()
                    .filter((TagNameFilter t) -> t.isSelected() && !t.isDisabled())
                    .map((TagNameFilter t) -> String.valueOf(t.getTagName().getId()))
                    .collect(Collectors.joining(", ", "(", ")"));
            return "(events.event_id == tags.event_id AND " //NON-NLS
                    + "tags.tag_name_id IN " + tagNameIDs + ") "; //NON-NLS
        } else {
            return "1";
        }

    }

    private static String getSQLWhere(HashHitsFilter filter) {
        if (filter.isActive()
                && (filter.getSubFilters().isEmpty() == false)) {
            String hashSetIDs = filter.getSubFilters().stream()
                    .filter((HashSetFilter t) -> t.isSelected() && !t.isDisabled())
                    .map((HashSetFilter t) -> String.valueOf(t.getHashSetID()))
                    .collect(Collectors.joining(", ", "(", ")"));
            return "(hash_set_hits.hash_set_id IN " + hashSetIDs + " AND hash_set_hits.event_id == events.event_id)"; //NON-NLS
        } else {
            return "1";
        }
    }

    private static String getSQLWhere(DataSourceFilter filter) {
        if (filter.isActive()) {
            return "(datasource_id = '" + filter.getDataSourceID() + "')"; //NON-NLS
        } else {
            return "1";
        }
    }

    private static String getSQLWhere(DataSourcesFilter filter) {
        return (filter.isActive()) ? "(datasource_id in (" //NON-NLS
                + filter.getSubFilters().stream()
                .filter(AbstractFilter::isActive)
                .map((dataSourceFilter) -> String.valueOf(dataSourceFilter.getDataSourceID()))
                .collect(Collectors.joining(", ")) + "))" : "1";
    }

    private static String getSQLWhere(TextFilter filter) {
        if (filter.isActive()) {
            if (StringUtils.isBlank(filter.getText())) {
                return "1";
            }
            String strippedFilterText = StringUtils.strip(filter.getText());
            return "((med_description like '%" + strippedFilterText + "%')" //NON-NLS
                    + " or (full_description like '%" + strippedFilterText + "%')" //NON-NLS
                    + " or (short_description like '%" + strippedFilterText + "%'))"; //NON-NLS
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
                    .allMatch(subFilter -> subFilter.isActive() && subFilter.getSubFilters().stream().allMatch(Filter::isActive))) {
                return "1"; //then collapse clause to true
            }
        }
        return "(sub_type IN (" + StringUtils.join(getActiveSubTypes(typeFilter), ",") + "))"; //NON-NLS
    }

    private static List<Integer> getActiveSubTypes(TypeFilter filter) {
        if (filter.isActive()) {
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
     * requested period size. That is, with all info more granular than that
     * requested dropped (replaced with zeros).
     *
     * @param timeUnit the {@link TimeUnits} instance describing what
     *                 granularity to build a strftime string for
     *
     * @return a String formatted according to the sqlite strftime spec
     *
     * @see https://www.sqlite.org/lang_datefunc.html
     */
    static String getStrfTimeFormat(@Nonnull TimeUnits timeUnit) {
        switch (timeUnit) {
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

    static String getDescriptionColumn(DescriptionLoD lod) {
        switch (lod) {
            case FULL:
                return "full_description"; //NON-NLS
            case MEDIUM:
                return "med_description"; //NON-NLS
            case SHORT:
            default:
                return "short_description"; //NON-NLS
        }
    }

    private SQLHelper() {
    }
}
