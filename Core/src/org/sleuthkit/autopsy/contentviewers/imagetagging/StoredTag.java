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

import com.sun.javafx.event.EventDispatchChainImpl;
import javafx.collections.ListChangeListener;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 *
 * @author dsmyda
 */
public final class StoredTag extends Group {

    private final EventDispatchChainImpl ALL_CHILDREN;
    private final PhysicalTag physicalTag;

    public StoredTag(ImageView image, double x, double y, double x1, double y1) {
        ALL_CHILDREN = new EventDispatchChainImpl();

        this.getChildren().addListener((ListChangeListener<Node>) change -> {
            change.next();
            change.getAddedSubList().forEach((node) -> ALL_CHILDREN.append(node.getEventDispatcher()));
        });

        double min = Math.min(image.getImage().getWidth(), image.getImage().getHeight());
        double lineThicknessPixels = min * 1.5 / 100.0;
        physicalTag = new PhysicalTag(image);
        physicalTag.setStrokeWidth(lineThicknessPixels);

        EditHandle bottomLeft = new EditHandle();
        bottomLeft.setPosition(Position.bottom(), Position.left());
        bottomLeft.setDrag(Drag.bottom(), Drag.left());

        EditHandle bottomRight = new EditHandle();
        bottomRight.setPosition(Position.bottom(), Position.right());
        bottomRight.setDrag(Drag.bottom(), Drag.right());

        EditHandle topLeft = new EditHandle();
        topLeft.setPosition(Position.top(), Position.left());
        topLeft.setDrag(Drag.top(), Drag.left());

        EditHandle topRight = new EditHandle();
        topRight.setPosition(Position.top(), Position.right());
        topRight.setDrag(Drag.top(), Drag.right());

        EditHandle bottomMiddle = new EditHandle();
        bottomMiddle.setPosition(Position.bottom(), Position.xMiddle());
        bottomMiddle.setDrag(Drag.bottom());

        EditHandle topMiddle = new EditHandle();
        topMiddle.setPosition(Position.top(), Position.xMiddle());
        topMiddle.setDrag(Drag.top());

        EditHandle rightMiddle = new EditHandle();
        rightMiddle.setPosition(Position.right(), Position.yMiddle());
        rightMiddle.setDrag(Drag.right());

        EditHandle leftMiddle = new EditHandle();
        leftMiddle.setPosition(Position.left(), Position.yMiddle());
        leftMiddle.setDrag(Drag.left());

        this.getChildren().addAll(physicalTag, bottomLeft, bottomRight, topLeft,
                topRight, bottomMiddle, topMiddle, rightMiddle, leftMiddle);

        //Position the tag on the image. The edit knobs will be notified of 
        //the new coords and adjust themselves.
        physicalTag.setX(x);
        physicalTag.setY(y);
        physicalTag.setWidth(x1 - x);
        physicalTag.setHeight(y1 - y);

        this.addEventHandler(ControlType.NOT_FOCUSED, event -> ALL_CHILDREN.dispatchEvent(event));
        this.addEventHandler(ControlType.FOCUSED, event -> ALL_CHILDREN.dispatchEvent(event));
        this.addEventHandler(ControlType.DELETE, event -> ALL_CHILDREN.dispatchEvent(event));
    }

    class PhysicalTag extends Rectangle {

        private final ImageView image;

        public PhysicalTag(ImageView image) {
            this.setStroke(Color.RED);
            this.setFill(Color.RED.deriveColor(0, 0, 0, 0));

            this.addEventHandler(ControlType.NOT_FOCUSED, event -> this.setOpacity(1));
            this.addEventHandler(ControlType.FOCUSED, event -> this.setOpacity(0.5));

            this.addEventHandler(ControlType.DELETE, event -> {
                this.setVisible(false);
                //TODO - delete tag from persistent storage here.
            });

            this.image = image;
        }

        private void save() {
            //TODO - persist tag
        }

        private ImageView getUnderlyingImage() {
            return image;
        }
    }

    class EditHandle extends Circle {

        public EditHandle() {
            super(physicalTag.getStrokeWidth(), physicalTag.getStroke());
            this.setVisible(false);

            //Manipulate the parent rectangle when this knob is dragged.
            this.addEventHandler(ControlType.NOT_FOCUSED, event -> this.setVisible(false));
            this.addEventHandler(ControlType.FOCUSED, event -> this.setVisible(true));
            this.addEventHandler(ControlType.DELETE, event -> this.setVisible(false));
            this.setOnDragDetected(event -> this.getParent().setCursor(Cursor.CLOSED_HAND));
            this.setOnMouseReleased(event -> {
                this.getParent().setCursor(Cursor.DEFAULT);
                physicalTag.save();
            });
        }

        public void setPosition(Position... vals) {
            for (Position pos : vals) {
                physicalTag.widthProperty().addListener((obs, oldVal, newVal) -> pos.set(physicalTag, this));
                physicalTag.heightProperty().addListener((obs, oldVal, newVal) -> pos.set(physicalTag, this));
            }
        }

        public void setDrag(Drag... vals) {
            this.setOnMouseDragged((event) -> {
                for (Drag drag : vals) {
                    drag.perform(physicalTag, event, physicalTag.getUnderlyingImage());
                }
            });
        }
    }

    static interface Position {

        void set(Rectangle parent, Circle knob);

        static Position left() {
            return (parent, knob) -> knob.centerXProperty().bind(parent.xProperty());
        }

        static Position right() {
            return (parent, knob) -> knob.centerXProperty().bind(parent.xProperty().add(parent.getWidth()));
        }

        static Position top() {
            return (parent, knob) -> knob.centerYProperty().bind(parent.yProperty());
        }

        static Position bottom() {
            return (parent, knob) -> knob.centerYProperty().bind(parent.yProperty().add(parent.getHeight()));
        }

        static Position xMiddle() {
            return (parent, knob) -> knob.centerXProperty().bind(parent.xProperty().add(parent.getWidth() / 2));
        }

        static Position yMiddle() {
            return (parent, knob) -> knob.centerYProperty().bind(parent.yProperty().add(parent.getHeight() / 2));
        }
    }

    static interface Drag {

        void perform(Rectangle parent, MouseEvent event, ImageView image);

        static Drag bottom() {
            return (parent, event, image) -> {
                double deltaY = event.getY() - parent.getY();
                if (deltaY > 0 && event.getY()
                        < image.getY() + image.getImage().getHeight()
                        - parent.getStrokeWidth() / 2) {
                    parent.setHeight(deltaY);
                }
            };
        }

        static Drag top() {
            return (parent, event, image) -> {
                double deltaY = parent.getY() + parent.getHeight() - event.getY();
                if (deltaY < parent.getY() + parent.getHeight() && event.getY()
                        > image.getY() + parent.getStrokeWidth() / 2 && deltaY > 0) {
                    parent.setHeight(deltaY);
                    parent.setY(event.getY());
                }
            };
        }

        static Drag left() {
            return (parent, event, image) -> {
                double deltaX = parent.getX() + parent.getWidth() - event.getX();
                if (deltaX < parent.getX() + parent.getWidth()
                        && event.getX() > image.getX() + parent.getStrokeWidth() / 2
                        && deltaX > 0) {
                    parent.setWidth(deltaX);
                    parent.setX(event.getX());
                }
            };
        }

        static Drag right() {
            return (parent, event, image) -> {
                double deltaX = event.getX() - parent.getX();
                if (deltaX > 0 && event.getX() < image.getX()
                        + image.getImage().getWidth() - parent.getStrokeWidth() / 2) {
                    parent.setWidth(deltaX);
                }
            };
        }
    }
}
