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
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.openide.util.NbBundle;

/**
 * Enum to represent the six categories in the DHS image categorization scheme.
 */
@NbBundle.Messages({"Category.one=CAT-1: Child Exploitation (Illegal)",
    "Category.two=CAT-2: Child Exploitation (Non-Illegal/Age Difficult)",
    "Category.three=CAT-3: CGI/Animation (Child Exploitive)",
    "Category.four=CAT-4: Exemplar/Comparison (Internal Use Only)",
    "Category.five=CAT-5: Non-pertinent",
    "Category.zero=CAT-0: Uncategorized"})
public enum Category {

    /*
     * This order of declaration is required so that Enum's compareTo method
     * preserves the fact that lower category numbers are first/most sever,
     * except 0 which is last
     */
    ONE(Color.RED, 1, Bundle.Category_one()),
    TWO(Color.ORANGE, 2, Bundle.Category_two()),
    THREE(Color.YELLOW, 3, Bundle.Category_three()),
    FOUR(Color.BISQUE, 4, Bundle.Category_four()),
    FIVE(Color.GREEN, 5, Bundle.Category_five()),
    ZERO(Color.LIGHTGREY, 0, Bundle.Category_zero());

    private static final BorderWidths BORDER_WIDTHS_2 = new BorderWidths(2);
    private static final CornerRadii CORNER_RADII_4 = new CornerRadii(4);

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
    private Image snapshot;

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

    synchronized public Node getGraphic() {
        if (snapshot == null) {
            Region region = new Region();
            region.setBackground(new Background(new BackgroundFill(getColor(), CORNER_RADII_4, Insets.EMPTY)));
            region.setPrefSize(16, 16);
            region.setBorder(new Border(new BorderStroke(getColor().darker(), BorderStrokeStyle.SOLID, CORNER_RADII_4, BORDER_WIDTHS_2)));
            Scene scene = new Scene(region, 16, 16, Color.TRANSPARENT);
            snapshot = region.snapshot(null, null);
        }
        return new ImageView(snapshot);
    }
}
