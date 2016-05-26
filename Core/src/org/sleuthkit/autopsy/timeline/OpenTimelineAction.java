/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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

import java.awt.Component;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.Installer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.timeline.Timeline")
@ActionRegistration(displayName = "#CTL_MakeTimeline", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 100)})
public class OpenTimelineAction extends CallableSystemAction implements Presenter.Toolbar {

    private static final Logger LOGGER = Logger.getLogger(OpenTimelineAction.class.getName());

    private static final boolean fxInited = Installer.isJavaFxInited();

    private static TimeLineController timeLineController = null;
    
    private JButton toolbarButton = new JButton();

    synchronized static void invalidateController() {
        timeLineController = null;
    }

    @Override
    public boolean isEnabled() {
        /**
         * we disabled the check to hasData() because if it is executed while a
         * data source is being added, it blocks the edt
         */
        return Case.isCaseOpen() && fxInited;// && Case.getCurrentCase().hasData();
    }

    @NbBundle.Messages({
        "OpenTimelineAction.settingsErrorMessage=Failed to initialize timeline settings.",
        "OpenTimeLineAction.msgdlg.text=Could not create timeline, there are no data sources."})
    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void performAction() {
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
                timeLineController.openTimeLine();
            } catch (IOException iOException) {
                MessageNotifyUtil.Message.error(Bundle.OpenTimelineAction_settingsErrorMessage());
                LOGGER.log(Level.SEVERE, "Failed to initialize per case timeline settings.", iOException);
            }
        } catch (IllegalStateException e) {
            //there is no case...   Do nothing.
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(OpenTimelineAction.class, "CTL_MakeTimeline");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
    
    /**
     * Returns the toolbar component of this action
     *
     * @return component the toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon("images//20140521121247760_easyicon_net_32_colorized.png"); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(this.getName());
        return toolbarButton;
    }
}
