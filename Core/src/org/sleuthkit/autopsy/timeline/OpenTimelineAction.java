/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import java.io.IOException;
import java.util.logging.Level;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.Installer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * An Action that opens the Timeline window. Has methods to open the window in
 * various specific states (e.g., showing a specific artifact in the List View)
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.timeline.Timeline")
@ActionRegistration(displayName = "#CTL_MakeTimeline", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 100)})
public class OpenTimelineAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(OpenTimelineAction.class.getName());

    private static final boolean FX_INITED = Installer.isJavaFxInited();

    private static TimeLineController timeLineController = null;

    /**
     * Invalidate the reference to the controller so that a new will will be
     * instantiated the next time this action is invoked
     */
    synchronized static void invalidateController() {
        timeLineController = null;
    }

    @Override
    public boolean isEnabled() {
        /**
         * we disabled the check to hasData() because if it is executed while a
         * data source is being added, it blocks the edt
         */
        return Case.isCaseOpen() && FX_INITED;// && Case.getCurrentCase().hasData();
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void performAction() {
        showTimeline();
    }

    @NbBundle.Messages({
        "OpenTimelineAction.settingsErrorMessage=Failed to initialize timeline settings.",
        "OpenTimeLineAction.msgdlg.text=Could not create timeline, there are no data sources."})
    synchronized private void showTimeline(AbstractFile file, BlackboardArtifact artifact) {
        try {
            Case currentCase = Case.getCurrentCase();
            if (currentCase.hasData() == false) {
                MessageNotifyUtil.Message.info(Bundle.OpenTimeLineAction_msgdlg_text());
                LOGGER.log(Level.INFO, "Could not create timeline, there are no data sources.");// NON-NLS
                return;
            }
            try {
                if (timeLineController == null) {
                    timeLineController = new TimeLineController(currentCase);
                } else if (timeLineController.getAutopsyCase() != currentCase) {
                    timeLineController.shutDownTimeLine();
                    timeLineController = new TimeLineController(currentCase);
                }

                timeLineController.openTimeLine(file, artifact);

            } catch (IOException iOException) {
                MessageNotifyUtil.Message.error(Bundle.OpenTimelineAction_settingsErrorMessage());
                LOGGER.log(Level.SEVERE, "Failed to initialize per case timeline settings.", iOException);
            }
        } catch (IllegalStateException e) {
            //there is no case...   Do nothing.
        }
    }

    /**
     * Open the Timeline window with the default initial view.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void showTimeline() {
        showTimeline(null, null);
    }

    /**
     * Open the Timeline window with the given file selected in ListView. The
     * user will be prompted to choose which timestamp to use for the file, and
     * how much time to show around it.
     *
     * @param file The AbstractFile to show in the Timeline.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void showFileInTimeline(AbstractFile file) {
        showTimeline(file, null);
    }

    /**
     * Open the Timeline window with the given artifact selected in ListView.
     * The how much time to show around it.
     *
     * @param artifact The BlackboardArtifact to show in the Timeline.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void showArtifactInTimeline(BlackboardArtifact artifact) {
        showTimeline(null, artifact);
    }

    @Override
    @NbBundle.Messages("OpenTimelineAction.displayName=Timeline")
    public String getName() {
        return Bundle.OpenTimelineAction_displayName();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}
