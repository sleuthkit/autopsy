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
package org.sleuthkit.autopsy.directorytree;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Extracts all the unallocated space as a single file
 */
public final class ExtractUnallocAction extends AbstractAction {

    private final List<UnallocStruct> LstUnallocs = new ArrayList<UnallocStruct>();
    private static final List<String> lockedVols = new ArrayList<String>();
    private static final List<Long> lockedImages = new ArrayList<Long>();
    private long currentImage = 0L;
    private static final Logger logger = Logger.getLogger(ExtractUnallocAction.class.getName());
    private boolean isImage = false;

    public ExtractUnallocAction(String title, Volume volu) {
        super(title);
        UnallocStruct us = new UnallocStruct(volu);
        LstUnallocs.add(us);
    }

    public ExtractUnallocAction(String title, Image img) {
        super(title);
        isImage = true;
        currentImage = img.getId();
        if (hasVolumeSystem(img)) {
            for (Volume v : getVolumes(img)) {
                UnallocStruct us = new UnallocStruct(v);
                LstUnallocs.add(us);
            }
        } else {
            UnallocStruct us = new UnallocStruct(img);
            LstUnallocs.add(us);
        }
    }

    /**
     * Writes the unallocated files to
     * $CaseDir/Export/ImgName-Unalloc-ImgObjectID-VolumeID.dat
     *
     * @param e
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (LstUnallocs != null && LstUnallocs.size() > 0) {
            if (lockedImages.contains(currentImage)) {
                MessageNotifyUtil.Message.info("Unallocated Space is already being extracted on this Image. Please select a different Image.");
                //JOptionPane.showMessageDialog(new Frame(), "Unallocated Space is already being extracted on this Image. Please select a different Image.");
                return;
            }
            List<UnallocStruct> copyList = new ArrayList<UnallocStruct>() {
                {
                    addAll(LstUnallocs);
                }
            };


            JFileChooser fc = new JFileChooser() {
                @Override
                public void approveSelection() {
                    File f = getSelectedFile();
                    if (!f.exists() && getDialogType() == SAVE_DIALOG || !f.canWrite()) {
                        JOptionPane.showMessageDialog(this, "Folder does not exist. Please choose a valid folder before continuing");
                        return;
                    }
                     super.approveSelection();
                }
            };
            
            fc.setCurrentDirectory(new File(Case.getCurrentCase().getCaseDirectory() + File.separator + "Export"));
            fc.setDialogTitle("Select directory to save to");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setAcceptAllFileFilterUsed(false);
            int returnValue = fc.showSaveDialog((Component) e.getSource());
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                String destination = fc.getSelectedFile().getPath();
                for (UnallocStruct u : LstUnallocs) {
                    u.setPath(destination);
                    if (u.llf != null && u.llf.size() > 0 && !lockedVols.contains(u.getFileName())) {
                        //Format for single Unalloc File is ImgName-Unalloc-ImgObjectID-VolumeID.dat                    
                        if (u.FileInstance.exists()) {
                            int res = JOptionPane.showConfirmDialog(new Frame(), "The Unalloc File for this volume, " + u.getFileName() + " already exists, do you want to replace it?");
                            if (res == JOptionPane.YES_OPTION) {
                                u.FileInstance.delete();
                            } else {
                                copyList.remove(u);
                            }
                        }
                        if (!isImage & !copyList.isEmpty()) {
                            ExtractUnallocWorker uw = new ExtractUnallocWorker(u);
                            uw.execute();
                        }
                    } else {
                        logger.log(Level.WARNING, "Tried to get unallocated content from volume ID but " + u.VolumeId + u.llf == null ? "its list of unallocated files was null" : "the volume is locked" );
                    }
                }
                if (isImage && !copyList.isEmpty()) {
                    ExtractUnallocWorker uw = new ExtractUnallocWorker(copyList);
                    uw.execute();
                }
            }
        }

    }

    /**
     * Gets all the unallocated files in a given Content.
     *
     * @param c Content to get Unallocated Files from
     * @return A list<LayoutFile> if it didn't crash List may be empty. Returns
     * null on failure.
     */
    private List<LayoutFile> getUnallocFiles(Content c) {
        UnallocVisitor uv = new UnallocVisitor();
        try {
            return c.getChildren().get(0).accept(uv); //Launching it on the root directory
        } catch (TskCoreException tce) {
            logger.log(Level.WARNING, "Couldn't get a list of Unallocated Files, failed at sending out the visitor ", tce);
        }
        return null;
    }

