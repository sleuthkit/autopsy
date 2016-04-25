/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.actions;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.swing.SwingUtilities;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 * An action that rebuilds the events database to include any new results from
 * ingest.
 */
public class RebuildDataBase extends Action {

    private static final Image DB_REFRESH = new Image("org/sleuthkit/autopsy/timeline/images/database_refresh.png");

    @NbBundle.Messages({"RebuildDataBase.text=Update"})
    public RebuildDataBase(TimeLineController controller) {
        super(Bundle.RebuildDataBase_text());

        setGraphic(new ImageView(DB_REFRESH));
        setEventHandler(actionEvent -> SwingUtilities.invokeLater(() -> controller.rebuildIfWindowOpen(null)));
    }
}
