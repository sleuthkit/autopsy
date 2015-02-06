/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.scene.image.WritableImage;
import javafx.stage.DirectoryChooser;
import javafx.util.Pair;
import javax.imageio.ImageIO;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.TskCoreException;

/**
 */
public class SaveSnapshot extends Action {

    private static final String HTML_EXT = ".html";

    private static final String REPORT_IMAGE_EXTENSION = ".png";

    private static final Logger LOGGER = Logger.getLogger(SaveSnapshot.class.getName());

    private final TimeLineController controller;

    private final WritableImage snapshot;

    public SaveSnapshot(TimeLineController controller, WritableImage snapshot) {
        super(NbBundle.getMessage(SaveSnapshot.class, "SaveSnapshot.action.name.text"));
        this.controller = controller;
        this.snapshot = snapshot;
        setEventHandler(new Consumer<ActionEvent>() {

            @Override
            public void accept(ActionEvent t) {
                //choose location/name
                DirectoryChooser fileChooser = new DirectoryChooser();
                fileChooser.setTitle(NbBundle.getMessage(this.getClass(), "SaveSnapshot.fileChoose.title.text"));
                fileChooser.setInitialDirectory(new File(Case.getCurrentCase().getCaseDirectory() + File.separator + "Reports")); // NON-NLS
                File outFolder = fileChooser.showDialog(null);
                if (outFolder == null) {
                    return;
                }
                outFolder.mkdir();
                String name = outFolder.getName();

                //gather metadata
                List<Pair<String, String>> reportMetaData = new ArrayList<>();

                reportMetaData.add(new Pair<>("Case", Case.getCurrentCase().getName())); // NON-NLS

                ZoomParams get = controller.getEventsModel().getRequestedZoomParamters().get();
                reportMetaData.add(new Pair<>("Time Range", get.getTimeRange().toString())); // NON-NLS
                reportMetaData.add(new Pair<>("Description Level of Detail", get.getDescrLOD().getDisplayName())); // NON-NLS
                reportMetaData.add(new Pair<>("Event Type Zoom Level", get.getTypeZoomLevel().getDisplayName())); // NON-NLS
                reportMetaData.add(new Pair<>("Filters", get.getFilter().getHTMLReportString())); // NON-NLS

                //save snapshot as png
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", new File(outFolder.getPath() + File.separator + outFolder.getName() + REPORT_IMAGE_EXTENSION)); // NON-NLS
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "failed to write snapshot to disk", ex); // NON-NLS
                    return;
                }

                //build html string
                StringBuilder wrapper = new StringBuilder();
                wrapper.append("<html>\n<head>\n\t<title>").append("timeline snapshot").append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n</head>\n<body>\n"); // NON-NLS
                wrapper.append("<div id=\"content\">\n<h1>").append(outFolder.getName()).append("</h1>\n"); // NON-NLS
                wrapper.append("<img src = \"").append(outFolder.getName()).append(REPORT_IMAGE_EXTENSION + "\" alt = \"snaphot\">"); // NON-NLS
                wrapper.append("<table>\n"); // NON-NLS
                for (Pair<String, String> pair : reportMetaData) {
                    wrapper.append("<tr><td>").append(pair.getKey()).append(": </td><td>").append(pair.getValue()).append("</td></tr>\n"); // NON-NLS
                }
                wrapper.append("</table>\n"); // NON-NLS
                wrapper.append("</div>\n</body>\n</html>"); // NON-NLS

                //write html wrapper
                try (Writer htmlWriter = new FileWriter(new File(outFolder, name + HTML_EXT))) {
                    htmlWriter.write(wrapper.toString());
                } catch (FileNotFoundException ex) {
                    LOGGER.log(Level.WARNING, "failed to open html wrapper file for writing ", ex); // NON-NLS
                    return;
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "failed to write html wrapper file", ex); // NON-NLS
                    return;
                }

                //copy css
                try (InputStream resource = this.getClass().getResourceAsStream("/org/sleuthkit/autopsy/timeline/index.css")) { // NON-NLS
                    Files.copy(resource, Paths.get(outFolder.getPath(), "index.css")); // NON-NLS
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "failed to copy css file", ex); // NON-NLS
                }

                //add html file as report to case
                try {
                    Case.getCurrentCase().addReport(outFolder.getPath() + File.separator + outFolder.getName() + HTML_EXT, "Timeline", outFolder.getName() + HTML_EXT); // NON-NLS
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "failed add html wrapper as a report", ex); // NON-NLS
                }
            }
        });
    }
}
