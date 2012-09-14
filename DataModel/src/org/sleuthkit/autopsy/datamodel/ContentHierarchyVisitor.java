package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.LayoutDirectory;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.VolumeSystem;


/**
 * Returns all the children of a Content object, descending the hierarchy
 * past subclasses that aren't part of the exposed hierarchy (VolumeSystem,
 * FileSystem, and root Directories)
 */
public class ContentHierarchyVisitor extends ContentVisitor.Default<List<? extends Content>> {
    private static final Logger logger = Logger.getLogger(ContentHierarchyVisitor.class.getName());
    private static final ContentHierarchyVisitor INSTANCE = new ContentHierarchyVisitor();
    
    private ContentHierarchyVisitor() {}

    /**
     * Get the child Content objects according the the exposed hierarchy.
     * @param parent
     * @return 
     */
    public static List<Content> getChildren(Content parent) {
        List<Content> keys = new ArrayList<Content>();

        List<Content> children;

        try {
            children = parent.getChildren();
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error getting Content children.", ex);
            children = Collections.emptyList();
        }

        for (Content c : children) {
            keys.addAll(c.accept(INSTANCE));
        }

        return keys;
    }

    @Override
    protected List<Content> defaultVisit(Content c) {
        return Collections.singletonList(c);
    }

    @Override
    public List<Content> visit(VolumeSystem vs) {
        return getChildren(vs);
    }

    @Override
    public List<Content> visit(FileSystem fs) {
        return getChildren(fs);
    }

    @Override
    public List<? extends Content> visit(Directory dir) {
        if (dir.isRoot()) {
            return getChildren(dir);
        } else {
            return Collections.singletonList(dir);
        }
    }
    
    @Override
    public List<? extends Content> visit(LayoutDirectory ldir) {
        //return getChildren(ldir);
        return Collections.singletonList(ldir);
    }
}