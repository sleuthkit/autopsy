/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 - 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.snapshot;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import javax.imageio.ImageIO;
import org.joda.time.format.DateTimeFormat;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.report.uisnapshot.UiSnapShotReportWriter;
import org.sleuthkit.autopsy.timeline.zooming.EventsModelParams;

/**
 * Generate and write the Timeline snapshot report to disk.
 */
public class SnapShotReportWriter extends UiSnapShotReportWriter{

    private final EventsModelParams zoomState;
    private final BufferedImage image;

    /**
     * Constructor
     *
     * @param currentCase      The Case to write a report for.
     * @param reportFolderPath The Path to the folder that will contain the
     *                         report.
     * @param reportName       The name of the report.
     * @param zoomState        The ZoomState in effect when the snapshot was
     *                         taken.
     * @param generationDate   The generation Date of the report.
     * @param snapshot         A snapshot of the view to include in the report.
     */
    public SnapShotReportWriter(Case currentCase, Path reportFolderPath, String reportName, EventsModelParams zoomState, Date generationDate, BufferedImage snapshot) {
        super(currentCase, reportFolderPath, reportName, generationDate);
        this.zoomState = zoomState;
        this.image = snapshot;
    }

    /**
     * Generate and write the html page that shows the snapshot and the ZoomState
     *
     * @throws IOException If there is a problem writing the html file to disk.
     */
    @Override
    protected void writeSnapShotHTMLFile() throws IOException {
        //save the snapshot in the report directory
        ImageIO.write(image, "png", getReportFolderPath().resolve("snapshot.png").toFile()); //NON-NLS

        //make a map of context objects to resolve template paramaters against
        HashMap<String, Object> snapShotContext = new HashMap<>();
        snapShotContext.put("reportTitle", getReportName()); //NON-NLS
        snapShotContext.put("startTime", zoomState.getTimeRange().getStart().toString(DateTimeFormat.fullDateTime())); //NON-NLS
        snapShotContext.put("endTime", zoomState.getTimeRange().getEnd().toString(DateTimeFormat.fullDateTime())); //NON-NLS
        snapShotContext.put("zoomState", zoomState); //NON-NLS

        fillTemplateAndWrite("/org/sleuthkit/autopsy/timeline/snapshot/snapshot_template.html", "Snapshot", snapShotContext, getReportFolderPath().resolve("snapshot.html")); //NON-NLS
    }
}
