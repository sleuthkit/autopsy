/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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

import java.util.Arrays;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Node for the results view
 */
public class ResultsNode extends DisplayableItemNode {

    public static final String NAME = "Results";

    public ResultsNode(SleuthkitCase sleuthkitCase) {
        super(new RootContentChildren(Arrays.asList(new ExtractedContent(sleuthkitCase),
                new KeywordHits(sleuthkitCase),
                new HashsetHits(sleuthkitCase),
                new EmailExtracted(sleuthkitCase),
                new Tags(sleuthkitCase) //TODO move to the top of the tree 
                )), Lookups.singleton(NAME));
        setName(NAME);
        setDisplayName(NAME);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/results.png");
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty("Name",
                "Name",
                "no description",
                NAME));
        return s;
    }

    @Override
    public TYPE getDisplayableItemNodeType() {
        return TYPE.META;
    }
}
