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

import java.sql.SQLException;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;

public class ContentFilterNode extends FilterNode implements ContentNode {

    public ContentFilterNode(ContentNode original) {
        super((Node) original);
    }
    
    public ContentFilterNode(ContentNode original, Children children) {
        super((Node) original, children);
    }
        
    public ContentFilterNode(ContentNode original, Children children, Lookup lookup)  {
        super((Node) original, children, lookup);
    }

    @Override
    public Object[][] getRowValues(int rows) throws SQLException {
        return ((ContentNode) super.getOriginal()).getRowValues(rows);
    }

    @Override
    public String[] getDisplayPath() {
        return ((ContentNode) super.getOriginal()).getDisplayPath();
    }

    @Override
    public String[] getSystemPath() {
        return ((ContentNode) super.getOriginal()).getSystemPath();
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return ((ContentNode) super.getOriginal()).accept(v);
    }
}
