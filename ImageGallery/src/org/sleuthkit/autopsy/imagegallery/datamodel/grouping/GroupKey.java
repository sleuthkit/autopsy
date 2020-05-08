/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel.grouping;

import java.util.Objects;
import java.util.Optional;
import javafx.scene.Node;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TagName;

/**
 * Key identifying information of a DrawableGroup. Used to look up groups in
 * Maps and from the db.
 *
 * @param <T> The type of the values of the attribute this key uses.
 */
@Immutable
public class GroupKey<T extends Comparable<T>> implements Comparable<GroupKey<T>> {

    private final T val;
    private final DrawableAttribute<T> attr;
    private final DataSource dataSource;

    public GroupKey(DrawableAttribute<T> attr, T val, DataSource dataSource) {
        this.attr = attr;
        this.val = val;
        this.dataSource = dataSource;
    }

    public T getValue() {
        return val;
    }

    public DrawableAttribute<T> getAttribute() {
        return attr;
    }

    public Optional< DataSource> getDataSource() {
        return Optional.ofNullable(dataSource);
    }

    public String getValueDisplayName() {
        return Objects.equals(attr, DrawableAttribute.TAGS)
                || Objects.equals(attr, DrawableAttribute.CATEGORY)
                ? ((TagName) getValue()).getDisplayName()
                : Objects.toString(getValue(), "unknown");
    }

    @Override
    public String toString() {
        return "GroupKey: " + getAttribute().attrName + " = " + getValue(); //NON-NLS
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 79 * hash + Objects.hashCode(this.val);
        hash = 79 * hash + Objects.hashCode(this.attr);
        if (this.dataSource != null) {
            hash = 79 * hash + (int) this.dataSource.getId();
        }

        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GroupKey<?> other = (GroupKey<?>) obj;
        if (!Objects.equals(this.val, other.val)) {
            return false;
        }

        if (!Objects.equals(this.attr, other.attr)) {
            return false;
        }

        // Data source is significant only for PATH based groups.
        if (this.attr == DrawableAttribute.PATH) {
            if (this.dataSource != null && other.dataSource != null) {
                return this.dataSource.getId() == other.dataSource.getId();
            } else if (this.dataSource == null && other.dataSource == null) {
                // neither group has a datasource
                return true;
            } else {
                // one group has a datasource, other doesn't
                return false;
            }
        }

        return true;
    }

    @Override
    public int compareTo(GroupKey<T> o) {
        return val.compareTo(o.val);
    }

    public Node getGraphic() {
        return attr.getGraphicForValue(val);
    }

    public long getDataSourceObjId() {
        return getDataSource().map(DataSource::getId).orElse(0L);
    }
}
