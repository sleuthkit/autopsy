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

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutDirectory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Extracts all the unallocated space as a single file
 */
public final class ExtractUnallocAction extends AbstractAction {

    private List<UnallocStruct> LstUnallocs = new ArrayList<UnallocStruct>();
    private static volatile List<String> lockedVols = new ArrayList<String>();
    private int numDone = 0;
    private volatile static boolean runningOnImage = false;
    private static final Logger logger = Logger.getLogger(ExtractUnallocAction.class.getName());
    private boolean isImage = false;
    
    public ExtractUnallocAction(String title, Volume volu){
        super(title);
        UnallocStruct us = new UnallocStruct(volu);
        LstUnallocs.add(us);
    }    
    public ExtractUnallocAction(String title, Image img) {
        super(title);
        isImage = true;
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
     * Writes the unallocated files to $CaseDir/Export/ImgName-Unalloc-ImgObjectID-VolumeID.dat
     * @param e 
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (LstUnallocs != null && LstUnallocs.size() > 0) {
            if (runningOnImage) {
                JOptionPane.showMessageDialog(new Frame(), "Unallocated Space is already running on this Image. Please select a different Image.");
                return;
            }
            for (UnallocStruct u : LstUnallocs) {
                String UnallocName = u.ImageName + "-Unalloc-" + u.ImageId + "-" + u.VolumeId + ".dat";
                if (u.llf != null && u.llf.size() > 0 && !lockedVols.contains(UnallocName)) {                    
                    //Format for single Unalloc File is ImgName-Unalloc-ImgObjectID-VolumeID.dat
                    File unalloc = new File(Case.getCurrentCase().getCaseDirectory() + File.separator + "Export" + File.separator + UnallocName);
                    if (unalloc.exists()) {
                        int res = JOptionPane.showConfirmDialog(new Frame(), "The Unalloc File for this volume, " + UnallocName + " already exists, do you want to replace it?");
                        if (res == JOptionPane.YES_OPTION) {
                            unalloc.delete();
                        } else {
                            return;
                        }
                    }
                    ExtractUnallocWorker uw = new ExtractUnallocWorker(unalloc, u);
                    uw.execute();
                } else {
                    logger.log(Level.WARNING, "Tried to get unallocated content from volume ID " + u.VolumeId + ", but its list of unallocated files was empty or null");
                }
            }
        }

    }

    /**
     * Gets all the unallocated files in a given Content. 
     * @param c Content o get Unallocated Files from 
     * @return A list<LayoutFile> if it didn't crash List may be empty. Returns null on failure.
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
        
        private File path;
        private ProgressHandle progress;
        private boolean canceled = false;
        private UnallocStruct us;
    

        ExtractUnallocWorker(File path, UnallocStruct us) {
            this.path = path;
            if(isImage){
                runningOnImage = true;
            }
            lockedVols.add(path.getName());
            this.us = us;
        }

        @Override
        protected Integer doInBackground() {
            try {
                progress = ProgressHandleFactory.createHandle("Extracting " + path.getName(), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        logger.log(Level.INFO, "Canceling extraction of Unalloc file " + path.getName());
                        canceled = true;
                        if (progress != null) {
                            progress.setDisplayName(path.getName() + " (Cancelling...)");
                        }
                        return true;
                    }
                });
                FileOutputStream fos = new FileOutputStream(path);
                int MAX_BYTES = 8192;
                byte[] buf = new byte[MAX_BYTES];    //read 8k at a time
                logger.log(Level.INFO, "Writing Unalloc file to " + path.getPath());
                
                progress.start(us.size());
                int count = 0;
                    for (LayoutFile f : us.getLayouts()) {
                        long offset = 0L;
                        while (offset != f.getSize() && !canceled) {
                            offset += f.read(buf, offset, MAX_BYTES);    //Offset + Bytes read
                            fos.write(buf);
                        }
                        progress.progress("processing block " + ++count + "of " + us.size(), count);
                    }
                progress.finish();
                fos.flush();
                fos.close();
                
                if(canceled){
                   path.delete();
                   logger.log(Level.INFO, "Canceled extraction of " + path.getName() + " and deleted file");
                }
                else{
                    logger.log(Level.INFO, "Finished writing unalloc file " + path.getPath());
                }
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
        protected void done(){
            lockedVols.remove(path.getName());
            if(++numDone == LstUnallocs.size()){
                runningOnImage = false;
                numDone = 0;
            }
        }
    }
    
    /**
     * Determines if an image has a volume system or not.
     * @param img The Image to analyze
     * @return True if there are Volume Systems present
     */
    private boolean hasVolumeSystem(Image img){
        try{
         return (img.getChildren().get(0) instanceof VolumeSystem);
        } catch(TskCoreException tce){
            logger.log(Level.WARNING, "Unable to determine if image has a volume system, extraction may be incomplete", tce);
            return false;
        }
    }
    