    /**
     * Private class for dispatching the file IO in a background thread.
     */
    private class ExtractUnallocWorker extends SwingWorker<Integer, Integer> {

        private ProgressHandle progress;
        private boolean canceled = false;
        private List<UnallocStruct> lus = new ArrayList<UnallocStruct>();
        private File currentlyProcessing;
        private int totalSizeinMegs;
        long totalBytes = 0;

        ExtractUnallocWorker(UnallocStruct us) {            
            //Getting the total megs this worker is going to be doing
            if (!lockedVols.contains(us.getFileName())) {
                this.lus.add(us);
                totalBytes = us.getSizeInBytes();
                totalSizeinMegs = toMb(totalBytes);
                lockedVols.add(us.getFileName());
            }
        }

        ExtractUnallocWorker(List<UnallocStruct> lst) {
            //Getting the total megs this worker is going to be doing            
            for (UnallocStruct lu : lst) {
                if (!lockedVols.contains(lu.getFileName())) {
                    totalBytes += lu.getSizeInBytes();
                    lockedVols.add(lu.getFileName());
                    this.lus.add(lu);
                }
            }
            totalSizeinMegs = toMb(totalBytes);
            lockedImages.add(currentImage);
        }

        private int toMb(long bytes) {
            if (bytes > 1024 && (bytes / 1024.0) <= Double.MAX_VALUE) {
                double Mb = ((bytes / 1024.0) / 1024.0);//Bytes -> Megabytes
                if (Mb <= Integer.MAX_VALUE) {
                    return (int) Math.ceil(Mb);
                }
            }
            return 0;
        }

        @Override
        protected Integer doInBackground() {
            try {
                progress = ProgressHandleFactory.createHandle("Extracting Unallocated Space", new Cancellable() {
                    @Override
                    public boolean cancel() {
                        logger.log(Level.INFO, "Canceling extraction of unallocated space");
                        canceled = true;
                        if (progress != null) {
                            progress.setDisplayName("Extracting Unallocated Space" + " (Cancelling...)");
                        }
                        return true;
                    }
                });
                int MAX_BYTES = 8192;
                byte[] buf = new byte[MAX_BYTES];    //read 8kb at a time                         


                //Begin the actual File IO
                progress.start(totalSizeinMegs);
                int kbs = 0; //Each completion of the while loop adds one to kbs. 16kb * 64 = 1mb. 
                int mbs = 0; //Increments every 128th tick of  kbs
                for (UnallocStruct u : this.lus) {
                    currentlyProcessing = u.getFile();
                    logger.log(Level.INFO, "Writing Unalloc file to " + currentlyProcessing.getPath());
                    OutputStream dos = new FileOutputStream(currentlyProcessing);
                    long bytes = 0;
                    int i = 0;
                    while(i < u.getLayouts().size() && bytes != u.getSizeInBytes()){                        
                        LayoutFile f = u.getLayouts().get(i);
                        long offsetPerFile = 0L;
                        int bytesRead;
                        while(offsetPerFile != f.getSize() && !canceled){
                            if (++kbs % 128 == 0) {
                                mbs++;                                
                                progress.progress("processing " + mbs + " of " + totalSizeinMegs + " MBs", mbs-1);
                            }
                            bytesRead = f.read(buf, offsetPerFile, MAX_BYTES);  
                            offsetPerFile+= bytesRead;
                            dos.write(buf, 0, bytesRead);       
                        }
                        bytes+=f.getSize();
                        i++;
                    }
                    dos.flush();
                    dos.close();

                    if (canceled) {
                        u.getFile().delete();
                        logger.log(Level.INFO, "Canceled extraction of " + u.getFileName() + " and deleted file");
                    } else {
                        logger.log(Level.INFO, "Finished writing unalloc file " + u.getFile().getPath());
                    }
                }
                progress.finish();


            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Could not create Unalloc File; error writing file", ioe);
                return -1;
            } catch (TskCoreException tce) {
                logger.log(Level.WARNING, "Could not create Unalloc File; error getting image info", tce);
                return -1;
            }
            return 1;
        }

