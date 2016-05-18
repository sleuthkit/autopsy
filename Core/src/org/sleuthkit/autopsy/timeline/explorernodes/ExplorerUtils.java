/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.explorernodes;

import javafx.scene.Node;
import javafx.scene.Parent;
import org.openide.explorer.ExplorerManager;

/**
 *
 */
public class ExplorerUtils {

    static public ExplorerManager find(Node node) {

        if (node instanceof ExplorerManager.Provider) {
            return ((ExplorerManager.Provider) node).getExplorerManager();
        } else {
            Parent parent = node.getParent();
            if (parent == null) {
                System.out.println(node.getScene().getWindow().getClass());
                return null;
            } else {
                return find(parent);
            }
        }
    }
}