    /**
     * Gets the volumes on an given image.
     * @param img The image to analyze
     * @return A list of volumes from the image. Returns an empty list if no matches.
     */
    private List<Volume> getVolumes(Image img) {
        List<Volume> lstVol = new ArrayList<Volume>();
        try {
            for (Content v : img.getChildren().get(0).getChildren()) {
                if(v instanceof Volume){
                    lstVol.add((Volume)v);
                }
            }
        } catch (TskCoreException tce) {
            logger.log(Level.WARNING, "Could not get volume information from image. Extraction may be incomplete", tce);
        }
        return lstVol;
    }
        
        


    /**
     * Private visitor class for going through a Content file and grabbing unallocated files.
     */
    private static class UnallocVisitor extends ContentVisitor.Default<List<LayoutFile>> {

        /**
         * If the volume has no FileSystem, then it will call this method to return the single instance of unallocated space.
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
         * If the visitor finds a FileSystem, it will filter the results for directories and return on the Root Dir.
         * @param fs the FileSystem the visitor encountered
         * @return A list<LayoutFile> containing the layout files from subsequent Visits(), returns null if it fails
         */
        @Override
        public List<LayoutFile> visit(FileSystem fs) {
            try {
                for (Content c : fs.getChildren()){
                    if(((AbstractFile)c).isRoot()){
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
         * @param ld LayoutDirectory the visitor encountered
         * @return A list<LayoutFile> containing all the LayoutFile in ld, returns null if it fails
         */
        @Override
        public List<LayoutFile> visit(LayoutDirectory ld){
            try{
                List<LayoutFile> lflst = new ArrayList<LayoutFile>();
                for(Content layout : ld.getChildren()){
                    lflst.add((LayoutFile)layout);
                }
                return lflst;
            } catch(TskCoreException tce){
                logger.log(Level.WARNING, "Could not get list of Layout Files, failed at visiting Layout Directory", tce);
            }
            return null;
        }

        /**
         * The only time this visitor should ever encounter a directory is when parsing over Root
         * @param dir the directory this visitor encountered
         * @return A list<LayoutFile> containing LayoutFiles encountered during subsequent Visits(), returns null if it fails
         */
        @Override
        public List<LayoutFile> visit(Directory dir) {
            try {
                for (Content c : dir.getChildren()) {
                    if(c instanceof LayoutDirectory){
                        return c.accept(this);
                    }
                }
            }catch (TskCoreException tce) {
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
     * Ensures that the single Unalloc File is in proper order, and that the bytes
     * are continuous.
     */
    private class SortObjId implements Comparator<LayoutFile>{
        
        @Override
        public int compare(LayoutFile o1, LayoutFile o2) {
            if(o1.getId() == o2.getId()){
                return 0;
            } 
            if(o1.getId() > o2.getId()){
                return -1;
            }
            else{
                return 1;
            }
        }
    }
    
    /**
     * Private class for assisting in the running the action over an image with multiple volumes.
     */
    private class UnallocStruct{
        private List<LayoutFile> llf;
        private long VolumeId;
        private long ImageId;
        private String ImageName;
        
        /**
         * Contingency constructor in event no VolumeSystem exists on an Image.
         * @param img Image file to be analyzed
         */
        UnallocStruct(Image img){
            this.llf = getUnallocFiles(img);
            this.VolumeId = 0;
            this.ImageId = img.getId();
            this.ImageName = img.getName();
        }

        /**
         * Default constructor for extracting info from Volumes.
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
            this.llf = getUnallocFiles(volu);
            Collections.sort(llf, new SortObjId());
        }

        //Getters
        int size() {
            return llf.size();
        }
        long getVolumeId(){
            return this.VolumeId;
        }
        long getImageId(){
            return this.ImageId;
        }
        String getImageName(){
            return this.ImageName;
        }
        List<LayoutFile> getLayouts(){
            return this.llf;
        }
        
        
        
    }

}
