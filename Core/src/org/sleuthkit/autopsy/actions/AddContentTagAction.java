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
package org.sleuthkit.autopsy.actions;

import java.util.Collection;
import java.util.HashSet;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to apply tags to content.
 */
@NbBundle.Messages({
    "AddContentTagAction.singularTagFile=Tag File",
    "AddContentTagAction.pluralTagFile=Tag Files",
    "# {0} - fileName",
    "AddContentTagAction.unableToTag.msg=Unable to tag {0}, not a regular file.",
    "AddContentTagAction.cannotApplyTagErr=Cannot Apply Tag",
    "# {0} - fileName",
    "AddContentTagAction.unableToTag.msg2=Unable to tag {0}.",
    "AddContentTagAction.taggingErr=Tagging Error",
    "# {0} - fileName", "# {1} - tagName",
    "AddContentTagAction.tagExists={0} has been tagged as {1}. Cannot reapply the same tag."
})
public class AddContentTagAction extends AddTagAction {

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static AddContentTagAction instance;

    public static synchronized AddContentTagAction getInstance() {
        if (null == instance) {
            instance = new AddContentTagAction();
        }
        return instance;
    }

    private AddContentTagAction() {
        super("");
    }

    @Override
    protected String getActionDisplayName() {
        String singularTagFile = NbBundle.getMessage(this.getClass(), "AddContentTagAction.singularTagFile");
        String pluralTagFile = NbBundle.getMessage(this.getClass(), "AddContentTagAction.pluralTagFile");
        return Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size() > 1 ? pluralTagFile : singularTagFile;
    }

    @Override
    protected void addTag(TagName tagName, String comment) {
        /*
         * The documentation for Lookup.lookupAll() explicitly says that the
         * collection it returns may contain duplicates. Within this invocation
         * of addTag(), we don't want to tag the same AbstractFile more than
         * once, so we dedupe the AbstractFiles by stuffing them into a HashSet.
         *
         * We don't want VirtualFile and DerivedFile objects to be tagged.
         */
        final Collection<AbstractFile> selectedFiles = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));

        new Thread(() -> {
            for (AbstractFile file : selectedFiles) {
                try {
                    // Handle the special cases of current (".") and parent ("..") directory entries.
                    if (file.getName().equals(".")) {
                        Content parentFile = file.getParent();
                        if (parentFile instanceof AbstractFile) {
                            file = (AbstractFile) parentFile;
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                        NbBundle.getMessage(this.getClass(),
                                                "AddContentTagAction.unableToTag.msg",
                                                parentFile.getName()),
                                        NbBundle.getMessage(this.getClass(),
                                                "AddContentTagAction.cannotApplyTagErr"),
                                        JOptionPane.WARNING_MESSAGE);
                            });
                            continue;
                        }
                    } else if (file.getName().equals("..")) {
                        Content parentFile = file.getParent();
                        if (parentFile instanceof AbstractFile) {
                            parentFile = (AbstractFile) ((AbstractFile) parentFile).getParent();
                            if (parentFile instanceof AbstractFile) {
                                file = (AbstractFile) parentFile;
                            } else {
                                final Content parentFileCopy = parentFile;
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                            NbBundle.getMessage(this.getClass(),
                                                    "AddContentTagAction.unableToTag.msg",
                                                    parentFileCopy.getName()),
                                            NbBundle.getMessage(this.getClass(),
                                                    "AddContentTagAction.cannotApplyTagErr"),
                                            JOptionPane.WARNING_MESSAGE);
                                });
                                continue;
                            }
                        } else {
                            final Content parentFileCopy = parentFile;
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                        NbBundle.getMessage(this.getClass(),
                                                "AddContentTagAction.unableToTag.msg",
                                                parentFileCopy.getName()),
                                        NbBundle.getMessage(this.getClass(),
                                                "AddContentTagAction.cannotApplyTagErr"),
                                        JOptionPane.WARNING_MESSAGE);
                            });
                            continue;
                        }
                    }

                    Case.getOpenCase().getServices().getTagsManager().addContentTag(file, tagName, comment);
                } catch (TskCoreException | NoCurrentCaseException ex) {
                    Logger.getLogger(AddContentTagAction.class.getName()).log(Level.SEVERE, "Error tagging result", ex); //NON-NLS
                    AbstractFile fileCopy = file;
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                NbBundle.getMessage(this.getClass(),
                                        "AddContentTagAction.unableToTag.msg2",
                                        fileCopy.getName()),
                                NbBundle.getMessage(this.getClass(), "AddContentTagAction.taggingErr"),
                                JOptionPane.ERROR_MESSAGE);
                    });
                    break;
                }
            }
        }).start();
    }
}
