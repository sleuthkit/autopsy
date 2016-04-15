/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.actions;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.swing.SwingUtilities;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 *
 */
public class RebuildDataBase extends Action {

    private static final Image DB_REFRESH = new Image("org/sleuthkit/autopsy/timeline/images/database_refresh.png");

    @NbBundle.Messages({"RebuildDataBase.text=Update"})
    public RebuildDataBase(TimeLineController controller) {
        super(Bundle.RebuildDataBase_text());

        setGraphic(new ImageView(DB_REFRESH));
        setEventHandler(actionEvent -> SwingUtilities.invokeLater(controller::confirmOutOfDateRebuildIfWindowOpen));
    }
}
