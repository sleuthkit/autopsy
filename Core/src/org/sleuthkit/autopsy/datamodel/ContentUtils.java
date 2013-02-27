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
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskException;

/**
 * Static class of utility methods for Content objects
 */
public final class ContentUtils {

    private final static Logger logger = Logger.getLogger(ContentUtils.class.getName());
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat dateFormatterISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // don't instantiate
    private ContentUtils() {
        throw new AssertionError();
    }

    /**
     * Convert epoch seconds to a string value in the given time zone
     *
     * @param epochSeconds
     * @param tzone
     * @return
     */
    public static String getStringTime(long epochSeconds, TimeZone tzone) {
        String time = "0000-00-00 00:00:00";
        if (epochSeconds != 0) {
            dateFormatter.setTimeZone(tzone);
            time = dateFormatter.format(new java.util.Date(epochSeconds * 1000));
        }
        return time;
    }

    public static String getStringTimeISO8601(long epochSeconds, TimeZone tzone) {
        String time = "0000-00-00T00:00:00Z";
        if (epochSeconds != 0) {
            dateFormatterISO8601.setTimeZone(tzone);
            time = dateFormatterISO8601.format(new java.util.Date(epochSeconds * 1000));
        }

        return time;
    }

    /**
     * Convert epoch seconds to a string value (convenience method)
     *
     * @param epochSeconds
     * @param c
     * @return
     */
    public static String getStringTime(long epochSeconds, Content c) {
        return getStringTime(epochSeconds, getTimeZone(c));
    }

    /**
     * Convert epoch seconds to a string value (convenience method) in ISO8601
     * format such as 2008-07-04T13:45:04Z
     *
     * @param epochSeconds
     * @param c
     * @return
     */
    public static String getStringTimeISO8601(long epochSeconds, Content c) {
        return getStringTimeISO8601(epochSeconds, getTimeZone(c));
    }

    public static TimeZone getTimeZone(Content c) {
        try {
            return TimeZone.getTimeZone(c.getImage().getTimeZone());
        } catch (TskException ex) {
            return TimeZone.getDefault();
        }
    }
    private static final SystemNameVisitor systemName = new SystemNameVisitor();

    static String getSystemName(Content content) {
        return content.accept(systemName);
    }

    private static class SystemNameVisitor extends ContentVisitor.Default<String> {

        SystemNameVisitor() {
        }

        @Override
        protected String defaultVisit(Content cntnt) {
            return cntnt.getName() + ":" + Long.toString(cntnt.getId());
        }
    }
    private static final int TO_FILE_BUFFER_SIZE = 8192;

    /**
     * Reads all the data from any content object and writes it to a file.
     *
     * @param content Any content object.
     * @param outputFile Will be created if it doesn't exist, and overwritten if
     * it does
     * @param progress progress bar handle to update, if available. null
     * otherwise
     * @param worker the swing worker background thread the process runs within,
     * or null, if in the main thread, used to handle task cancellation
     * @param source true if source file
     * @throws IOException if file could not be written
     */
    public static void writeToFile(Content content, java.io.File outputFile,
            ProgressHandle progress, SwingWorker worker, boolean source) throws IOException {

        InputStream in = new ReadContentInputStream(content);

        boolean append = false;
        FileOutputStream out = new FileOutputStream(outputFile, append);

        // Get the unit size for a progress bar
        int unit = (int) (content.getSize() / 100);
        long totalRead = 0;

        try {
            byte[] buffer = new byte[TO_FILE_BUFFER_SIZE];
            int len = in.read(buffer);
            while (len != -1) {
                // If there is a worker, check for a cancelation
                if (worker != null && worker.isCancelled()) {
                    break;
                }
                out.write(buffer, 0, len);
                len = in.read(buffer);
                totalRead += len;
                // If there is a progress bar and this is the source file,
                // report any progress
                if (progress != null && source && totalRead >= TO_FILE_BUFFER_SIZE) {
                    int totalProgress = (int) (totalRead / unit);
                    progress.progress(content.getName(), totalProgress);
                    // If it's not the source, just update the file being processed
                } else if (progress != null && !source) {
                    progress.progress(content.getName());
                }
            }
        } finally {
            out.close();
        }
    }

    public static void writeToFile(Content content, java.io.File outputFile) throws IOException {
        writeToFile(content, outputFile, null, null, false);
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
        ProgressHandle progress;
        SwingWorker worker;
        boolean source = false;

        /**
         * Make new extractor for a specific destination
         *
         * @param dest The file/folder visited will be extracted as this file
         * @param progress progress bar handle to update, if available. null
         * otherwise
         * @param worker the swing worker background thread the process runs
         * within, or null, if in the main thread, used to handle task
         * cancellation
         * @param source true if source file
         */
        public ExtractFscContentVisitor(java.io.File dest,
                ProgressHandle progress, SwingWorker worker, boolean source) {
            this.dest = dest;
            this.progress = progress;
            this.worker = worker;
            this.source = source;
        }

        public ExtractFscContentVisitor(java.io.File dest) {
            this.dest = dest;
        }

        /**
         * Convenience method to make a new instance for given destination and
         * extract given content
         */
        public static void extract(Content cntnt, java.io.File dest, ProgressHandle progress, SwingWorker worker) {
            cntnt.accept(new ExtractFscContentVisitor(dest, progress, worker, true));
        }

        @Override
        public Void visit(File f) {
            try {
                ContentUtils.writeToFile(f, dest, progress, worker, source);
            } catch (IOException ex) {
                logger.log(Level.SEVERE,
                        "Trouble extracting file to " + dest.getAbsolutePath(),
                        ex);
            }
            return null;
        }

        @Override
        public Void visit(LayoutFile f) {
            try {
                ContentUtils.writeToFile(f, dest, progress, worker, source);
            } catch (IOException ex) {
                logger.log(Level.SEVERE,
                        "Trouble extracting unallocated content file to " + dest.getAbsolutePath(),
                        ex);
            }
            return null;
        }

        @Override
        public Void visit(DerivedFile df) {
            try {
                ContentUtils.writeToFile(df, dest, progress, worker, source);
            } catch (IOException ex) {
                logger.log(Level.SEVERE,
                        "Error extracting derived file to " + dest.getAbsolutePath(),
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
                int numProcessed = 0;
                // recurse on children
                for (Content child : dir.getChildren()) {
                    java.io.File childFile = child.accept(destFileCV);
                    ExtractFscContentVisitor childVisitor =
                            new ExtractFscContentVisitor(childFile, progress, worker, false);
                    // If this is the source directory of an extract it
                    // will have a progress and worker, and will keep track
                    // of the progress bar's progress
                    if (worker != null && worker.isCancelled()) {
                        break;
                    }
                    if (progress != null && source) {
                        progress.progress(child.getName(), numProcessed);
                    }
                    child.accept(childVisitor);
                    numProcessed++;
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
        private class DestFileContentVisitor extends ContentVisitor.Default<java.io.File> {

            /**
             * Get destination file by adding File/Directory name to the path of
             * parent
             */
            private java.io.File getFsContentDest(AbstractFile fsc) {
                String path = dest.getAbsolutePath() + java.io.File.separator
                        + fsc.getName();
                return new java.io.File(path);
            }

            @Override
            public java.io.File visit(File f) {
                return getFsContentDest(f);
            }

            @Override
            public java.io.File visit(LayoutFile lf) {
                return getFsContentDest(lf);
            }
            
             @Override
            public java.io.File visit(DerivedFile df) {
                return getFsContentDest(df);
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
