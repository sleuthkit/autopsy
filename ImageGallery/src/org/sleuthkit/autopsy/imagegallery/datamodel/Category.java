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
package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.paint.Color;
import org.openide.util.NbBundle;

/**
 * Enum to represent the six categories in the DHS image categorization scheme.
 */
public enum Category {

    /*
     * This order of declaration is required so that Enum's compareTo method
     * preserves the fact that lower category numbers are first/most severe,
     * except 0 which is last
     */
    ONE(Color.RED, 1, NbBundle.getMessage(Category.class, "Category.cat1.childExploitationIllegal.displayName")),
    TWO(Color.ORANGE, 2, NbBundle.getMessage(Category.class, "Category.cat2.childExploitationNonIllegalAgeDifficult.displayName")),
    THREE(Color.YELLOW, 3, NbBundle.getMessage(Category.class, "Category.cat3.cgiAnimationChildExploitive.displayName")),
    FOUR(Color.BISQUE, 4, NbBundle.getMessage(Category.class, "Category.cat4.exemplarComparisonInternalUseOnly.displayName")),
    FIVE(Color.GREEN, 5, NbBundle.getMessage(Category.class, "Category.cat5.nonPertinent.displayName")),
    ZERO(Color.LIGHTGREY, 0, NbBundle.getMessage(Category.class, "Category.cat0.uncategorized.displayName"));

    public static ImmutableList<Category> getNonZeroCategories() {
        return nonZeroCategories;
    }

    private static final ImmutableList<Category> nonZeroCategories =
            ImmutableList.of(Category.FIVE, Category.FOUR, Category.THREE, Category.TWO, Category.ONE);

    /**
     * map from displayName to enum value
     */
    private static final Map<String, Category> nameMap =
            Stream.of(values()).collect(Collectors.toMap(
                            Category::getDisplayName,
                            Function.identity()));

    public static Category fromDisplayName(String displayName) {
        return nameMap.get(displayName);
    }

    public static boolean isCategoryName(String tName) {
        return nameMap.containsKey(tName);
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
