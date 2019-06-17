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
package org.sleuthkit.autopsy.contentviewers.imagetagging;

/**
 * Bean representation of an image tag. This class is used for storage and
 * retrieval of ImageTags from the case database.
 */
public class ImageTagRegion {

    /**
     * These fields will be serialized and stored in the case database by the
     * ContentViewerTagManager.
     */
    private double x;
    private double y;
    private double width;
    private double height;

    private double strokeThickness;

    public ImageTagRegion setStrokeThickness(double thickness) {
        this.strokeThickness = thickness;
        return this;
    }

    public ImageTagRegion setX(double x) {
        this.x = x;
        return this;
    }

    public ImageTagRegion setWidth(double width) {
        this.width = width;
        return this;
    }

    public ImageTagRegion setY(double y) {
        this.y = y;
        return this;
    }

    public ImageTagRegion setHeight(double height) {
        this.height = height;
        return this;
    }

    public double getX() {
        return x;
    }

    public double getWidth() {
        return width;
    }

    public double getY() {
        return y;
    }

    public double getHeight() {
        return height;
    }

    public double getStrokeThickness() {
        return strokeThickness;
    }
}
