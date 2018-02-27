/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * This creates a single context menu item for adding a new filename extension
 * to the extension list for the MIME type of the selected node.
 */
@ServiceProvider(service = ContextMenuActionsProvider.class)
public class FileExtMismatchContextMenuActionsProvider implements ContextMenuActionsProvider {
    private static final Logger logger = Logger.getLogger(FileExtMismatchContextMenuActionsProvider.class.getName());

    @Override
    public List<Action> getActions() {
        ArrayList<Action> actions = new ArrayList<>();

        // Ignore if file ingest is in progress.
        if (!IngestManager.getInstance().isIngestRunning()) {

            final Collection<? extends BlackboardArtifact> selectedArts = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class);

            // Prevent multiselect
            if (selectedArts.size() == 1) {

                for (BlackboardArtifact nodeArt : selectedArts) {

                    // Only for mismatch results
                    if (nodeArt.getArtifactTypeName().equals("TSK_EXT_MISMATCH_DETECTED")) { //NON-NLS
                        String mimeTypeStr = "";
                        String extStr = "";

                        AbstractFile af = Utilities.actionsGlobalContext().lookup(AbstractFile.class);
                        
                        if (af != null) {
                            int i = af.getName().lastIndexOf(".");
                            if ((i > -1) && ((i + 1) < af.getName().length())) {
                                extStr = af.getName().substring(i + 1).toLowerCase();
                            }
                            mimeTypeStr = af.getMIMEType();
                            if (mimeTypeStr == null) {
                                mimeTypeStr = "";
                            }

                            if (!extStr.isEmpty() && !mimeTypeStr.isEmpty()) {
                                // Limit max size so the context window doesn't get ridiculously wide
                                if (extStr.length() > 10) {
                                    extStr = extStr.substring(0, 9);
                                }
                                if (mimeTypeStr.length() > 40) {
                                    mimeTypeStr = mimeTypeStr.substring(0, 39);
                                }
                                String menuItemStr = NbBundle.getMessage(this.getClass(),
                                        "FileExtMismatchContextMenuActionsProvider.menuItemStr",
                                        extStr, mimeTypeStr);

                                // Check if already added
                                HashMap<String, Set<String>> editableMap;
                                try {
                                    FileExtMismatchSettings settings = FileExtMismatchSettings.readSettings();
                                    editableMap = settings.getMimeTypeToExtsMap();
                                    actions.add(new AddFileExtensionAction(menuItemStr, extStr, mimeTypeStr, settings));
                                    Set<String> editedExtensions = editableMap.get(mimeTypeStr);
                                    if (editedExtensions.contains(extStr)) {
                                        // Informs the user that they have already added this extension to this MIME type
                                        actions.get(0).setEnabled(false);
                                    }
                                } catch (FileExtMismatchSettings.FileExtMismatchSettingsException ex) {
                                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                            NbBundle.getMessage(this.getClass(), "AddFileExtensionAction.msgDlg.msg2"),
                                            NbBundle.getMessage(this.getClass(), "AddFileExtensionAction.msgDlg.title"),
                                            JOptionPane.ERROR_MESSAGE);
                                    logger.log(Level.WARNING, "File extension mismatch settings could not be read, extensions update not available.", ex);
                                }
                            }
                        }
                    }
                }
            }
        }

        return actions;
    }
}
