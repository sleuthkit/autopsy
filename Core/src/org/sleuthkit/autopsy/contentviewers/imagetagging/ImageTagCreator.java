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

import java.util.ArrayList;
import java.util.Collection;
import javafx.event.EventHandler;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Creates image tags. This tool can be treated like any other JavaFX node.
 */
public final class ImageTagCreator extends Rectangle {

    //Origin of the drag event.
    private double rectangleOriginX, rectangleOriginY;

    //Rectangle lines should be 1.5% of the image. This level of thickness has
    //a good balance between visual acuity and loss of selection at the borders
    //of the image.
    private double lineThicknessAsPercent = 1.5;
    private final double minArea;  
    private final Collection<StoredTagListener> listeners;
    
    private final EventHandler<MouseEvent> mousePressed;
    private final EventHandler<MouseEvent> mouseDragged;
    private final EventHandler<MouseEvent> mouseReleased;

    /**
     * Adds tagging support to an image, where the 'tag' rectangle will be the
     * specified color.
     *
     * @param image Image to tag
     */
    public ImageTagCreator(ImageView image) {
        listeners = new ArrayList<>();

        setStroke(Color.RED);
        setFill(Color.RED.deriveColor(0, 0, 0, 0));

        //Calculate how many pixels the stroke width should be to guarentee
        //a consistent % of image consumed by the rectangle border.
        double min = Math.min(image.getImage().getWidth(), image.getImage().getHeight());
        double lineThicknessPixels = min * lineThicknessAsPercent / 100.0;
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
            if ((this.getWidth() - this.getStrokeWidth()) * 
                    (this.getHeight() - this.getStrokeWidth()) <= minArea) {
                defaultSettings();
                return;
            }          
            
            //Notify listeners
            StoredTagEvent newTagEvent = new StoredTagEvent(this, new StoredTag(image, this.getX(), this.getY(),
                    this.getX() + this.getWidth(), this.getY() + this.getHeight()));
            
            listeners.forEach((listener) -> {
                listener.newTagEvent(newTagEvent);
            });
            
            defaultSettings();
        };
        
        image.addEventHandler(MouseEvent.MOUSE_RELEASED, this.mouseReleased);
        this.addEventHandler(ControlType.NOT_FOCUSED, (event) -> {
            defaultSettings();
            image.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleased);
            image.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDragged);
            image.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed);
        });
        
        this.addEventHandler(ControlType.DELETE, event -> {
            defaultSettings();
            image.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleased);
            image.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDragged);
            image.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed);
        }); 
    }
    
    public void addNewTagListener(StoredTagListener listener) {
        listeners.add(listener);
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
