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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javax.annotation.concurrent.GuardedBy;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.TagUtils;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public enum Category implements Comparable<Category> {

    ZERO(Color.LIGHTGREY, 0, "CAT-0, Uncategorized"),
    ONE(Color.RED, 1, "CAT-1,  Child Exploitation (Illegal)"),
    TWO(Color.ORANGE, 2, "CAT-2, Child Exploitation (Non-Illegal/Age Difficult)"),
    THREE(Color.YELLOW, 3, "CAT-3, CGI/Animation (Child Exploitive)"),
    FOUR(Color.BISQUE, 4, "CAT-4,  Exemplar/Comparison (Internal Use Only)"),
    FIVE(Color.GREEN, 5, "CAT-5, Non-pertinent");

    final static private Map<String, Category> nameMap = new HashMap<>();

    private static final List<Category> valuesList = Arrays.asList(values());

    static {
        for (Category cat : values()) {
            nameMap.put(cat.displayName, cat);
        }
    }

    @GuardedBy("listeners")
    private final static Set<CategoryListener> listeners = new HashSet<>();

    public static void fireChange(Collection<Long> ids) {
        Set<CategoryListener> listenersCopy = new HashSet<>();
        synchronized (listeners) {
            listenersCopy.addAll(listeners);
        }
        for (CategoryListener list : listenersCopy) {
            list.handleCategoryChanged(ids);
        }
      
    }

    public static void registerListener(CategoryListener aThis) {
        synchronized (listeners) {
            listeners.add(aThis);
        }
    }

    public static void unregisterListener(CategoryListener aThis) {
        synchronized (listeners) {
            listeners.remove(aThis);
        }
    }
  
    public KeyCode getHotKeycode() {
        return KeyCode.getKeyCode(Integer.toString(id));
    }

    public static final String CATEGORY_PREFIX = "CAT-";

    private TagName tagName;

    public static List<Category> valuesList() {
        return valuesList;
    }

    private Color color;

    private String displayName;

    private int id;

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

    static public Category fromDisplayName(String displayName) {
        return nameMap.get(displayName);
    }

    private Category(Color color, int id, String name) {
        this.color = color;
        this.displayName = name;
        this.id = id;
    }

    public TagName getTagName() {

        if (tagName == null) {
            try {
                tagName = TagUtils.getTagName(displayName);
            } catch (TskCoreException ex) {
                Logger.getLogger(Category.class.getName()).log(Level.SEVERE, "failed to get TagName for " + displayName, ex);
            }
        }
        return tagName;
    }

    public MenuItem createSelCatMenuItem(final SplitMenuButton catSelectedMenuButton) {
        final MenuItem menuItem = new MenuItem(this.getDisplayName(), new ImageView(DrawableAttribute.CATEGORY.getIcon()));
        menuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                new CategorizeAction().addTag(Category.this.getTagName(), "");
                catSelectedMenuButton.setText(Category.this.getDisplayName());
                catSelectedMenuButton.setOnAction(this);
            }
        });
        return menuItem;
    }
    
    /**
     * Use when closing a case to make sure everything is re-initialized in the next case.
     */
    public static void clearTagNames(){
        Category.ZERO.tagName = null;
        Category.ONE.tagName = null;
        Category.TWO.tagName = null;
        Category.THREE.tagName = null;
        Category.FOUR.tagName = null;
        Category.FIVE.tagName = null;
    }

    public static interface CategoryListener {

        public void handleCategoryChanged(Collection<Long> ids);

    }
}
