/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Used by the Common Files search feature to encapsulate instances of a given
 * MD5s matched in the search. These nodes will be children of
 * <code>Md5Node</code>s.
 *
 * Use this type for files which are not in the current case, but from the
 * Central Repo. Contrast with <code>SleuthkitCase</code> which should be used
 * when the FileInstance was found in the case presently open in Autopsy.
 */
public class CentralRepoCommonAttributeInstanceNode extends DisplayableItemNode {

    private final CorrelationAttributeInstance crFile;
    private final AbstractCommonAttributeInstance.NODE_TYPE nodeType;

    CentralRepoCommonAttributeInstanceNode(CorrelationAttributeInstance content, AbstractCommonAttributeInstance.NODE_TYPE nodeType) {
        super(Children.LEAF, Lookups.fixed(content));
        this.crFile = content;
        this.setDisplayName(new File(this.crFile.getFilePath()).getName());
        this.nodeType = nodeType;
    }

    public CorrelationAttributeInstance getCorrelationAttributeInstance() {
        return this.crFile;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();

        actionsList.addAll(Arrays.asList(super.getActions(true)));

        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        //objects of type FileNode will co-occur in the treetable with objects
        //  of this type and they will need to provide the same key
        return CaseDBCommonAttributeInstanceNode.class.getName();
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);

        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        final CorrelationAttributeInstance centralRepoFile = this.getCorrelationAttributeInstance();

        final String fullPath = centralRepoFile.getFilePath();
        final File file = new File(fullPath);

        final String caseName = centralRepoFile.getCorrelationCase().getDisplayName();

        String name = file.getName();
        if (name == null) {
            name = "";
        }
        String parent = file.getParent();
        if (parent == null) {
            parent = "";
        }
        final String value = centralRepoFile.getCorrelationValue();

        final String dataSourceName = centralRepoFile.getCorrelationDataSource().getName();

        final String NO_DESCR = Bundle.CommonFilesSearchResultsViewerTable_noDescText();

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"), NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"), NO_DESCR, name));
        //add different columns for complete information depending on how nodes are structured in results
        if (nodeType == AbstractCommonAttributeInstance.NODE_TYPE.COUNT_NODE) {
             sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), NO_DESCR, parent));
            sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), NO_DESCR, dataSourceName));
            sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_caseColLbl(), Bundle.CommonFilesSearchResultsViewerTable_caseColLbl(), NO_DESCR, caseName));
        } else if (nodeType == AbstractCommonAttributeInstance.NODE_TYPE.CASE_NODE) {
             sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_localPath(), Bundle.CommonFilesSearchResultsViewerTable_localPath(), NO_DESCR, parent));
            sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_valueColLbl(), Bundle.CommonFilesSearchResultsViewerTable_valueColLbl(), NO_DESCR, value));
        }
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType"), NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType"), NO_DESCR, ""));

        return sheet;
    }
}
