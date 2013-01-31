/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.LinkedHashMap;
import java.util.Map;
import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.LayoutFile;

/**
 * A Node for a DerivedFile content object.
 * 
 * TODO should be able to extend FileNode after FileNode extends AbstractFsContentNode<AbstractFile>
 */
public class DerivedFileNode  extends AbstractAbstractFileNode<DerivedFile> {

    public static enum DerivedFilePropertyType {

        NAME {
            @Override
            public String toString() {
                return "Name";
            }
        },
        SIZE {
            @Override
            public String toString() {
                return "Size";
            }
        },
  
    }

    public static String nameForLayoutFile(LayoutFile lf) {
        return lf.getName();
    }

    public DerivedFileNode(DerivedFile df) {
        super(df);

        this.setDisplayName(df.getName());
        this.setIconBaseWithExtension(FileNode.getIconForFileType(df));
    }

    @Override
    public TYPE getDisplayableItemNodeType() {
        return TYPE.CONTENT;
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        Map<String, Object> map = new LinkedHashMap<String, Object>();
        fillPropertyMap(map, content);

        ss.put(new NodeProperty("Name", "Name", "no description", getName()));

        final String NO_DESCR = "no description";
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ss.put(new NodeProperty(entry.getKey(), entry.getKey(), NO_DESCR, entry.getValue()));
        }
        // @@@ add more properties here...

        return s;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
    
    

    //TODO add more
    private static void fillPropertyMap(Map<String, Object> map, DerivedFile content) {
        map.put(DerivedFilePropertyType.NAME.toString(), content.getName());
        map.put(DerivedFilePropertyType.SIZE.toString(), content.getSize());
    }
}
