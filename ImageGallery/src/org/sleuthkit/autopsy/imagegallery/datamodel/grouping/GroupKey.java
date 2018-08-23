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

import java.util.Map;
import java.util.Objects;
import javafx.scene.Node;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.datamodel.TagName;

/**
 * key identifying information of a {@link Grouping}. Used to look up groups in
 * {@link Map}s and from the db.
 */
@Immutable
public class GroupKey<T extends Comparable<T>> implements Comparable<GroupKey<T>> {

    private final T val;

    private final DrawableAttribute<T> attr;
    
    private final long dataSourceObjectId;

    public GroupKey(DrawableAttribute<T> attr, T val) {
       this(attr, val, 0);
    }
    
    public GroupKey(DrawableAttribute<T> attr, T val, long dataSourceObjId) {
        this.attr = attr;
        this.val = val;
        this.dataSourceObjectId = dataSourceObjId;
    }

    public T getValue() {
        return val;
    }

    public DrawableAttribute<T> getAttribute() {
        return attr;
    }

    public long getDataSourceObjId() {
        return dataSourceObjectId;
    }
    
    public String getValueDisplayName() {
        return Objects.equals(attr, DrawableAttribute.TAGS)
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
        hash = 29 * hash + Objects.hashCode(this.val);
        hash = 29 * hash + Objects.hashCode(this.attr);
        hash = 29 * hash + Objects.hashCode(this.dataSourceObjectId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GroupKey<?> other = (GroupKey<?>) obj;
        if (this.attr != other.attr) {
            return false;
        }
        if (this.dataSourceObjectId != other.dataSourceObjectId) {
            return false;
        }
        return Objects.equals(this.val, other.val);
    }

    @Override
    public int compareTo(GroupKey<T> o) {
        return val.compareTo(o.val);
    }

    public Node getGraphic() {
        return attr.getGraphicForValue(val);
    }
}
