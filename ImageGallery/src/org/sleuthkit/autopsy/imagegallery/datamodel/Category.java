/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.paint.Color;

/**
 * Enum to represent the six categories in the DHs image categorization scheme.
 */
public enum Category {

    /* This order of declaration is required so that Enum's compareTo method
     * preserves the fact that lower category numbers are first/most sever,
     * except 0 which is last */
    ONE(Color.RED, 1, "CAT-1:  Child Exploitation (Illegal)"),
    TWO(Color.ORANGE, 2, "CAT-2: Child Exploitation (Non-Illegal/Age Difficult)"),
    THREE(Color.YELLOW, 3, "CAT-3: CGI/Animation (Child Exploitive)"),
    FOUR(Color.BISQUE, 4, "CAT-4:  Exemplar/Comparison (Internal Use Only)"),
    FIVE(Color.GREEN, 5, "CAT-5: Non-pertinent"),
    ZERO(Color.LIGHTGREY, 0, "CAT-0: Uncategorized");

    /** map from displayName to enum value */
    private static final Map<String, Category> nameMap
            = Stream.of(values()).collect(Collectors.toMap(
                            Category::getDisplayName,
                            Function.identity()));

    public static Category fromDisplayName(String displayName) {
        return nameMap.get(displayName);
    }
    public static boolean isCategoryName(String tName) {
        return nameMap.containsKey(tName);

    public static boolean isNotCategoryName(String tName) {
        return nameMap.containsKey(tName) == false;
    }

    public static boolean isNotCategoryName(String tName) {
        return nameMap.containsKey(tName) == false;
    }

    private final Color color;

    private final String displayName;

    private final int id;

    private Category(Color color, int id, String name) {
        this.color = color;
        this.displayName = name;
        this.id = id;
    }

    public int getCategoryNumber() {
        return id;
    }

    public Color getColor() {
        return color;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

}
