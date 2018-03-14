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
package org.sleuthkit.autopsy.report.taggedhashes;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class plug in to the reporting infrastructure to provide a
 * convenient way to add content hashes to hash set databases.
 */
@ServiceProvider(service = GeneralReportModule.class)
public class AddTaggedHashesToHashDb implements GeneralReportModule {

    private AddTaggedHashesToHashDbConfigPanel configPanel;

    public AddTaggedHashesToHashDb() {
    }

    @Override
    public String getName() {
        return "Add Tagged Hashes";
    }

    @Override
    public String getDescription() {
        return "Adds hashes of tagged files to a hash set.";
    }

    @Override
    public String getRelativeFilePath() {
        return "";
    }

    @Override
    public void generateReport(String reportPath, ReportProgressPanel progressPanel) {
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(AddTaggedHashesToHashDb.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex);
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), "No open Case", "Exception while getting open case.", JOptionPane.ERROR_MESSAGE);
            return;
        }
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel("Adding hashes...");

        HashDb hashSet = configPanel.getSelectedHashDatabase();
        if (hashSet != null) {
            progressPanel.updateStatusLabel("Adding hashes to " + hashSet.getHashSetName() + " hash set...");

            TagsManager tagsManager = openCase.getServices().getTagsManager();
            List<TagName> tagNames = configPanel.getSelectedTagNames();
            ArrayList<String> failedExports = new ArrayList<>();
            for (TagName tagName : tagNames) {
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    break;
                }

                progressPanel.updateStatusLabel("Adding " + tagName.getDisplayName() + " hashes to " + hashSet.getHashSetName() + " hash set...");
                try {
                    List<ContentTag> tags = tagsManager.getContentTagsByTagName(tagName);
                    for (ContentTag tag : tags) {
                        // TODO: Currently only AbstractFiles have md5 hashes. Here only files matter. 
                        Content content = tag.getContent();
                        if (content instanceof AbstractFile) {
                            if (null != ((AbstractFile) content).getMd5Hash()) {
                                try {
                                    hashSet.addHashes(tag.getContent(), openCase.getDisplayName());
                                } catch (TskCoreException ex) {
                                    Logger.getLogger(AddTaggedHashesToHashDb.class.getName()).log(Level.SEVERE, "Error adding hash for obj_id = " + tag.getContent().getId() + " to hash set " + hashSet.getHashSetName(), ex);
                                    failedExports.add(tag.getContent().getName());
                                }
                            } else {
                                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), "Unable to add the " + (tags.size() > 1 ? "files" : "file") + " to the hash set. Hashes have not been calculated. Please configure and run an appropriate ingest module.", "Add to Hash Set Error", JOptionPane.ERROR_MESSAGE);
                                break;
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                    Logger.getLogger(AddTaggedHashesToHashDb.class.getName()).log(Level.SEVERE, "Error adding to hash set", ex);
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), "Error getting selected tags for case.", "Hash Export Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            if (!failedExports.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Failed to export hashes for the following files: ");
                for (int i = 0; i < failedExports.size(); ++i) {
                    errorMessage.append(failedExports.get(i));
                    if (failedExports.size() > 1 && i < failedExports.size() - 1) {
                        errorMessage.append(",");
                    }
                    if (i == failedExports.size() - 1) {
                        errorMessage.append(".");
                    }
                }
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), errorMessage.toString(), "Hash Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        progressPanel.setIndeterminate(false);
        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
    }

    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new AddTaggedHashesToHashDbConfigPanel();
        return configPanel;
    }
}
