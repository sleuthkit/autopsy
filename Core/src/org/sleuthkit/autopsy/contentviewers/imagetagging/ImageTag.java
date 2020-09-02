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


// TODO: See JIRA-6693
//import com.sun.javafx.event.EventDispatchChainImpl;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javafx.collections.ListChangeListener;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager.ContentViewerTag;

/**
 * A tagged region displayed over an image. This class contains a "physical tag"
 * and 8 edit "handles". The physical tag is a plain old rectangle that defines
 * the tag boundaries. The edit handles serve two purposes. One is to represent
 * selection. All 8 edit handles will become visible overtop the physical tag
 * when the user clicks on the rectangle. The other purpose is to allow the user to edit
 * and manipulate the physical tag boundaries (hence the name, edit handle).
 * This class should be treated as a logical image tag.
 */
public final class ImageTag extends Group {

    // Used to tell the 8 edit handles to hide if this tag is no longer selected
//    private final EventDispatchChainImpl ALL_CHILDREN;
    
    private final PhysicalTag physicalTag;
    
    //Notifies listeners that the user has editted the tag boundaries
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    //The underlying presistent tag details that this image tag originates from
    private final ContentViewerTag<ImageTagRegion> appTag;

    public ImageTag(ContentViewerTag<ImageTagRegion> contentViewerTag, ImageView image) {
//        ALL_CHILDREN = new EventDispatchChainImpl();
        this.appTag = contentViewerTag;

//        this.getChildren().addListener((ListChangeListener<Node>) change -> {
//            change.next();
//            change.getAddedSubList().forEach((node) -> ALL_CHILDREN.append(node.getEventDispatcher()));
//        });

        ImageTagRegion details = contentViewerTag.getDetails();
        physicalTag = new PhysicalTag(details);

        //Defines the max allowable boundary that a user may drag any given handle.
        Boundary dragBoundary = (x, y) -> {
            double boundingX = image.getX();
            double boundingY = image.getY();
            double width = image.getImage().getWidth();
            double height = image.getImage().getHeight();

            return x > boundingX + details.getStrokeThickness() / 2
                    && x < boundingX + width - details.getStrokeThickness() / 2
                    && y > boundingY + details.getStrokeThickness() / 2
                    && y < boundingY + height - details.getStrokeThickness() / 2;
        };

        EditHandle bottomLeft = new EditHandle(physicalTag)
                .setPosition(Position.bottom(), Position.left())
                .setDrag(dragBoundary, Draggable.bottom(), Draggable.left());

        EditHandle bottomRight = new EditHandle(physicalTag)
                .setPosition(Position.bottom(), Position.right())
                .setDrag(dragBoundary, Draggable.bottom(), Draggable.right());

        EditHandle topLeft = new EditHandle(physicalTag)
                .setPosition(Position.top(), Position.left())
                .setDrag(dragBoundary, Draggable.top(), Draggable.left());

        EditHandle topRight = new EditHandle(physicalTag)
                .setPosition(Position.top(), Position.right())
                .setDrag(dragBoundary, Draggable.top(), Draggable.right());

        EditHandle bottomMiddle = new EditHandle(physicalTag)
                .setPosition(Position.bottom(), Position.xMiddle())
                .setDrag(dragBoundary, Draggable.bottom());

        EditHandle topMiddle = new EditHandle(physicalTag)
                .setPosition(Position.top(), Position.xMiddle())
                .setDrag(dragBoundary, Draggable.top());

        EditHandle rightMiddle = new EditHandle(physicalTag)
                .setPosition(Position.right(), Position.yMiddle())
                .setDrag(dragBoundary, Draggable.right());

        EditHandle leftMiddle = new EditHandle(physicalTag)
                .setPosition(Position.left(), Position.yMiddle())
                .setDrag(dragBoundary, Draggable.left());

        //The "logical" tag is the Group
        this.getChildren().addAll(physicalTag, bottomLeft, bottomRight, topLeft,
                topRight, bottomMiddle, topMiddle, rightMiddle, leftMiddle);

        Tooltip.install(this, new Tooltip(contentViewerTag.getContentTag()
                .getName().getDisplayName()));

//        this.addEventHandler(ImageTagControls.NOT_FOCUSED, event -> ALL_CHILDREN.dispatchEvent(event));
//        this.addEventHandler(ImageTagControls.FOCUSED, event -> ALL_CHILDREN.dispatchEvent(event));
    }

    /**
     * Add a new listener for edit events. These events are generated when a
     * user drags on one of the edit "knobs" of the tag.
     *
     * @param listener
     */
    public void subscribeToEditEvents(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }
    
    public double getWidth() {
        return physicalTag.getWidth();
    }
    
    public double getHeight() {
        return physicalTag.getHeight();
    }

    /**
     * Get the content viewer tag that this class represents.
     *
     * @return
     */
    public ContentViewerTag<ImageTagRegion> getContentViewerTag() {
        return appTag;
    }

