package org.sleuthkit.autopsy.imageanalyzer.gui;

import java.util.Collection;
import javafx.application.Platform;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imageanalyzer.TagUtils;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.Category;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;

/**
 * TODO: extract common interface out of {@link SingleImageView} and
 * {@link MetaDataPane}
 */
public interface DrawableView extends Category.CategoryListener, TagUtils.TagListener {

    //TODO: do this all in css? -jm
    static final int CAT_BORDER_WIDTH = 10;

    static final BorderWidths CAT_BORDER_WIDTHS = new BorderWidths(CAT_BORDER_WIDTH);

    static final CornerRadii CAT_CORNER_RADII = new CornerRadii(3);

    static final Border HASH_BORDER = new Border(new BorderStroke(Color.PURPLE, BorderStrokeStyle.DASHED, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT1_BORDER = new Border(new BorderStroke(Category.ONE.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT2_BORDER = new Border(new BorderStroke(Category.TWO.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT3_BORDER = new Border(new BorderStroke(Category.THREE.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT4_BORDER = new Border(new BorderStroke(Category.FOUR.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT5_BORDER = new Border(new BorderStroke(Category.FIVE.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT0_BORDER = new Border(new BorderStroke(Category.ZERO.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    Region getBorderable();

    DrawableFile<?> getFile();

    void setFile(final Long fileID);

    Long getFileID();

    @Override
    void handleCategoryChanged(Collection<Long> ids);

    @Override
    void handleTagsChanged(Collection<Long> ids);

    default boolean hasHashHit() {
        return getFile().getHashHitSetNames().isEmpty() == false;
    }

    static Border getCategoryBorder(Category category) {
        switch (category) {
            case ZERO:
                return CAT0_BORDER;
            case ONE:
                return CAT1_BORDER;
            case TWO:
                return CAT2_BORDER;
            case THREE:
                return CAT3_BORDER;
            case FOUR:
                return CAT4_BORDER;
            case FIVE:
            default:
                return CAT5_BORDER;
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    default Category updateCategoryBorder() {
        final Category category = getFile().getCategory();
        final Border border = hasHashHit() && (category == Category.ZERO)
                              ? HASH_BORDER
                              : DrawableView.getCategoryBorder(category);

        Platform.runLater(() -> {
            getBorderable().setBorder(border);
        });
        return category;
    }

}
