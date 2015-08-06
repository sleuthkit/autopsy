/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.events.db;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
public class SQLHelper {

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

    static boolean hasActiveHashFilter(RootFilter filter) {
        HashHitsFilter hashHitFilter = filter.getHashHitsFilter();
        return hashHitFilter.isSelected() && false == hashHitFilter.isDisabled();
    }

    static boolean hasActiveTagFilter(RootFilter filter) {
        TagsFilter tagsFilter = filter.getTagsFilter();
        return tagsFilter.isSelected() && false == tagsFilter.isDisabled();
    }

    private SQLHelper() {
    }

    static String getSQLWhere(IntersectionFilter<?> filter) {
        return filter.getSubFilters().stream().filter(Filter::isSelected).map(SQLHelper::getSQLWhere).collect(Collectors.joining(" and ", "( ", ")"));
    }

    static String getSQLWhere(UnionFilter<?> filter) {
        return filter.getSubFilters().stream().filter(Filter::isSelected).map(SQLHelper::getSQLWhere).collect(Collectors.joining(" or ", "( ", ")"));
    }

    static String getSQLWhere(Filter filter) {
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

    static String getSQLWhere(HideKnownFilter filter) {
        if (filter.isSelected()) {
            return "(known_state IS NOT '" + TskData.FileKnown.KNOWN.getFileKnownValue() + "')"; // NON-NLS
        } else {
            return "1";
        }
    }

    static String getSQLWhere(TagsFilter filter) {
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

    static String getSQLWhere(HashHitsFilter filter) {
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

    static String getSQLWhere(DataSourceFilter filter) {
        return (filter.isSelected()) ? "(datasource_id = '" + filter.getDataSourceID() + "')" : "1";
    }

    static String getSQLWhere(DataSourcesFilter filter) {
        return (filter.isSelected()) ? "(datasource_id in ("
                + filter.getSubFilters().stream()
                .filter(AbstractFilter::isSelected)
                .map((dataSourceFilter) -> String.valueOf(dataSourceFilter.getDataSourceID()))
                .collect(Collectors.joining(", ")) + "))" : "1";
    }

    static String getSQLWhere(TextFilter filter) {
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
    static String getSQLWhere(TypeFilter typeFilter) {
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

}
