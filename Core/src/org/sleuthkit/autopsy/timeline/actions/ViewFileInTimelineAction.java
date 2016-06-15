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
package org.sleuthkit.autopsy.timeline.actions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * An action to prompt the user to pick an timestamp/event associated with the
 * given file and show it in the Timeline List View
 */
public final class ViewFileInTimelineAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final AbstractFile file;

    @NbBundle.Messages({"ViewFileInTimelineAction.fileSource.displayName=View File in Timeline... ",
        "ViewFileInTimelineAction.artifactSource.displayName=View Source File in Timeline... "})
    public ViewFileInTimelineAction(AbstractFile file, boolean isArtifactSource) {
        super(isArtifactSource
                ? Bundle.ViewFileInTimelineAction_artifactSource_displayName()
                : Bundle.ViewFileInTimelineAction_fileSource_displayName());
        this.file = file;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SystemAction.get(OpenTimelineAction.class).showFileInTimeline(file);
    }
}
