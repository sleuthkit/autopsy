/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;
import org.sleuthkit.autopsy.timeline.TimeLineController;

public interface ContextMenuProvider {

    TimeLineController getController();

    void clearContextMenu();

    ContextMenu getContextMenu(MouseEvent m);
}
