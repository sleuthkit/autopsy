/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

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
 * FlowLayout subclass that fully supports wrapping of components.
 *
 * Originally written by Rob Camick
 * https://tips4java.wordpress.com/2008/11/06/wrap-layout/
 */
class WrapLayout implements LayoutManager, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    
    /**
     * The flow layout manager allows a seperation of
     * components with gaps.  The horizontal gap will
     * specify the space between components and between
     * the components and the borders of the
     * <code>Container</code>.
     *
     * @serial
     * @see #getHgap()
     * @see #setHgap(int)
     */
    private int hgap;

    /**
     * The flow layout manager allows a seperation of
     * components with gaps.  The vertical gap will
     * specify the space between rows and between the
     * the rows and the borders of the <code>Container</code>.
     *
     * @serial
     * @see #getHgap()
     * @see #setHgap(int)
     */
    private int vgap;

    /**
     * If true, components will be aligned on their baseline.
     */
    private boolean alignOnBaseline;
    
    
    
    
    
    private final Set<Component> oppositeAlignedItems = new HashSet<>();

    /**
     * Constructs a new <code>WrapLayout</code> with a left alignment and a
     * default 5-unit horizontal and vertical gap.
     */
    WrapLayout() {
        super();
    }

    
    void setOppositeAligned(Collection<Component> rightAlignedComponents) {
        synchronized (this.oppositeAlignedItems) {
            this.oppositeAlignedItems.clear();
            this.oppositeAlignedItems.addAll(rightAlignedComponents);
        }
    }

    Set<Component> getOppositeAlignedItems() {
        return oppositeAlignedItems;
    }
    
    
    /**
     * Gets the horizontal gap between components
     * and between the components and the borders
     * of the <code>Container</code>
     *
     * @return     the horizontal gap between components
     *             and between the components and the borders
     *             of the <code>Container</code>
     * @see        java.awt.FlowLayout#setHgap
     * @since      JDK1.1
     */
    int getHgap() {
        return hgap;
    }

    /**
     * Sets the horizontal gap between components and
     * between the components and the borders of the
     * <code>Container</code>.
     *
     * @param hgap the horizontal gap between components
     *             and between the components and the borders
     *             of the <code>Container</code>
     * @see        java.awt.FlowLayout#getHgap
     * @since      JDK1.1
     */
    void setHgap(int hgap) {
        this.hgap = hgap;
    }

    /**
     * Gets the vertical gap between components and
     * between the components and the borders of the
     * <code>Container</code>.
     *
     * @return     the vertical gap between components
     *             and between the components and the borders
     *             of the <code>Container</code>
     * @see        java.awt.FlowLayout#setVgap
     * @since      JDK1.1
     */
    int getVgap() {
        return vgap;
    }

    /**
     * Sets the vertical gap between components and between
     * the components and the borders of the <code>Container</code>.
     *
     * @param vgap the vertical gap between components
     *             and between the components and the borders
     *             of the <code>Container</code>
     * @see        java.awt.FlowLayout#getVgap
     * @since      JDK1.1
     */
    void setVgap(int vgap) {
        this.vgap = vgap;
    }

    /**
     * Sets whether or not components should be vertically aligned along their
     * baseline.  Components that do not have a baseline will be centered.
     * The default is false.
     *
     * @param alignOnBaseline whether or not components should be
     *                        vertically aligned on their baseline
     * @since 1.6
     */
    void setAlignOnBaseline(boolean alignOnBaseline) {
        this.alignOnBaseline = alignOnBaseline;
    }

    /**
     * Returns true if components are to be vertically aligned along
     * their baseline.  The default is false.
     *
     * @return true if components are to be vertically aligned along
     *              their baseline
     * @since 1.6
     */
    boolean getAlignOnBaseline() {
        return alignOnBaseline;
    }

    /**
     * Adds the specified component to the layout.
     * Not used by this class.
     * @param name the name of the component
     * @param comp the component to be added
     */
    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    /**
     * Removes the specified component from the layout.
     * Not used by this class.
     * @param comp the component to remove
     * @see       java.awt.Container#removeAll
     */
    @Override
    public void removeLayoutComponent(Component comp) {
    }
    

    
    private int getComponentY(int rowY, boolean alignBaseline, int rowHeight, int itemHeight) {
        return alignBaseline ? 
            rowY + rowHeight - itemHeight : 
            rowY;
    }
    
    private int getComponentX(int leftX, int rightX, boolean ltr, int xPos, int componentWidth) {
        return ltr ? leftX + xPos : rightX - xPos - componentWidth;
    }
    
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
     * components in the target container in order to satisfy the alignment of
     * this <code>FlowLayout</code> object. Taken from
     * https://raw.githubusercontent.com/mynawang/Java8-Source-Code/master/src/main/jdk8/java/awt/FlowLayout.java.
     *
     * @param target the specified component being laid out
     *
     * @see Container
     * @see java.awt.Container#doLayout
     */
    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            synchronized (this.oppositeAlignedItems) {
                ParentDimensions targetDims = getTargetDimensions(target);
                List<Component> components = Arrays.asList(target.getComponents());
                List<Row> rows = getAllRows(components, true, targetDims.innerWidth);

                boolean ltr = target.getComponentOrientation().isLeftToRight();
                boolean useBaseline = getAlignOnBaseline();
                
                int rowY = targetDims.insets.top + getVgap();
                int leftX = targetDims.insets.left + getHgap();
                int rightX = targetDims.outerWidth - targetDims.insets.right - getHgap();
                
                for (Row row : rows) {
                    int rowHeight = row.height;
                    
                    int curX = 0;
                    if (row.components != null) {
                        for (Component origComp : row.components)
                            curX += setComponentDims(origComp, useBaseline, ltr, rowY, rowHeight, leftX, rightX, curX) + getHgap();
                    }
                    
                    if (row.oppositeAligned != null) {
                        curX = 0;
                        // reverse opposite aligned for layout purposes since flipping ltr
                        Collections.reverse(row.oppositeAligned);
                        for (Component oppAlignedComp : row.oppositeAligned)
                            curX += setComponentDims(oppAlignedComp, useBaseline, !ltr, rowY, rowHeight, leftX, rightX, curX) + getHgap();
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
     * @param target the component which needs to be laid out
     *
     * @return the preferred dimensions to lay out the subcomponents of the
     *         specified container
     */
    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    /**
     * Returns the minimum dimensions needed to layout the <i>visible</i>
     * components contained in the specified target container.
     *
     * @param target the component which needs to be laid out
     *
     * @return the minimum dimensions to lay out the subcomponents of the
     *         specified container
     */
    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private static class ParentDimensions {

        final int outerWidth;
        final int innerWidth;
        final Insets insets;

        ParentDimensions(int outerWidth, int innerWidth, Insets insets) {
            this.outerWidth = outerWidth;
            this.innerWidth = innerWidth;
            this.insets = insets;
        }
    }

    private ParentDimensions getTargetDimensions(Container target) {
        //  Each row must fit with the width allocated to the containter.
        //  When the container width = 0, the preferred width of the container
        //  has not yet been calculated so lets ask for the maximum.

        int targetWidth = target.getSize().width;
        Container container = target;

        while (container.getSize().width == 0 && container.getParent() != null) {
            container = container.getParent();
        }

        targetWidth = container.getSize().width;

        if (targetWidth == 0) {
            targetWidth = Integer.MAX_VALUE;
        }

        int hgap = getHgap();

        Insets insets = target.getInsets();
        int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
        int maxWidth = targetWidth - horizontalInsetsAndGap;

        return new ParentDimensions(targetWidth, maxWidth, insets);
    }

    /**
     * Returns the minimum or preferred dimension needed to layout the target
     * container.
     *
     * @param target    target to get layout size for
     * @param preferred should preferred size be calculated
     *
     * @return the dimension to layout the target container
     */
    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            synchronized (this.oppositeAlignedItems) {
                ParentDimensions targetDims = getTargetDimensions(target);
                List<Component> components = Arrays.asList(target.getComponents());
                List<Row> rows = getAllRows(components, preferred, targetDims.innerWidth);

                Integer containerHeight = rows.stream().map((r) -> r.height).reduce(0, Integer::sum);
                // add in vertical gap between rows
                if (rows.size() > 1) {
                    containerHeight += (rows.size() - 1) * getVgap();
                }

                containerHeight += targetDims.insets.top + targetDims.insets.bottom;

                Integer containerWidth = rows.stream().map((r) -> r.width).reduce(0, Math::max);
                containerWidth += targetDims.insets.left + targetDims.insets.right + (getHgap() * 2);

                //	When using a scroll pane or the DecoratedLookAndFeel we need to
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

    private class Row {

        final List<Component> components;
        final List<Component> oppositeAligned;
        final int height;
        final int width;

        public Row(List<Component> components, List<Component> oppositeAligned, int height, int width) {
            this.components = components;
            this.oppositeAligned = oppositeAligned;
            this.height = height;
            this.width = width;
        }
    }

    private List<Row> getAllRows(List<Component> components, boolean preferred, int maxWidth) {
        List<Component> originalComp
                = components
                        .stream()
                        .filter((comp) -> !this.oppositeAlignedItems.contains(comp))
                        .collect(Collectors.toList());

        List<Row> originalRowSet = getRowSet(originalComp, preferred, maxWidth);

        List<Component> oppositeAlignedComp
                = components
                        .stream()
                        .filter((comp) -> this.oppositeAlignedItems.contains(comp))
                        .collect(Collectors.toList());

        // go in reverse order and then revert so we can use same getRowSet method
        Collections.reverse(oppositeAlignedComp);
        List<Row> oppositeRowSet = getRowSet(oppositeAlignedComp, preferred, maxWidth)
                .stream()
                .map((Row row) -> {
                    Collections.reverse(row.components);
                    return new Row(null, row.components, row.height, row.width);
                })
                .collect(Collectors.toList());
        Collections.reverse(oppositeRowSet);

        List<Row> toReturn = new ArrayList<>();

        if (originalRowSet.size() > 0 && oppositeRowSet.size() > 0) {
            Row lastOrig = originalRowSet.get(originalRowSet.size() - 1);
            Row firstOpp = oppositeRowSet.get(0);

            int proposedRowWidth = lastOrig.width + firstOpp.width + getHgap();
            if (proposedRowWidth <= maxWidth) {
                Row middleRow = new Row(lastOrig.components, firstOpp.oppositeAligned,
                        Math.max(lastOrig.height, firstOpp.height), proposedRowWidth);

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

    private List<Row> getRowSet(List<Component> components, boolean preferred, int maxWidth) {
        List<Row> rows = new ArrayList<>();

        List<Component> rowComponents = new ArrayList<>();
        int rowWidth = 0;
        int rowHeight = 0;

        for (Component m : components) {
            if (m.isVisible()) {
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                //  Can't add the component to current row. Start a new row.
                if (rowWidth + d.width > maxWidth) {
                    rows.add(new Row(rowComponents, null, rowHeight, rowWidth));
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

        if (rowComponents.size() > 0) {
            rows.add(new Row(rowComponents, null, rowHeight, rowWidth));
        }

        return rows;
    }
}