    /**
     * Plain old rectangle that represents an unselected Image Tag
     */
    class PhysicalTag extends Rectangle {

        public PhysicalTag(ImageTagRegion details) {
            this.setStroke(Color.RED);
            this.setFill(Color.RED.deriveColor(0, 0, 0, 0));
            this.setStrokeWidth(details.getStrokeThickness());

            setX(details.getX());
            setY(details.getY());
            setWidth(details.getWidth());
            setHeight(details.getHeight());

            this.addEventHandler(ImageTagControls.NOT_FOCUSED, event -> this.setOpacity(1));
            this.addEventHandler(ImageTagControls.FOCUSED, event -> this.setOpacity(0.5));
        }

        /**
         * Builds a portable description of the tag region.
         *
         * @return
         */
        public ImageTagRegion getState() {
            return new ImageTagRegion()
                    .setX(this.getX())
                    .setY(this.getY())
                    .setWidth(this.getWidth())
                    .setHeight(this.getHeight())
                    .setStrokeThickness(this.getStrokeWidth());
        }
    }

    /**
     * Draggable "knob" used to manipulate the physical tag boundaries.
     */
    class EditHandle extends Circle {

        private final PhysicalTag parent;

        public EditHandle(PhysicalTag parent) {
            this.setVisible(false);

            //Hide when the tag is not selected.
            this.addEventHandler(ImageTagControls.NOT_FOCUSED, event -> this.setVisible(false));
            this.addEventHandler(ImageTagControls.FOCUSED, event -> this.setVisible(true));

            this.setRadius(parent.getStrokeWidth());
            this.setFill(parent.getStroke());

            this.setOnDragDetected(event -> {
                this.getParent().setCursor(Cursor.CLOSED_HAND);
            });

            this.setOnMouseReleased(event -> {
                this.getParent().setCursor(Cursor.DEFAULT);
                pcs.firePropertyChange(new PropertyChangeEvent(this, "Tag Edit", null, parent.getState()));
            });

            this.parent = parent;
        }

        /**
         * Sets the positioning of this edit handle on the physical tag.
         *
         * @param vals
         * @return
         */
        public EditHandle setPosition(Position... vals) {
            for (Position pos : vals) {
                parent.widthProperty().addListener((obs, oldVal, newVal) -> pos.set(parent, this));
                parent.heightProperty().addListener((obs, oldVal, newVal) -> pos.set(parent, this));
                pos.set(parent, this);
            }
            return this;
        }

        /**
         * Sets the drag capabilities for manipulating the physical tag.
         *
         * @param bounds
         * @param vals
         * @return
         */
        public EditHandle setDrag(Boundary bounds, Draggable... vals) {
            this.setOnMouseDragged((event) -> {
                for (Draggable drag : vals) {
                    drag.perform(parent, event, bounds);
                }
            });
            return this;
        }
    }

    /**
     * Position strategies for "sticking" to a location on the physical tag when
     * it is resized.
     */
    static interface Position {

        void set(PhysicalTag parent, Circle knob);

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

    /**
     * Defines the bounding box for which dragging is allowable.
     */
    @FunctionalInterface
    static interface Boundary {

        boolean isPointInBounds(double x, double y);
    }

    /**
     * Drag strategies for manipulating the physical tag from a given side of
     * the rectangle.
     */
    static interface Draggable {

        void perform(PhysicalTag parent, MouseEvent event, Boundary b);

        static Draggable bottom() {
            return (parent, event, bounds) -> {
                if (!bounds.isPointInBounds(event.getX(), event.getY())) {
                    return;
                }

                double deltaY = event.getY() - parent.getY();
                if (deltaY > 0) {
                    parent.setHeight(deltaY);
                }
            };
        }

        static Draggable top() {
            return (parent, event, bounds) -> {
                if (!bounds.isPointInBounds(event.getX(), event.getY())) {
                    return;
                }

                double deltaY = parent.getY() + parent.getHeight() - event.getY();
                if (deltaY < parent.getY() + parent.getHeight() && deltaY > 0) {
                    parent.setHeight(deltaY);
                    parent.setY(event.getY());
                }
            };
        }

        static Draggable left() {
            return (parent, event, bounds) -> {
                if (!bounds.isPointInBounds(event.getX(), event.getY())) {
                    return;
                }

                double deltaX = parent.getX() + parent.getWidth() - event.getX();
                if (deltaX < parent.getX() + parent.getWidth() && deltaX > 0) {
                    parent.setWidth(deltaX);
                    parent.setX(event.getX());
                }
            };
        }

        static Draggable right() {
            return (parent, event, bounds) -> {
                if (!bounds.isPointInBounds(event.getX(), event.getY())) {
                    return;
                }

                double deltaX = event.getX() - parent.getX();
                if (deltaX > 0) {
                    parent.setWidth(deltaX);
                }
            };
        }
    }
}