        @Override
        protected void done() {
            if (isImage) {
                lockedImages.remove(currentImage);
            }
            for (UnallocStruct u : lus) {
                lockedVols.remove(u.getFileName());
            }
            if (!canceled && !lus.isEmpty()) {
                MessageNotifyUtil.Notify.info("Completed extraction of unallocated space.", "Files were extracted to " + lus.get(0).getFile().getParent());
            }
        }        
    }

    /**
     * Determines if an image has a volume system or not.
     *
     * @param img The Image to analyze
     * @return True if there are Volume Systems present
     */
    private boolean hasVolumeSystem(Image img) {
        try {
            return (img.getChildren().get(0) instanceof VolumeSystem);
        } catch (TskCoreException tce) {
            logger.log(Level.WARNING, "Unable to determine if image has a volume system, extraction may be incomplete", tce);
            return false;
        }
    }

    /**
     * Gets the volumes on an given image.
     *
     * @param img The image to analyze
     * @return A list of volumes from the image. Returns an empty list if no
     * matches.
     */
    private List<Volume> getVolumes(Image img) {
        List<Volume> lstVol = new ArrayList<Volume>();
        try {
            for (Content v : img.getChildren().get(0).getChildren()) {
                if (v instanceof Volume) {
                    lstVol.add((Volume) v);
                }
            }
        } catch (TskCoreException tce) {
            logger.log(Level.WARNING, "Could not get volume information from image. Extraction may be incomplete", tce);
        }
        return lstVol;
    }

    /**
     * Private visitor class for going through a Content file and grabbing
     * unallocated files.
     */
    private static class UnallocVisitor extends ContentVisitor.Default<List<LayoutFile>> {

        /**
         * If the volume has no FileSystem, then it will call this method to
         * return the single instance of unallocated space.
         *
         * @param lf the LayoutFile the visitor encountered
         * @return A list<LayoutFile> of size 1, returns null if it fails
         */
        @Override
        public List<LayoutFile> visit(final org.sleuthkit.datamodel.LayoutFile lf) {
            return new ArrayList<LayoutFile>() {
                {
                    add(lf);
                }
            };
        }

        /**
         * If the visitor finds a FileSystem, it will filter the results for
         * directories and return on the Root Dir.
         *
         * @param fs the FileSystem the visitor encountered
         * @return A list<LayoutFile> containing the layout files from
         * subsequent Visits(), returns null if it fails
         */
        @Override
        public List<LayoutFile> visit(FileSystem fs) {
            try {
                for (Content c : fs.getChildren()) {
                    if (((AbstractFile) c).isRoot()) {
                        return c.accept(this);
                    }
                }
            } catch (TskCoreException tce) {
                logger.log(Level.WARNING, "Couldn't get a list of Unallocated Files, failed at visiting FileSystem " + fs.getId(), tce);
            }
            return null;
        }

        /**
         * LayoutDirectory has all the Layout(Unallocated) files
         *
         * @param vd VirtualDirectory the visitor encountered
         * @return A list<LayoutFile> containing all the LayoutFile in ld,
         * returns null if it fails
         */
        @Override
        public List<LayoutFile> visit(VirtualDirectory vd) {
            try {
                List<LayoutFile> lflst = new ArrayList<LayoutFile>();
                for (Content layout : vd.getChildren()) {
                    lflst.add((LayoutFile) layout);
                }
                return lflst;
            } catch (TskCoreException tce) {
                logger.log(Level.WARNING, "Could not get list of Layout Files, failed at visiting Layout Directory", tce);
            }
            return null;
        }

