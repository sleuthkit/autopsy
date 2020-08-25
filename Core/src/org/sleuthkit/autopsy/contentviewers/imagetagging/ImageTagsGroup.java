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
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

/**
 * Manages the focus and z-ordering of ImageTags. Only one image tag may be
 * selected at a time. Image tags show their 8 edit "handles" upon selection
 * (see ImageTag class for more details) and get the highest z-ordering to make
 * editing easier. This class is responsible for setting and dropping focus as
 * the user navigates from tag to tag. The ImageTag is treated as a logical
 * unit, however it's underlying representation consists of the physical
 * rectangle and the 8 edit handles. JavaFX will report selection on the Node
 * level (so either the Rectangle, or a singe edit handle), which makes keeping
 * the entire image tag in focus a non-trivial problem.
 */
public final class ImageTagsGroup extends Group {

    // TODO: See JIRA-6693
//    private final EventDispatchChain NO_OP_CHAIN = new EventDispatchChainImpl();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private volatile ImageTag currentFocus;

    public ImageTagsGroup(Node backDrop) {

        //Reset focus of current selection if the back drop has focus.
        backDrop.setOnMousePressed((mouseEvent) -> {
//            if (currentFocus != null) {
//                currentFocus.getEventDispatcher().dispatchEvent(
//                        new Event(ImageTagControls.NOT_FOCUSED), NO_OP_CHAIN);
//            }

            this.pcs.firePropertyChange(new PropertyChangeEvent(this,
                    ImageTagControls.NOT_FOCUSED.getName(), currentFocus, null));
            currentFocus = null;
        });

        //Set the focus of selected tag
        this.addEventFilter(MouseEvent.MOUSE_PRESSED, (MouseEvent e) -> {
            if (!e.isPrimaryButtonDown()) {
                return;
            }

            ImageTag selection = getTagToSelect(new Point2D(e.getX(), e.getY()));
            requestFocus(selection);
        });
    }

    /**
     * Subscribe to focus change events on Image tags.
     *
     * @param fcl PCL to be notified which Image tag has been selected.
     */
    public void addFocusChangeListener(PropertyChangeListener fcl) {
        this.pcs.addPropertyChangeListener(fcl);
    }

    /**
     * Get the image tag that current has focus.
     * 
     * @return ImageTag instance or null if no tag is in focus.
     */
    public ImageTag getFocus() {
        return currentFocus;
    }
    
    /**
     * Clears the current focus
     */
    public void clearFocus() {
        if(currentFocus != null) {
            resetFocus(currentFocus);
            currentFocus = null;
        }
    }
    
    /**
     * Find which tag to select on a user mouse press. If multiple tags are 
     * overlapping, pick the smallest one that is determined by the L + W of
     * the tag sides.
     * 
     * @param coordinate User mouse press location
     * @return The tag to give focus
     */
    private ImageTag getTagToSelect(Point2D coordinate) {
        ImageTag tagToSelect = null;
        double minTagSize = Double.MAX_VALUE;
        
        //Find all intersecting tags, select the absolute min based on L + W.
        for (Node node : this.getChildren()) {
            ImageTag tag = (ImageTag) node;
            double tagSize = tag.getWidth() + tag.getHeight();
            if (tag.contains(coordinate) && tagSize < minTagSize) {
                tagToSelect = tag;
                minTagSize = tagSize;
            }
        }
        
        return tagToSelect;
    }

    /**
     * Notifies the logical image tag that it is no longer in focus.
     * 
     * @param n 
     */
    private void resetFocus(ImageTag n) {
//        n.getEventDispatcher().dispatchEvent(new Event(ImageTagControls.NOT_FOCUSED), NO_OP_CHAIN);
        this.pcs.firePropertyChange(new PropertyChangeEvent(this, ImageTagControls.NOT_FOCUSED.getName(), n, null));
    }

    /**
     * Notifies the logical image that it is in focus.
     * 
     * @param n 
     */
    private void requestFocus(ImageTag n) {
        if (n == null || n.equals(currentFocus)) {
            return;
        } else if (currentFocus != null && !currentFocus.equals(n)) {
            resetFocus(currentFocus);
        }

//        n.getEventDispatcher().dispatchEvent(new Event(ImageTagControls.FOCUSED), NO_OP_CHAIN);
        this.pcs.firePropertyChange(new PropertyChangeEvent(this, ImageTagControls.FOCUSED.getName(), currentFocus, n));

        currentFocus = n;
        n.toFront();
    }
}
