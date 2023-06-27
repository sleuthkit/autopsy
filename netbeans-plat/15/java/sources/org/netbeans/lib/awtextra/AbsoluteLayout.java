/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.lib.awtextra;

import java.awt.*;

/** AbsoluteLayout is a LayoutManager that works as a replacement for "null" layout to
* allow placement of components in absolute positions.
*
* @see AbsoluteConstraints
* @version 1.01, Aug 19, 1998
*/
public class AbsoluteLayout implements LayoutManager2, java.io.Serializable {
    /** generated Serialized Version UID */
    static final long serialVersionUID = -1919857869177070440L;

    /** Adds the specified component with the specified name to
    * the layout.
    * @param name the component name
    * @param comp the component to be added
    */
    public void addLayoutComponent(String name, Component comp) {
        throw new IllegalArgumentException();
    }

    /** Removes the specified component from the layout.
    * @param comp the component to be removed
    */
    public void removeLayoutComponent(Component comp) {
        constraints.remove(comp);
    }

    /** Calculates the preferred dimension for the specified
    * panel given the components in the specified parent container.
    * @param parent the component to be laid out
    *
    * @see #minimumLayoutSize
    */
    public Dimension preferredLayoutSize(Container parent) {
        int maxWidth = 0;
        int maxHeight = 0;
        for (java.util.Enumeration e = constraints.keys(); e.hasMoreElements();) {
            Component comp = (Component)e.nextElement();
            AbsoluteConstraints ac = (AbsoluteConstraints)constraints.get(comp);
            Dimension size = comp.getPreferredSize();

            int width = ac.getWidth ();
            if (width == -1) width = size.width;
            int height = ac.getHeight ();
            if (height == -1) height = size.height;

            if (ac.x + width > maxWidth)
                maxWidth = ac.x + width;
            if (ac.y + height > maxHeight)
                maxHeight = ac.y + height;
        }
        return new Dimension (maxWidth, maxHeight);
    }

    /** Calculates the minimum dimension for the specified
    * panel given the components in the specified parent container.
    * @param parent the component to be laid out
    * @see #preferredLayoutSize
    */
    public Dimension minimumLayoutSize(Container parent) {
        int maxWidth = 0;
        int maxHeight = 0;
        for (java.util.Enumeration e = constraints.keys(); e.hasMoreElements();) {
            Component comp = (Component)e.nextElement();
            AbsoluteConstraints ac = (AbsoluteConstraints)constraints.get(comp);

            Dimension size = comp.getMinimumSize();

            int width = ac.getWidth ();
            if (width == -1) width = size.width;
            int height = ac.getHeight ();
            if (height == -1) height = size.height;

            if (ac.x + width > maxWidth)
                maxWidth = ac.x + width;
            if (ac.y + height > maxHeight)
                maxHeight = ac.y + height;
        }
        return new Dimension (maxWidth, maxHeight);
    }

    /** Lays out the container in the specified panel.
    * @param parent the component which needs to be laid out
    */
    public void layoutContainer(Container parent) {
        for (java.util.Enumeration e = constraints.keys(); e.hasMoreElements();) {
            Component comp = (Component)e.nextElement();
            AbsoluteConstraints ac = (AbsoluteConstraints)constraints.get(comp);
            Dimension size = comp.getPreferredSize();
            int width = ac.getWidth ();
            if (width == -1) width = size.width;
            int height = ac.getHeight ();
            if (height == -1) height = size.height;

            comp.setBounds(ac.x, ac.y, width, height);
        }
    }

    /** Adds the specified component to the layout, using the specified
    * constraint object.
    * @param comp the component to be added
    * @param constr  where/how the component is added to the layout.
    */
    public void addLayoutComponent(Component comp, Object constr) {
        if (!(constr instanceof AbsoluteConstraints))
            throw new IllegalArgumentException();
        constraints.put(comp, constr);
    }

    /** Returns the maximum size of this component.
    * @see java.awt.Component#getMinimumSize()
    * @see java.awt.Component#getPreferredSize()
    * @see LayoutManager
    */
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** Returns the alignment along the x axis.  This specifies how
    * the component would like to be aligned relative to other
    * components.  The value should be a number between 0 and 1
    * where 0 represents alignment along the origin, 1 is aligned
    * the furthest away from the origin, 0.5 is centered, etc.
    */
    public float getLayoutAlignmentX(Container target) {
        return 0;
    }

    /** Returns the alignment along the y axis.  This specifies how
    * the component would like to be aligned relative to other
    * components.  The value should be a number between 0 and 1
    * where 0 represents alignment along the origin, 1 is aligned
    * the furthest away from the origin, 0.5 is centered, etc.
    */
    public float getLayoutAlignmentY(Container target) {
        return 0;
    }

    /** Invalidates the layout, indicating that if the layout manager
    * has cached information it should be discarded.
    */
    public void invalidateLayout(Container target) {
    }


    /** A mapping <Component, AbsoluteConstraints> */
    protected java.util.Hashtable constraints = new java.util.Hashtable();
}

