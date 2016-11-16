/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * Static class of utility methods for Content objects
 */
public final class ContentUtils {

    private final static Logger logger = Logger.getLogger(ContentUtils.class.getName());
    private static boolean displayTimesInLocalTime = UserPreferences.displayTimesInLocalTime();
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private static final SimpleDateFormat dateFormatterISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static {
        UserPreferences.addChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                if (evt.getKey().equals(UserPreferences.DISPLAY_TIMES_IN_LOCAL_TIME)) {
                    displayTimesInLocalTime = UserPreferences.displayTimesInLocalTime();
                }
            }
        });
    }

    // don't instantiate
    private ContentUtils() {
        throw new AssertionError();
    }

    /**
     * Convert epoch seconds to a string value in the given time zone
     *
     * @param epochSeconds
     * @param tzone
     *
     * @return
     */
    public static String getStringTime(long epochSeconds, TimeZone tzone) {
        String time = "0000-00-00 00:00:00";
        if (epochSeconds != 0) {
            synchronized (dateFormatter) {
                dateFormatter.setTimeZone(tzone);
                time = dateFormatter.format(new java.util.Date(epochSeconds * 1000));
            }
        }
        return time;
    }

    public static String getStringTimeISO8601(long epochSeconds, TimeZone tzone) {
        String time = "0000-00-00T00:00:00Z"; //NON-NLS
        if (epochSeconds != 0) {
            synchronized (dateFormatterISO8601) {
                dateFormatterISO8601.setTimeZone(tzone);
                time = dateFormatterISO8601.format(new java.util.Date(epochSeconds * 1000));
            }
        }

        return time;
    }

    /**
     * Convert epoch seconds to a string value (convenience method)
     *
     * @param epochSeconds
     * @param c
     *
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
     *
     * @return
     */
    public static String getStringTimeISO8601(long epochSeconds, Content c) {
        return getStringTimeISO8601(epochSeconds, getTimeZone(c));
    }

    public static TimeZone getTimeZone(Content c) {

        try {
            if (!shouldDisplayTimesInLocalTime()) {
                return TimeZone.getTimeZone("GMT");
            } else {
                final Content dataSource = c.getDataSource();
                if ((dataSource != null) && (dataSource instanceof Image)) {
                    Image image = (Image) dataSource;
                    return TimeZone.getTimeZone(image.getTimeZone());
                } else {
                    //case such as top level VirtualDirectory
                    return TimeZone.getDefault();
                }
            }
        } catch (TskException ex) {
            return TimeZone.getDefault();
        }
    }
    private static final SystemNameVisitor systemName = new SystemNameVisitor();

    public static String getSystemName(Content content) {
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
     * Reads all the data from any content object and writes (extracts) it to a
     * file.
     *
     * @param content    Any content object.
     * @param outputFile Will be created if it doesn't exist, and overwritten if
     *                   it does
     * @param progress   progress bar handle to update, if available. null
     *                   otherwise
     * @param worker     the swing worker background thread the process runs
     *                   within, or null, if in the main thread, used to handle
     *                   task cancellation
     * @param source     true if source file
     *
     * @return number of bytes extracted
     *
     * @throws IOException if file could not be written
     */
    public static <T> long writeToFile(Content content, java.io.File outputFile,
            ProgressHandle progress, Future<T> worker, boolean source) throws IOException {
        InputStream in = new ReadContentInputStream(content);

        // Get the unit size for a progress bar
        int unit = (int) (content.getSize() / 100);
        long totalRead = 0;

        try (FileOutputStream out = new FileOutputStream(outputFile, false)) {
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
            in.close();
        }
        return totalRead;
    }

    public static void writeToFile(Content content, java.io.File outputFile) throws IOException {
        writeToFile(content, outputFile, null, null, false);
    }
    
    /**
     * Reads all the data from any content object and writes (extracts) it to a
     * file, using a cancellation check instead of a Future object method.
     * 
     * @param content     Any content object.
     * @param outputFile  Will be created if it doesn't exist, and overwritten
     *                    if it does
     * @param cancelCheck A function used to check if the file write process
     *                    should be terminated.
     * @return number of bytes extracted
     * @throws IOException if file could not be written
     */
    public static long writeToFile(Content content, java.io.File outputFile,
            Supplier<Boolean> cancelCheck) throws IOException {
        InputStream in = new ReadContentInputStream(content);
        long totalRead = 0;
        
        try (FileOutputStream out = new FileOutputStream(outputFile, false)) {
            byte[] buffer = new byte[TO_FILE_BUFFER_SIZE];
            int len = in.read(buffer);
            while (len != -1) {
                out.write(buffer, 0, len);
                totalRead += len;
                if (cancelCheck.get()) {
                    break;
                }
                len = in.read(buffer);
            }
        } finally {
            in.close();
        }
        return totalRead;
    }

    /**
     * Helper to ignore the '.' and '..' directories
     */
    public static boolean isDotDirectory(AbstractFile dir) {
        String name = dir.getName();
        return name.equals(".") || name.equals("..");
    }

    /**
     * Extracts file/folder as given destination file, recursing into folders.
     * Assumes there will be no collisions with existing directories/files, and
     * that the directory to contain the destination file already exists.
     */
    public static class ExtractFscContentVisitor<T, V> extends ContentVisitor.Default<Void> {

        java.io.File dest;
        ProgressHandle progress;
        SwingWorker<T, V> worker;
        boolean source = false;

        /**
         * Make new extractor for a specific destination
         *
         * @param dest     The file/folder visited will be extracted as this
         *                 file
         * @param progress progress bar handle to update, if available. null
         *                 otherwise
         * @param worker   the swing worker background thread the process runs
         *                 within, or null, if in the main thread, used to
         *                 handle task cancellation
         * @param source   true if source file
         */
        public ExtractFscContentVisitor(java.io.File dest,
                ProgressHandle progress, SwingWorker<T, V> worker, boolean source) {
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
        public static <T, V> void extract(Content cntnt, java.io.File dest, ProgressHandle progress, SwingWorker<T, V> worker) {
            cntnt.accept(new ExtractFscContentVisitor<>(dest, progress, worker, true));
        }

        @Override
        public Void visit(File f) {
            try {
                ContentUtils.writeToFile(f, dest, progress, worker, source);
            } catch (IOException ex) {
                logger.log(Level.SEVERE,
                        "Trouble extracting file to " + dest.getAbsolutePath(), //NON-NLS
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
                        "Trouble extracting unallocated content file to " + dest.getAbsolutePath(), //NON-NLS
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
                        "Error extracting derived file to " + dest.getAbsolutePath(), //NON-NLS
                        ex);
            }
            return null;
        }

        @Override
        public Void visit(LocalFile lf) {
            try {
                ContentUtils.writeToFile(lf, dest, progress, worker, source);
            } catch (IOException ex) {
                logger.log(Level.SEVERE,
                        "Error extracting local file to " + dest.getAbsolutePath(), //NON-NLS
                        ex);
            }
            return null;
        }
        
        @Override
        public Void visit(SlackFile f) {
            try {
                ContentUtils.writeToFile(f, dest, progress, worker, source);
            } catch (IOException ex) {
                logger.log(Level.SEVERE,
                        "Trouble extracting slack file to " + dest.getAbsolutePath(), //NON-NLS
                        ex);
            }
            return null;
        }

        @Override
        public Void visit(Directory dir) {
            return visitDir(dir);
        }

        @Override
        public Void visit(VirtualDirectory dir) {
            return visitDir(dir);
        }

        private java.io.File getFsContentDest(Content fsc) {
            String path = dest.getAbsolutePath() + java.io.File.separator
                    + fsc.getName();
            return new java.io.File(path);
        }

        public Void visitDir(AbstractFile dir) {

            // don't extract . and .. directories
            if (isDotDirectory(dir)) {
                return null;
            }

            dest.mkdir();

            try {
                int numProcessed = 0;
                // recurse on children
                for (Content child : dir.getChildren()) {
                    java.io.File childFile = getFsContentDest(child);
                    ExtractFscContentVisitor<T, V> childVisitor
                            = new ExtractFscContentVisitor<>(childFile, progress, worker, false);
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
                        "Trouble fetching children to extract.", ex); //NON-NLS
            }

            return null;
        }

        @Override
        protected Void defaultVisit(Content content) {
            throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(),
                    "ContentUtils.exception.msg",
                    content.getClass().getSimpleName()));
        }
    }

    /**
     * Indicates whether or not times should be displayed using local time.
     *
     * @return True or false.
     */
    public static boolean shouldDisplayTimesInLocalTime() {
        return displayTimesInLocalTime;
    }
    
}