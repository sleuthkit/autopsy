
package org.sleuthkit.autopsy.keywordsearch;

import java.sql.SQLException;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.sleuthkit.autopsy.datamodel.ContentNode;
import org.sleuthkit.autopsy.datamodel.ContentNodeVisitor;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

class KeywordSearchNode extends AbstractNode  implements ContentNode {
    private String searchText;
    
    KeywordSearchNode(List<FsContent> keys, String searchText) {
        super(new RootContentChildren(keys));
        this.searchText = searchText;
    }

    @Override
    public long getID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Object[][] getRowValues(int rows) throws SQLException {
        int totalNodes = getChildren().getNodesCount();

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
            PropertySet[] props = getChildren().getNodeAt(i).getPropertySets();
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
    public byte[] read(long offset, long len) throws TskException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Content getContent() {
        return null;
    }

    @Override
    public String[] getDisplayPath() {
        return new String[] {this.searchText};
    }

    @Override
    public String[] getSystemPath() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
