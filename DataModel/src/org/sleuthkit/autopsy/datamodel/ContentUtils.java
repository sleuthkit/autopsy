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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Static class of utility methods for Content objects
 */
public final class ContentUtils {
    
    private final static Logger logger = Logger.getLogger(ContentUtils.class.getName());
    
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
    
    
    private static final int TO_FILE_BUFFER_SIZE = 8192;
    
    /**
     * Reads all the data from any content object and writes it to a file.
     * @param content Any content object.
     * @param outputFile Will be created if it doesn't exist, and overwritten if
     * it does
     * @throws IOException 
     */
    public static void writeToFile(Content content, java.io.File outputFile) throws IOException {

        InputStream in = new ReadContentInputStream(content);
        
        boolean append = false;
        FileOutputStream out = new FileOutputStream(outputFile, append);

        try {
            byte[] buffer = new byte[TO_FILE_BUFFER_SIZE];
            int len = in.read(buffer);
            while (len != -1) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
        } finally {
            out.close();
        }
    }
    
    /**
     * Helper to ignore the '.' and '..' directories
     */
    public static boolean isDotDirectory(Directory dir) {
        String name = dir.getName();
        return name.equals(".") || name.equals("..");
    }
    
    
    /**
     * Extracts file/folder as given destination file, recursing into folders.
     * Assumes there will be no collisions with existing directories/files, and
     * that the directory to contain the destination file already exists.
     */
    public static class ExtractFscContentVisitor extends ContentVisitor.Default<Void> {

        java.io.File dest;

        /**
         * Make new extractor for a specific destination
         * @param dest The file/folder visited will be extracted as this file
         */
        public ExtractFscContentVisitor(java.io.File dest) {
            this.dest = dest;
        }

        /**
         * Convenience method to make a new instance for given destination
         * and extract given content 
         */
        public static void extract(Content cntnt, java.io.File dest) {
            cntnt.accept(new ExtractFscContentVisitor(dest));
        }

        public Void visit(File f) {
            try {
                ContentUtils.writeToFile(f, dest);
            } catch (IOException ex) {
                logger.log(Level.SEVERE,
                        "Trouble extracting file to " + dest.getAbsolutePath(),
                        ex);
            }
            return null;
        }

        @Override
        public Void visit(Directory dir) {
            
            // don't extract . and .. directories
            if (isDotDirectory(dir)) {
                return null;
            }
            
            dest.mkdir();
            
            // member visitor to generate destination files for children
            DestFileContentVisitor destFileCV = new DestFileContentVisitor();

            try {
                // recurse on children
                for (Content child : dir.getChildren()) {
                    java.io.File childFile = child.accept(destFileCV);
                    ExtractFscContentVisitor childVisitor = 
                        new ExtractFscContentVisitor(childFile);
                    child.accept(childVisitor);
                }
            } catch (TskException ex) {
                logger.log(Level.SEVERE,
                        "Trouble fetching children to extract.", ex);
            }

            return null;
        }

        @Override
        protected Void defaultVisit(Content cntnt) {
            throw new UnsupportedOperationException("Can't extract a "
                    + cntnt.getClass().getSimpleName());
        }

        /**
         * Helper visitor to get the destination file for a child Content object
         */
        private class DestFileContentVisitor extends
                ContentVisitor.Default<java.io.File> {

            /**
             * Get destination file by adding File/Directory name to the path
             * of parent
             */
            private java.io.File getFsContentDest(FsContent fsc) {
                String path = dest.getAbsolutePath() + java.io.File.separator
                        + fsc.getName();
                return new java.io.File(path);
            }

            @Override
            public java.io.File visit(File f) {
                return getFsContentDest(f);
            }

            @Override
            public java.io.File visit(Directory dir) {
                return getFsContentDest(dir);
            }

            @Override
            protected java.io.File defaultVisit(Content cntnt) {
                throw new UnsupportedOperationException("Can't get destination file for a "
                        + cntnt.getClass().getSimpleName());
            }
        }
    }
}
