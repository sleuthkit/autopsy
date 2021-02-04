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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import org.openide.util.NbBundle;

/**
 * Enum to represent the six categories in the DHS image categorization scheme.
 * NOTE: This appears to not be used anywhere anymore after the ImageGallery refactoring
 */
@NbBundle.Messages({
    "Category.one=CAT-1: Child Exploitation (Illegal)",
    "Category.two=CAT-2: Child Exploitation (Non-Illegal/Age Difficult)",
    "Category.three=CAT-3: CGI/Animation (Child Exploitive)",
    "Category.four=CAT-4: Exemplar/Comparison (Internal Use Only)",
    "Category.five=CAT-5: Non-pertinent",
    "Category.zero=CAT-0: Uncategorized"})
public enum DhsImageCategory {

    /*
     * This order of declaration is required so that Enum's compareTo method
     * preserves the fact that lower category numbers are first/most sever,
     * except 0 which is last
     */
    ONE(Color.RED, 1, Bundle.Category_one(), "cat1.png"),
    TWO(Color.ORANGE, 2, Bundle.Category_two(), "cat2.png"),
    THREE(Color.YELLOW, 3, Bundle.Category_three(), "cat3.png"),
    FOUR(Color.BISQUE, 4, Bundle.Category_four(), "cat4.png"),
    FIVE(Color.GREEN, 5, Bundle.Category_five(), "cat5.png"),
    ZERO(Color.LIGHTGREY, 0, Bundle.Category_zero(), "cat0.png");

    /** Map from displayName to enum value */
    private static final Map<String, DhsImageCategory> nameMap
            = Maps.uniqueIndex(Arrays.asList(values()), DhsImageCategory::getDisplayName);

    private final Color color;
    private final String displayName;
    private final int id;
    private final Image icon;

    private DhsImageCategory(Color color, int id, String name, String filename) {
        this.color = color;
        this.displayName = name;
        this.id = id;
        this.icon = new Image(getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/" + filename));
    }

    public static ImmutableList<DhsImageCategory> getNonZeroCategories() {
        return ImmutableList.of(FIVE, FOUR, THREE, TWO, ONE);
    }

    public static DhsImageCategory fromDisplayName(String displayName) {
        return nameMap.get(displayName);
    }

    public static boolean isCategoryName(String tName) {
        return nameMap.containsKey(tName);
    }

    public static boolean isNotCategoryName(String tName) {
        return nameMap.containsKey(tName) == false;
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

    public Node getGraphic() {
        return new ImageView(icon);
    }
}
