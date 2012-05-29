package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;


/**
 * Returns all the children of a Content object, descending the hierarchy
 * past subclasses that aren't part of the exposed hierarchy (VolumeSystem,
 * FileSystem, and root Directories)
 */
public class AbstractFileHierarchyVisitor extends ContentVisitor.Default<List<AbstractFile>> {
    private static final Logger logger = Logger.getLogger(AbstractFileHierarchyVisitor.class.getName());
    private static final AbstractFileHierarchyVisitor INSTANCE = new AbstractFileHierarchyVisitor();
    
    private AbstractFileHierarchyVisitor() {}

    
    /**
     * Get the child AbstractFile objects according the the exposed hierarchy.
     * @param parent
     * @return 
     */
    public static List<AbstractFile> getAbstractChildren(Content parent) {
        List<Content> children = new ArrayList<Content>();
        List<AbstractFile> keys = new ArrayList<AbstractFile>();


        try {
            children = parent.getChildren();
            
            for(Content c : children) {
                keys.addAll(c.accept(INSTANCE));
            }
            
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error getting Content children.", ex);
        }


        return keys;
    }

    @Override
    protected List<AbstractFile> defaultVisit(Content c) {
        return Collections.<AbstractFile>emptyList();
    }

    @Override
    public List<AbstractFile> visit(FileSystem v) {
        List<AbstractFile> ret = new ArrayList<AbstractFile>();
        try {
            for (TskData.TSK_DB_FILES_TYPE_ENUM type : TskData.TSK_DB_FILES_TYPE_ENUM.values()) {
                if(type != TskData.TSK_DB_FILES_TYPE_ENUM.FS)
                    ret.addAll(v.getAbstractFileChildren(type));
            }
        } catch (Exception ex) {
            Logger.getLogger(AbstractFileHierarchyVisitor.class.getName()).log(Level.INFO, "error", ex);
        }
        return ret;
    }
}