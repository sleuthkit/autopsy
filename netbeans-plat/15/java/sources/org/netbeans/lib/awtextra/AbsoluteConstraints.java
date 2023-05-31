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

import java.awt.Dimension;
import java.awt.Point;

/** An object that encapsulates position and (optionally) size for
* Absolute positioning of components.
*
* @see AbsoluteLayout
* @version 1.01, Aug 19, 1998
*/
public class AbsoluteConstraints implements java.io.Serializable {
    /** generated Serialized Version UID */
    static final long serialVersionUID = 5261460716622152494L;

    /** The X position of the component */
    public int x;
    /** The Y position of the component */
    public int y;
    /** The width of the component or -1 if the component's preferred width should be used */
    public int width = -1;
    /** The height of the component or -1 if the component's preferred height should be used */
    public int height = -1;

    /** Creates a new AbsoluteConstraints for specified position.
    * @param pos The position to be represented by this AbsoluteConstraints
    */
    public AbsoluteConstraints(Point pos) {
        this (pos.x, pos.y);
    }

    /** Creates a new AbsoluteConstraints for specified position.
    * @param x The X position to be represented by this AbsoluteConstraints
    * @param y The Y position to be represented by this AbsoluteConstraints
    */
    public AbsoluteConstraints(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** Creates a new AbsoluteConstraints for specified position and size.
    * @param pos  The position to be represented by this AbsoluteConstraints
    * @param size The size to be represented by this AbsoluteConstraints or null
    *             if the component's preferred size should be used
    */
    public AbsoluteConstraints(Point pos, Dimension size) {
        this.x = pos.x;
        this.y = pos.y;
        if (size != null) {
            this.width = size.width;
            this.height = size.height;
        }
    }

    /** Creates a new AbsoluteConstraints for specified position and size.
    * @param x      The X position to be represented by this AbsoluteConstraints
    * @param y      The Y position to be represented by this AbsoluteConstraints
    * @param width  The width to be represented by this AbsoluteConstraints or -1 if the 
    *               component's preferred width should be used  
    * @param height The height to be represented by this AbsoluteConstraints or -1 if the
    *               component's preferred height should be used  
    */
    public AbsoluteConstraints(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return The X position represented by this AbsoluteConstraints */
    public int getX () {
        return x;
    }

    /** @return The Y position represented by this AbsoluteConstraints */
    public int getY () {
        return y;
    }

    /** @return The width represented by this AbsoluteConstraints or -1 if the
    * component's preferred width should be used 
    */
    public int getWidth () {
        return width;
    }

    /** @return The height represented by this AbsoluteConstraints or -1 if the
    * component's preferred height should be used 
    */
    public int getHeight () {
        return height;
    }

    public String toString () {
        return super.toString () +" [x="+x+", y="+y+", width="+width+", height="+height+"]";
    }

}

