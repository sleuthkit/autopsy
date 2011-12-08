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

package org.sleuthkit.autopsy.filesearch;

import java.sql.SQLException;
import java.util.ArrayList;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node.Property;
import org.sleuthkit.autopsy.datamodel.ContentNode;
import org.sleuthkit.autopsy.datamodel.ContentNodeVisitor;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author jantonius
 */
class SearchNode extends AbstractNode implements ContentNode {

    private SearchChildren children;

    SearchNode(ArrayList<FsContent> keys) {
        super(new SearchChildren(true, keys));
        this.children = (SearchChildren)this.getChildren();
    }

    @Override
    public String getName() {
        return "Search Result";
    }

    @Override
    public Object[][] getRowValues(int rows) throws SQLException {
        int totalNodes = children.getNodesCount();

        Object[][] objs;
        int maxRows = 0;
        if(totalNodes > rows){
            objs = new Object[rows][];
            maxRows = rows;
        }
        else{
            objs = new Object[totalNodes][];
            maxRows = totalNodes;
        }

        for(int i = 0; i < maxRows; i++){
            PropertySet[] props = children.getNodeAt(i).getPropertySets();
            Property[] property = props[0].getProperties();
            objs[i] = new Object[property.length];

            for(int j = 0; j < property.length; j++){
                try {
                    objs[i][j] = property[j].getValue();
                } catch (Exception ex) {
                    objs[i][j] = "n/a";
                }
            }
        }
        return objs;
    }

    @Override
    public Content getContent() {
        return null;
    }

    @Override
    public String[] getDisplayPath() {
        return new String[]{"KeyWord Search Result:"};
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        //TODO: figure out how to deal with visitors
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public String[] getSystemPath() {
        // Shouldn't be used
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
