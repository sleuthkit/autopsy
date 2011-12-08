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

import java.util.LinkedList;
import java.util.List;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Static class of 
 */
public final class ContentUtils {
    
    // don't instantiate
    private ContentUtils() {
        throw new AssertionError();
    }
    
    private static final ShortNameVisitor shortName = new ShortNameVisitor();
    
    private static final GetPathVisitor getDisplayPath = new GetPathVisitor(shortName);
    
    /**
     * Returns full path to this node.
     *
     * @return the path of this node
     */
    public static String[] getDisplayPath(Content content) {
        return content.accept(getDisplayPath).toArray(new String[]{});
    }
    
    private static final SystemNameVisitor systemName = new SystemNameVisitor();
    
    private static final GetPathVisitor getSystemPath = new GetPathVisitor(systemName);
    
    /**
     * Returns full path to this node.
     * 
     * @return the path of this node
     */
    public static String[] getSystemPath(Content content) {
        return content.accept(getSystemPath).toArray(new String[]{});
    }
    
    static String getSystemName(Content content) {
        return content.accept(systemName);
    }
    
    private static class SystemNameVisitor extends ContentVisitor.Default<String> {
        SystemNameVisitor() {}

        @Override
        protected String defaultVisit(Content cntnt) {
            return cntnt.accept(shortName) + ":" + Long.toString(cntnt.getId());
        }
    }
   
    private static class ShortNameVisitor extends ContentVisitor.Default<String> {
        ShortNameVisitor() {}

        @Override
        protected String defaultVisit(Content cntnt) {
            throw new UnsupportedOperationException("Can't get short name for given content type:" + cntnt.getClass());
        }

        @Override
        public String visit(Directory dir) {
            return DirectoryNode.nameForDirectory(dir);
        }

        @Override
        public String visit(File f) {
            return FileNode.nameForFile(f);
        }

        @Override
        public String visit(Volume v) {
            return VolumeNode.nameForVolume(v);
        }

        @Override
        public String visit(Image i) {
            return ImageNode.nameForImage(i);
        }
    }
    
    private static class GetPathVisitor implements ContentVisitor<List<String>> { 
        ContentVisitor<String> toString;

        GetPathVisitor(ContentVisitor<String> toString) {
            this.toString = toString;
        }

        @Override
        public List<String> visit(Directory dir) {
            List<String> path;

            if (dir.isRoot()) {
                path = dir.getFileSystem().accept(this);
            } else {
                try {
                    path = dir.getParentDirectory().accept(this);
                    path.add(toString.visit(dir));
                } catch (TskException ex) {
                    throw new RuntimeException("Couldn't get directory path.", ex);
                }
            }
            
            return path;
        }

        @Override
        public List<String> visit(File file) {
            try {
                List<String> path = file.getParentDirectory().accept(this);
                path.add(toString.visit(file));
                return path;
            } catch (TskException ex) {
                throw new RuntimeException("Couldn't get file path.", ex);
            }
        }

        @Override
        public List<String> visit(FileSystem fs) {
            return fs.getParent().accept(this);
        }

        @Override
        public List<String> visit(Image image) {
           List<String> path = new LinkedList<String>();
           path.add(toString.visit(image));
           return path;
        }

        @Override
        public List<String> visit(Volume volume) {
            List<String> path = volume.getParent().accept(this);
            path.add(toString.visit(volume));
            return path;
        }

        @Override
        public List<String> visit(VolumeSystem vs) {
            return vs.getParent().accept(this);
        }
    }
    
}
