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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javafx.event.EventHandler;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Creates image tags. This class attaches itself to a source image, waiting
 * for mouse press, mouse drag, and mouse release events. Upon a mouse release 
 * event, any listeners are updated with the portable description of the new tag 
 * boundaries (ImageTagRegion).
 */
public final class ImageTagCreator extends Rectangle {

    //Origin of the drag event.
    private double rectangleOriginX, rectangleOriginY;

    //Rectangle lines should be 1.5% of the image. This level of thickness has
    //a good balance between visual acuity and loss of selection at the borders
    //of the image.
    private final static double LINE_THICKNESS_PERCENT = 1.5;
    private final double minArea;
    
    //Used to update listeners of the new tag boundaries
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private final EventHandler<MouseEvent> mousePressed;
    private final EventHandler<MouseEvent> mouseDragged;
    private final EventHandler<MouseEvent> mouseReleased;
    
    //Handles the unregistering this ImageTagCreator from mouse press, mouse drag,
    //and mouse release events of the source image.
    private final Runnable disconnectRunnable;

    /**
     * Adds tagging support to an image, where the 'tag' rectangle will be the
     * specified color.
     *
     * @param image Image to tag
     */
    public ImageTagCreator(ImageView image) {
        setStroke(Color.RED);
        setFill(Color.RED.deriveColor(0, 0, 0, 0));

        //Calculate how many pixels the stroke width should be to guarentee
        //a consistent % of image consumed by the rectangle border.
        double min = Math.min(image.getImage().getWidth(), image.getImage().getHeight());
        double lineThicknessPixels = min * LINE_THICKNESS_PERCENT / 100.0;
        setStrokeWidth(lineThicknessPixels);
        minArea = lineThicknessPixels * lineThicknessPixels;
        setVisible(false);
        
        this.mousePressed = (MouseEvent event) -> {
            if (event.isSecondaryButtonDown()) {
                return;
            }

            //Reset box on new click.
            defaultSettings();
            rectangleOriginX = event.getX();
            rectangleOriginY = event.getY();

            setX(rectangleOriginX);
            setY(rectangleOriginY);
        };

        image.addEventHandler(MouseEvent.MOUSE_PRESSED, this.mousePressed);

        this.mouseDragged = (MouseEvent event) -> {
            if (event.isSecondaryButtonDown()) {
                return;
            }

            double currentX = event.getX(), currentY = event.getY();

            /**
             * Ensure the rectangle is contained within image boundaries and
             * that the line thickness is kept within bounds.
             */
            double newX = Math.min(Math.max(currentX, image.getX())
                    + lineThicknessPixels / 2, image.getImage().getWidth() - lineThicknessPixels / 2);
            double newY = Math.min(Math.max(currentY, image.getY())
                    + lineThicknessPixels / 2, image.getImage().getHeight() - lineThicknessPixels / 2);

            setVisible(true);
            double offsetX = newX - rectangleOriginX;
            if (offsetX < 0) {
                setX(newX);
            }
            setWidth(Math.abs(offsetX));

            double offsetY = newY - rectangleOriginY;
            if (offsetY < 0) {
                setY(newY);
            }
            setHeight(Math.abs(offsetY));
        };

        image.addEventHandler(MouseEvent.MOUSE_DRAGGED, this.mouseDragged);

        this.mouseReleased = event -> {
            //Reject any drags that are too small to count as a meaningful tag.
            //Meaningful is described as having an area that is visible that is 
            //not consumed by the thickness of the stroke.
            if ((this.getWidth() - this.getStrokeWidth())
                    * (this.getHeight() - this.getStrokeWidth()) <= minArea) {
                defaultSettings();
                return;
            }

            this.pcs.firePropertyChange(new PropertyChangeEvent(this, "New Tag",
                    null, new ImageTagRegion()
                            .setX(this.getX())
                            .setY(this.getY())
                            .setWidth(this.getWidth())
                            .setHeight(this.getHeight())
                            .setStrokeThickness(lineThicknessPixels)));
        };

        image.addEventHandler(MouseEvent.MOUSE_RELEASED, this.mouseReleased);

        //Used to remove itself from mouse events on the source image
        disconnectRunnable = () -> {
            defaultSettings();
            image.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleased);
            image.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDragged);
            image.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed);
        };
    }

    /**
     * Registers a PCL for new tag events. Listeners are updated with a portable
     * description (ImageTagRegion) of the new tag, which represent the
     * rectangle boundaries.
     *
     * @param listener
     */
    public void addNewTagListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes itself from mouse events on the source image.
     */
    public void disconnect() {
        this.disconnectRunnable.run();
    }

    /**
     * Reset the rectangle to default dimensions.
     */
    private void defaultSettings() {
        setWidth(0);
        setHeight(0);
        setVisible(false);
    }
}
