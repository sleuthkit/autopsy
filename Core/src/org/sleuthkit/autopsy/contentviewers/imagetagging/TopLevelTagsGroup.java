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
import java.util.ArrayList;
import java.util.Collection;
import javafx.event.Event;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

/**
 * Top level group containing Image and all existing tags.
 */
public final class TopLevelTagsGroup extends Group {

    private final EventDispatchChainImpl NO_OP_CHAIN = new EventDispatchChainImpl();
    private final Collection<FocusChangeListener> listeners;

    private Node lastFocus;
    private final ImageView baseImage;

    public TopLevelTagsGroup(ImageView image) {
        super(image);
        baseImage = image;
        listeners = new ArrayList<>();
        
        //Manage focus, such that only one child can be set at a time.
        this.addEventFilter(MouseEvent.MOUSE_PRESSED, (MouseEvent e) -> {
            if (!e.isPrimaryButtonDown()) {
                return;
            }

            Node topLevelChild = e.getPickResult().getIntersectedNode();
            while (!this.getChildren().contains(topLevelChild)) {
                topLevelChild = topLevelChild.getParent();
            }

            requestFocus(topLevelChild);
        });
    }

    public void addFocusChangeListener(FocusChangeListener fcl) {
        listeners.add(fcl);
    }

    private void resetFocus(Node n) {
        n.getEventDispatcher().dispatchEvent(new Event(ControlType.NOT_FOCUSED), NO_OP_CHAIN);
        if(n instanceof StoredTag) {
            listeners.forEach((listener) -> {
                listener.focusChanged(new FocusChangeEvent(this, ControlType.NOT_FOCUSED, (StoredTag) n));
            });
        }
    }

    public void deleteNode(Node n) {
        if (lastFocus == n) {
            resetFocus(n);
            lastFocus = null;
        }
        n.getEventDispatcher().dispatchEvent(new Event(ControlType.DELETE), NO_OP_CHAIN);
        this.getChildren().remove(n);
    }

    public void requestFocus(Node n) {
        if (lastFocus == n) {
            return;
        } else if (lastFocus != null && lastFocus != n) {
            resetFocus(lastFocus);
        }

        n.getEventDispatcher().dispatchEvent(new Event(ControlType.FOCUSED), NO_OP_CHAIN);
        if(n instanceof StoredTag) {
            listeners.forEach((listener) -> {
                listener.focusChanged(new FocusChangeEvent(this, ControlType.FOCUSED, (StoredTag) n));
            });
        }

        if (n != baseImage) {
            n.toFront();
        }

        lastFocus = n;
    }
}
