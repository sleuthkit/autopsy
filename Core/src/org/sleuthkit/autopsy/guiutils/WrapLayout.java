/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.guiutils;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * A layout class similar to FlowLayout in that when a component can't fit in a
 * row it is moved to the next row. Inspired by WrapLayout, this layout also
 * allows for aligning some components in the opposite side. In instances where
 * components are laid out left to right, these opposite aligned components will
 * be aligned to the right.
 *
 * Inspired by WrapLayout
 * https://tips4java.wordpress.com/2008/11/06/wrap-layout/ and FlowLayout
 * https://raw.githubusercontent.com/mynawang/Java8-Source-Code/master/src/main/jdk8/java/awt/FlowLayout.java.
 */
public class WrapLayout implements LayoutManager, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The <code>WrapLayout</code> manager allows a separation of components
     * with gaps. The horizontal gap will specify the space between components
     * and between the components and the borders of the <code>Container</code>.
     *
     * @serial
     * @see #getHgap()
     * @see #setHgap(int)
     */
    private int hgap = 0;

    /**
     * The <code>WrapLayout</code> manager allows a separation of components
     * with gaps. The vertical gap will specify the space between rows and
     * between the the rows and the borders of the <code>Container</code>.
     *
     * @serial
     * @see #getVgap()
     * @see #setVgap(int)
     */
    private int vgap = 0;

    /**
     * If true, components will be aligned on their baseline.
     */
    private boolean alignOnBaseline = false;

    /**
     * The set of components that will be aligned on the opposite side (if left
     * to right, on the right).
     */
    private final Set<Component> oppositeAlignedItems = new HashSet<>();

    /**
     * Constructs a new <code>WrapLayout</code> with a left alignment and a
     * default 5-unit horizontal and vertical gap.
     */
    public WrapLayout() {
        this(5, 5);
    }

    /**
     * Constructs a new <code>WrapLayout</code> with a left alignment.
     *
     * @param vgap The vertical gap spacing between rows of components.
     * @param hgap The horizontal gap spacing between components.
     */
    public WrapLayout(int vgap, int hgap) {
        this.vgap = vgap;
        this.hgap = hgap;
    }

    /**
     * Items in the collection will be aligned opposite to the rest. For
     * instance, if items should be displayed left to right based on locale,
     * these components will be on the right.
     *
     * @param oppAlignedComponents The components to display with opposite
     *                             alignment.
     */
    public void setOppositeAligned(Collection<Component> oppAlignedComponents) {
        synchronized (this.oppositeAlignedItems) {
            this.oppositeAlignedItems.clear();
            this.oppositeAlignedItems.addAll(oppAlignedComponents);
        }
    }

    /**
     * Items in the collection will be aligned opposite to the rest. For
     * instance, if items should be displayed left to right based on locale,
     * these components will be on the right.
     *
     * @return The components to display with opposite alignment.
     */
    public Collection<Component> getOppositeAlignedItems() {
        return oppositeAlignedItems;
    }

    /**
     * Gets the horizontal gap between components and between the components and
     * the borders of the <code>Container</code>
     *
     * @return The horizontal gap between components and between the components
     *         and the borders of the <code>Container</code>.
     */
    public int getHgap() {
        return hgap;
    }

    /**
     * Sets the horizontal gap between components and between the components and
     * the borders of the <code>Container</code>.
     *
     * @param hgap The horizontal gap between components and between the
     *             components and the borders of the <code>Container</code>.
     */
    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    /**
     * Gets the vertical gap between components and between the components and
     * the borders of the <code>Container</code>.
     *
     * @return The vertical gap between components and between the components
     *         and the borders of the <code>Container</code>.
     */
    public int getVgap() {
        return vgap;
    }

    /**
     * Sets the vertical gap between components and between the components and
     * the borders of the <code>Container</code>.
     *
     * @param vgap The vertical gap between components and between the
     *             components and the borders of the <code>Container</code>.
     */
    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    /**
     * Sets whether or not components should be vertically aligned along their
     * baseline. Components that do not have a baseline will be centered. The
     * default is false.
     *
     * @param alignOnBaseline Whether or not components should be vertically
     *                        aligned on their baseline.
     */
    public void setAlignOnBaseline(boolean alignOnBaseline) {
        this.alignOnBaseline = alignOnBaseline;
    }

    /**
     * Returns true if components are to be vertically aligned along their
     * baseline. The default is false.
     *
     * @return true If components are to be vertically aligned along their
     *         baseline.
     */
    public boolean getAlignOnBaseline() {
        return alignOnBaseline;
    }

    /**
     * Adds the specified component to the layout. Not used by this class. NOTE:
     * This is not used for this layout
     *
     * @param name The name of the component.
     * @param comp The component to be added.
     */
    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    /**
     * Removes the specified component from the layout. Not used by this class.
     * NOTE: This is not used for this layout
     *
     * @param comp The component to remove.
     */
    @Override
    public void removeLayoutComponent(Component comp) {
    }

    /**
     * Determines the subcomponent's y position.
     *
     * @param rowY          The top y position of the row.
     * @param alignBaseline Whether this component should be aligned on the
     *                      baseline.
     * @param rowHeight     The height of the row.
     * @param itemHeight    The height of the item.
     *
     * @return The top y position of the component.
     */
    private int getComponentY(int rowY, boolean alignBaseline, int rowHeight, int itemHeight) {
        return alignBaseline
                ? rowY + rowHeight - itemHeight
                : rowY;
    }

    /**
     * * Determines the subcomponent's x position.
     *
     * @param leftX          The leftmost position a component can be placed.
     * @param rightX         The rightmost position a component can be placed.
     * @param ltr            If the components should be laid out left to right.
     * @param xPos           The x position of the component (if left to right,
     *                       how far from leftX; otherwise how far from rightX).
     * @param componentWidth The component's width.
     *
     * @return The component's left x position.
     */
    private int getComponentX(int leftX, int rightX, boolean ltr, int xPos, int componentWidth) {
        return ltr ? leftX + xPos : rightX - xPos - componentWidth;
    }

    /**
     * Sets a subcomponent's size to preferred size and sets the (x,y) position
     * for the component.
     *
     * @param comp          The component.
     * @param alignBaseline Whether this component should be aligned on the
     *                      baseline.
     * @param ltr           If the components should be laid out left to right.
     * @param rowY          The top y position of the row.
     * @param rowHeight     The height of the row.
     * @param leftX         The leftmost position a component can be placed.
     * @param rightX        The rightmost position a component can be placed.
     * @param xPos          The x position of the component (if left to right,
     *                      how far from leftX; otherwise how far from rightX).
     *
     * @return The width of the component.
     */
    private int setComponentDims(Component comp, boolean alignBaseline, boolean ltr, int rowY, int rowHeight, int leftX, int rightX, int xPos) {
        Dimension d = comp.getPreferredSize();
        comp.setSize(d);

        int x = getComponentX(leftX, rightX, ltr, xPos, d.width);
        int y = getComponentY(rowY, alignBaseline, rowHeight, d.height);
        comp.setLocation(x, y);

        return d.width;
    }

    /**
     * Lays out the container. This method lets each
     * <i>visible</i> component take its preferred size by reshaping the
     * components in the target container and creating new rows.
     *
     * @param target The specified component being laid out.
     */
    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            synchronized (this.oppositeAlignedItems) {
                ParentDimensions targetDims = getTargetDimensions(target);
                List<Component> components = Arrays.asList(target.getComponents());
                List<WrapLayoutRow> rows = getAllRows(components, true, targetDims.getInnerWidth());

                boolean ltr = target.getComponentOrientation().isLeftToRight();
                boolean useBaseline = getAlignOnBaseline();

                int rowY = targetDims.getInsets().top + getVgap();
                int leftX = targetDims.getInsets().left + getHgap();
                int rightX = targetDims.getOuterWidth() - targetDims.getInsets().right - getHgap();

                for (WrapLayoutRow row : rows) {
                    int rowHeight = row.getHeight();

                    int curX = 0;
                    if (row.getComponents() != null) {
                        for (Component origComp : row.getComponents()) {
                            curX += setComponentDims(origComp, useBaseline, ltr, rowY, rowHeight, leftX, rightX, curX) + getHgap();
                        }
                    }

                    if (row.getOppositeAligned() != null) {
                        curX = 0;
                        // reverse opposite aligned for layout purposes since flipping ltr
                        Collections.reverse(row.getOppositeAligned());
                        for (Component oppAlignedComp : row.getOppositeAligned()) {
                            curX += setComponentDims(oppAlignedComp, useBaseline, !ltr, rowY, rowHeight, leftX, rightX, curX) + getHgap();
                        }
                    }

                    rowY += rowHeight + getVgap();
                }
            }
        }
    }

    /**
     * Returns the preferred dimensions for this layout given the
     * <i>visible</i> components in the specified target container.
     *
     * @param target The component which needs to be laid out.
     *
     * @return The preferred dimensions to lay out the subcomponents of the
     *         specified container.
     */
    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    /**
     * Returns the minimum dimensions needed to layout the <i>visible</i>
     * components contained in the specified target container.
     *
     * @param target The component which needs to be laid out.
     *
     * @return The minimum dimensions to lay out the subcomponents of the
     *         specified container.
     */
    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    /**
     * This class provides metrics on the parent container dimensions.
     */
    private static class ParentDimensions {

        private final int outerWidth;
        private final int innerWidth;
        private final Insets insets;

        /**
         * Main constructor for ParentDimensions class.
         *
         * @param outerWidth The full width that the component can consume.
         * @param innerWidth The full width that subcomponent rows can consume.
         * @param insets     The insets of the parent container.
         */
        ParentDimensions(int outerWidth, int innerWidth, Insets insets) {
            this.outerWidth = outerWidth;
            this.innerWidth = innerWidth;
            this.insets = insets;
        }

        /**
         * Gets the full width that the component can consume.
         *
         * @return The full width that the component can consume.
         */
        int getOuterWidth() {
            return outerWidth;
        }

        /**
         * Gets the full width that subcomponent rows can consume. This is the
         * outerWidth accounting for left and right insets.
         *
         * @return The full width that subcomponent rows can consume.
         */
        int getInnerWidth() {
            return innerWidth;
        }

        /**
         * Gets the insets of the parent container.
         *
         * @return The insets of the parent container.
         */
        Insets getInsets() {
            return insets;
        }
    }

    /**
     * Derives metrics on the space allowed within the parent container for rows
     * of components.
     *
     * @param target The target container.
     *
     * @return The dimensions for laying out components.
     */
    private ParentDimensions getTargetDimensions(Container target) {
        while (target.getSize().width == 0 && target.getParent() != null) {
            target = target.getParent();
        }

        int targetWidth = target.getSize().width;

        if (targetWidth == 0) {
            targetWidth = Integer.MAX_VALUE;
        }

        Insets insets = target.getInsets();
        int horizontalInsetsAndGap = insets.left + insets.right + (getHgap() * 2);
        int maxWidth = targetWidth - horizontalInsetsAndGap;

        return new ParentDimensions(targetWidth, maxWidth, insets);
    }

    /**
     * Returns the minimum or preferred dimension needed to layout the target
     * container.
     *
     * @param target    Target to get layout size for.
     * @param preferred Should preferred size be calculated.
     *
     * @return The dimension to layout the target container.
     */
    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            synchronized (this.oppositeAlignedItems) {
                ParentDimensions targetDims = getTargetDimensions(target);
                List<Component> components = Arrays.asList(target.getComponents());
                List<WrapLayoutRow> rows = getAllRows(components, preferred, targetDims.getInnerWidth());

                Integer containerHeight = rows.stream().map((r) -> r.getHeight()).reduce(0, Integer::sum);
                // add in vertical gap between rows
                if (rows.size() > 1) {
                    containerHeight += (rows.size() - 1) * getVgap();
                }

                containerHeight += targetDims.getInsets().top + targetDims.getInsets().bottom;

                Integer containerWidth = rows.stream().map((r) -> r.getWidth()).reduce(0, Math::max);
                containerWidth += targetDims.getInsets().left + targetDims.getInsets().right + (getHgap() * 2);

                //  When using a scroll pane or the DecoratedLookAndFeel we need to
                //  make sure the preferred size is less than the size of the
                //  target containter so shrinking the container size works
                //  correctly. Removing the horizontal gap is an easy way to do this.
                Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);

                if (scrollPane != null && target.isValid()) {
                    containerWidth -= (getHgap() + 1);
                }

                return new Dimension(containerWidth, containerHeight);
            }
        }
    }

    /**
     * A row of components in the WrapLayout.
     */
    private class WrapLayoutRow {

        private final List<Component> components;
        private final List<Component> oppositeAligned;
        private final int height;
        private final int width;

        /**
         * This is the main constructor for a row of components in the
         * WrapLayout.
         *
         * @param components      The components that should be normally aligned
         *                        in the row.
         * @param oppositeAligned The components that should be oppositely
         *                        aligned in the row.
         * @param height          The maximum height of the row.
         * @param width           The total width of the row.
         */
        WrapLayoutRow(List<Component> components, List<Component> oppositeAligned, int height, int width) {
            this.components = components;
            this.oppositeAligned = oppositeAligned;
            this.height = height;
            this.width = width;
        }

        /**
         * Gets the normally aligned components in the order that they will be
         * laid out.
         *
         * @return The normally aligned components in the order that they will
         *         be laid out.
         */
        List<Component> getComponents() {
            return components;
        }

        /**
         * Gets the opposite aligned components in the order that they will be
         * laid out.
         *
         * @return The opposite aligned components in the order that they will
         *         be laid out.
         */
        List<Component> getOppositeAligned() {
            return oppositeAligned;
        }

        /**
         * Gets the minimum height of the row which is the maximum of the
         * preferred heights of the components.
         *
         * @return The minimum height of the row which is the maximum of the
         *         preferred heights of the components.
         */
        int getHeight() {
            return height;
        }

        /**
         * Gets the minimum width of the row which is the sum of the preferred
         * widths of the subcomponents.
         *
         * @return The minimum width of the row which is the sum of the
         *         preferred widths of the subcomponents.
         */
        int getWidth() {
            return width;
        }

    }

    /**
     * Retrieves the rows of wrap layout components.
     *
     * @param components The components to be laid out.
     * @param preferred  Whether or not to use preferred dimensions of
     *                   subcomponents for determining rows.
     * @param maxWidth   The maximum width that a row can consume.
     *
     * @return The list of rows ordered from top to bottom.
     */
    private List<WrapLayoutRow> getAllRows(List<Component> components, boolean preferred, int maxWidth) {
        List<Component> originalComp
                = components
                        .stream()
                        .filter((comp) -> !this.oppositeAlignedItems.contains(comp))
                        .collect(Collectors.toList());

        List<WrapLayoutRow> originalRowSet = getRowSet(originalComp, preferred, maxWidth);

        List<Component> oppositeAlignedComp
                = components
                        .stream()
                        .filter((comp) -> this.oppositeAlignedItems.contains(comp))
                        .collect(Collectors.toList());

        // go in reverse order and then revert so we can use same getRowSet method
        Collections.reverse(oppositeAlignedComp);
        List<WrapLayoutRow> oppositeRowSet = getRowSet(oppositeAlignedComp, preferred, maxWidth)
                .stream()
                .map((WrapLayoutRow row) -> {
                    Collections.reverse(row.getComponents());
                    return new WrapLayoutRow(null, row.getComponents(), row.getHeight(), row.getWidth());
                })
                .collect(Collectors.toList());
        Collections.reverse(oppositeRowSet);

        List<WrapLayoutRow> toReturn = new ArrayList<>();

        // if there is a row of components that will have both normal and opposite aligned
        // components, create the corresponding row.
        if (!originalRowSet.isEmpty() && !oppositeRowSet.isEmpty()) {
            WrapLayoutRow lastOrig = originalRowSet.get(originalRowSet.size() - 1);
            WrapLayoutRow firstOpp = oppositeRowSet.get(0);

            int proposedRowWidth = lastOrig.getWidth() + firstOpp.getWidth() + getHgap();
            if (proposedRowWidth <= maxWidth) {
                WrapLayoutRow middleRow = new WrapLayoutRow(lastOrig.getComponents(), firstOpp.getOppositeAligned(),
                        Math.max(lastOrig.getHeight(), firstOpp.getHeight()), proposedRowWidth);

                toReturn.addAll(originalRowSet.subList(0, originalRowSet.size() - 1));
                toReturn.add(middleRow);
                toReturn.addAll(oppositeRowSet.subList(1, oppositeRowSet.size()));
                return toReturn;
            }
        }

        toReturn.addAll(originalRowSet);
        toReturn.addAll(oppositeRowSet);
        return toReturn;
    }

    /**
     * Handles determining rows for a single set of similarly aligned
     * components. Used once for normal alignment and once for opposite aligned
     * components.
     *
     * @param components The components in the set of similarly aligned items.
     * @param preferred  Whether or not to use preferred dimensions for
     *                   components.
     * @param maxWidth   The maximum width components can consume.
     *
     * @return The list of rows determined.
     */
    private List<WrapLayoutRow> getRowSet(List<Component> components, boolean preferred, int maxWidth) {
        List<WrapLayoutRow> rows = new ArrayList<>();

        List<Component> rowComponents = new ArrayList<>();
        int rowWidth = 0;
        int rowHeight = 0;

        for (Component m : components) {
            if (m.isVisible()) {
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                //  Can't add the component to current row. Start a new row.
                if (rowWidth + d.width > maxWidth) {
                    rows.add(new WrapLayoutRow(rowComponents, null, rowHeight, rowWidth));
                    rowComponents = new ArrayList<>();
                    rowWidth = 0;
                    rowHeight = 0;
                }

                //  Add a horizontal gap for all components after the first
                if (rowWidth != 0) {
                    rowWidth += getHgap();
                }

                rowComponents.add(m);
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
        }

        if (!rowComponents.isEmpty()) {
            rows.add(new WrapLayoutRow(rowComponents, null, rowHeight, rowWidth));
        }

        return rows;
    }
}
