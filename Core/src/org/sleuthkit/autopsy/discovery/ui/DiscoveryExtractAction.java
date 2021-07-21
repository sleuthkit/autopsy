/*
 * Autopsy
 *
 * Copyright 2019-2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this content except in compliance with the License.
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
package org.sleuthkit.autopsy.discovery.ui;

import org.sleuthkit.autopsy.directorytree.actionhelpers.ExtractActionHelper;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.HashSet;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Extracts AbstractFiles to a location selected by the user.
 */
final class DiscoveryExtractAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final Collection<AbstractFile> files = new HashSet<>();

    /**
     * Construct a new DiscoveryExtractAction for the extraction of the
     * specified files.
     *
     * @param selectedFiles The files to extract from the current case.
     */
    @NbBundle.Messages({"DiscoveryExtractAction.title.extractFiles.text=Extract File"})
    DiscoveryExtractAction(Collection<AbstractFile> selectedFiles) {
        super(Bundle.DiscoveryExtractAction_title_extractFiles_text());
        files.addAll(selectedFiles);
    }

    /**
     * Asks user to choose destination, then extracts content to destination
     * (recursing on directories).
     *
     * @param e The action event.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        ExtractActionHelper extractor = new ExtractActionHelper();
        extractor.extract(e, files);

    }
}
