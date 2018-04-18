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
package org.sleuthkit.autopsy.datamodel;

import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import java.util.Arrays;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Node for the results section of the tree.
 */
public class ResultsNode extends DisplayableItemNode {

    @NbBundle.Messages("ResultsNode.name.text=Results")
    public static final String NAME = Bundle.ResultsNode_name_text();

    
    
    public ResultsNode(SleuthkitCase sleuthkitCase) {
        super(new RootContentChildren(Arrays.asList(
                new ExtractedContent(sleuthkitCase),
                new KeywordHits(sleuthkitCase),
                new HashsetHits(sleuthkitCase),
                new EmailExtracted(sleuthkitCase),
                new InterestingHits(sleuthkitCase),
                new Accounts(sleuthkitCase)
        )), Lookups.singleton(NAME));
        setName(NAME);
        setDisplayName(NAME);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/results.png"); //NON-NLS
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    @NbBundle.Messages({
        "ResultsNode.createSheet.name.name=Name",
        "ResultsNode.createSheet.name.displayName=Name",
        "ResultsNode.createSheet.name.desc=no description"})
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(Bundle.ResultsNode_createSheet_name_name(),
                Bundle.ResultsNode_createSheet_name_displayName(),
                Bundle.ResultsNode_createSheet_name_desc(),
                NAME
        ));
        return sheet;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