        /**
         * The only time this visitor should ever encounter a directory is when
         * parsing over Root
         *
         * @param dir the directory this visitor encountered
         * @return A list<LayoutFile> containing LayoutFiles encountered during
         * subsequent Visits(), returns null if it fails
         */
        @Override
        public List<LayoutFile> visit(Directory dir) {
            try {
                for (Content c : dir.getChildren()) {
                    if (c instanceof VirtualDirectory) {
                        return c.accept(this);
                    }
                }
            } catch (TskCoreException tce) {
                logger.log(Level.WARNING, "Couldn't get a list of Unallocated Files, failed at visiting Directory " + dir.getId(), tce);
            }
            return null;
        }

        @Override
        protected List<LayoutFile> defaultVisit(Content cntnt) {
            return null;
        }
    }

    /**
     * Comparator for sorting lists of LayoutFiles based on their Object ID
     * Ensures that the single Unalloc File is in proper order, and that the
     * bytes are continuous.
     */
    private class SortObjId implements Comparator<LayoutFile> {

        @Override
        public int compare(LayoutFile o1, LayoutFile o2) {
            if (o1.getId() == o2.getId()) {
                return 0;
            }
            if (o1.getId() > o2.getId()) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    /**
     * Private class for assisting in the running the action over an image with
     * multiple volumes.
     */
    private class UnallocStruct {

        private List<LayoutFile> llf;
        private long SizeInBytes;
        private long VolumeId;
        private long ImageId;
        private String ImageName;
        private String FileName;
        private File FileInstance;

        /**
         * Contingency constructor in event no VolumeSystem exists on an Image.
         *
         * @param img Image file to be analyzed
         */
        UnallocStruct(Image img) {
            this.llf = getUnallocFiles(img);
            Collections.sort(llf, new SortObjId());
            this.VolumeId = 0;
            this.ImageId = img.getId();
            this.ImageName = img.getName();
            this.FileName = this.ImageName + "-Unalloc-" + this.ImageId + "-" + 0 + ".dat";
            this.FileInstance = new File(Case.getCurrentCase().getCaseDirectory() + File.separator + "Export" + File.separator + this.FileName);
            this.SizeInBytes = calcSizeInBytes();
        }

        /**
         * Default constructor for extracting info from Volumes.
         *
         * @param volu Volume file to be analyzed
         */
        UnallocStruct(Volume volu) {
            try {
                this.ImageName = volu.getImage().getName();
                this.ImageId = volu.getImage().getId();
                this.VolumeId = volu.getId();
            } catch (TskCoreException tce) {
                logger.log(Level.WARNING, "Unable to properly create ExtractUnallocAction, extraction may be incomplete", tce);
                this.ImageName = "";
                this.ImageId = 0;
            }
            this.FileName = this.ImageName + "-Unalloc-" + this.ImageId + "-" + VolumeId + ".dat";
            this.FileInstance = new File(Case.getCurrentCase().getCaseDirectory() + File.separator + "Export" + File.separator + this.FileName);
            this.llf = getUnallocFiles(volu);
            Collections.sort(llf, new SortObjId());
            this.SizeInBytes = calcSizeInBytes();
        }

        //Getters
        int size() {
            return llf.size();
        }

       private long calcSizeInBytes() {
            long size = 0L;
            for (LayoutFile f : llf) {
                size += f.getSize();
            }
            return size;
        }
       
       long getSizeInBytes(){
           return this.SizeInBytes;
       }

        long getVolumeId() {
            return this.VolumeId;
        }

        long getImageId() {
            return this.ImageId;
        }

        String getImageName() {
            return this.ImageName;
        }

        List<LayoutFile> getLayouts() {
            return this.llf;
        }

        String getFileName() {
            return this.FileName;
        }

        File getFile() {
            return this.FileInstance;
        }

        void setPath(String path) {
            this.FileInstance = new File(path + File.separator + this.FileName);
        }
    }
}
