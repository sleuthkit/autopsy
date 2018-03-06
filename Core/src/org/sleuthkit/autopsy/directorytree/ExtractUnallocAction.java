/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractContent;
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
final class ExtractUnallocAction extends AbstractAction {
    private static final Logger logger = Logger.getLogger(ExtractUnallocAction.class.getName());

    private final List<OutputFileData> filesToExtract = new ArrayList<>();
    private static final Set<String> volumesInProgress = new HashSet<>();
    private static final Set<Long> imagesInProgress = new HashSet<>();
    private long currentImage = 0L;
    private final boolean isImage;

    public ExtractUnallocAction(String title, Volume volume) {
        super(title);
        isImage = false;
        OutputFileData outputFileData = new OutputFileData(volume);
        filesToExtract.add(outputFileData);
    }

    public ExtractUnallocAction(String title, Image image) {
        super(title);
        isImage = true;
        currentImage = image.getId();
        if (hasVolumeSystem(image)) {
            for (Volume v : getVolumes(image)) {
                OutputFileData outputFileData = new OutputFileData(v);
                filesToExtract.add(outputFileData);
            }
        } else {
            OutputFileData outputFileData = new OutputFileData(image);
            filesToExtract.add(outputFileData);
        }
    }

    /**
     * Writes the unallocated files to
     * $CaseDir/Export/ImgName-Unalloc-ImgObjectID-VolumeID.dat
     *
     * @param e
     */
    @NbBundle.Messages({"# {0} - fileName",
            "ExtractUnallocAction.volumeInProgress=Already extracting unallocated space into {0} - will skip this volume",
            "ExtractUnallocAction.volumeError=Error extracting unallocated space from volume",
            "ExtractUnallocAction.noFiles=No unallocated files found on volume",
            "ExtractUnallocAction.imageError=Error extracting unallocated space from image",
            "ExtractUnallocAction.noOpenCase.errMsg=No open case available."})
    @Override
    public void actionPerformed(ActionEvent e) {
        if (filesToExtract != null && filesToExtract.size() > 0) {
            // This check doesn't absolutely guarantee that the image won't be in progress when we make the worker, 
            // but in general it will suffice.
            if (isImage && isImageInProgress(currentImage)) {
                MessageNotifyUtil.Message.info(NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.notifyMsg.unallocAlreadyBeingExtr.msg"));
                //JOptionPane.showMessageDialog(new Frame(), "Unallocated Space is already being extracted on this Image. Please select a different Image.");
                return;
            }
            Case openCase;
            try {
                openCase = Case.getOpenCase();
            } catch (NoCurrentCaseException ex) {
                MessageNotifyUtil.Message.info(Bundle.ExtractAction_noOpenCase_errMsg());
                return;
            }
            List<OutputFileData> copyList = new ArrayList<OutputFileData>() {
                {
                    addAll(filesToExtract);
                }
            };

            JFileChooser fileChooser = new JFileChooser() {
                @Override
                public void approveSelection() {
                    File f = getSelectedFile();
                    if (!f.exists() && getDialogType() == SAVE_DIALOG || !f.canWrite()) {
                        JOptionPane.showMessageDialog(this, NbBundle.getMessage(this.getClass(),
                                "ExtractUnallocAction.msgDlg.folderDoesntExist.msg"));
                        return;
                    }
                    super.approveSelection();
                }
            };

            fileChooser.setCurrentDirectory(new File(openCase.getExportDirectory()));
            fileChooser.setDialogTitle(
                    NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.dlgTitle.selectDirToSaveTo.msg"));
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setAcceptAllFileFilterUsed(false);
            int returnValue = fileChooser.showSaveDialog((Component) e.getSource());
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                String destination = fileChooser.getSelectedFile().getPath();
                for (OutputFileData outputFileData : filesToExtract) {
                    outputFileData.setPath(destination);
                    
                    if (outputFileData.layoutFiles != null && outputFileData.layoutFiles.size() > 0 && (! isVolumeInProgress(outputFileData.getFileName()))) {
                        //Format for single Unalloc File is ImgName-Unalloc-ImgObjectID-VolumeID.dat  
                        
                        // Check if there is already a file with this name
                        if (outputFileData.fileInstance.exists()) {
                            int res = JOptionPane.showConfirmDialog(new Frame(), NbBundle.getMessage(this.getClass(),
                                    "ExtractUnallocAction.confDlg.unallocFileAlreadyExist.msg",
                                    outputFileData.getFileName()));
                            if (res == JOptionPane.YES_OPTION) {
                                // If the user wants to overwrite, delete the exising output file
                                outputFileData.fileInstance.delete();
                            } else {
                                // Otherwise remove it from the list of output files
                                copyList.remove(outputFileData);
                            }
                        }
                        
                        if (!isImage & !copyList.isEmpty()) {
                            try{
                                ExtractUnallocWorker worker = new ExtractUnallocWorker(outputFileData);
                                worker.execute();
                            } catch (Exception ex){
                                logger.log(Level.WARNING, "Already extracting unallocated space into " + outputFileData.getFileName());
                                MessageNotifyUtil.Message.info(NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.volumeInProgress", outputFileData.getFileName()));
                            }
                        }
                    } else {
                        // The output file for this volume could not be created for one of the following reasons
                        if (outputFileData.layoutFiles == null){
                            MessageNotifyUtil.Message.info(NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.volumeError"));
                            logger.log(Level.SEVERE, "Tried to get unallocated content but the list of unallocated files was null"); //NON-NLS
                        } else if (outputFileData.layoutFiles.isEmpty()){
                            MessageNotifyUtil.Message.info(NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.noFiles"));
                            logger.log(Level.WARNING, "No unallocated files found in volume"); //NON-NLS
                            copyList.remove(outputFileData);
                        } else {
                            MessageNotifyUtil.Message.info(NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.volumeInProgress", outputFileData.getFileName()));
                            logger.log(Level.WARNING, "Tried to get unallocated content but the volume is locked");  // NON_NLS
                            copyList.remove(outputFileData);
                        }
                    }
                }
                
                // This needs refactoring. The idea seems to be that we'll take advantage of the loop above to
                // check whether each output file exists but wait until this point to make a worker
                // to extract everything (the worker in the above loop doesn't get created because isImage is true)
                // It's also unclear to me why we need the two separate worker types.
                if (isImage && !copyList.isEmpty()) {
                    try{
                        ExtractUnallocWorker worker = new ExtractUnallocWorker(copyList);
                        worker.execute();
                    } catch (Exception ex){
                        logger.log(Level.WARNING, "Error creating ExtractUnallocWorker", ex);
                        MessageNotifyUtil.Message.info(NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.imageError"));
                    }
                }
            }
        }

    }

    /**
     * Gets all the unallocated files in a given Content.
     *
     * @param c Content to get Unallocated Files from
     *
     * @return A list<LayoutFile> if it didn't crash List may be empty.
     */
    private List<LayoutFile> getUnallocFiles(Content c) {
        UnallocVisitor uv = new UnallocVisitor();
        try {
            for (Content contentChild : c.getChildren()) {
                if (contentChild instanceof AbstractContent) {
                    return contentChild.accept(uv);  //call on first non-artifact child added to database
                }
            }
        } catch (TskCoreException tce) {
            logger.log(Level.WARNING, "Couldn't get a list of Unallocated Files, failed at sending out the visitor ", tce); //NON-NLS
        }
        return Collections.emptyList();
    }
    
    synchronized static private void addVolumeInProgress(String volumeOutputFileName) throws TskCoreException {
        if(volumesInProgress.contains(volumeOutputFileName)){
            throw new TskCoreException("Already writing unallocated space to " + volumeOutputFileName);
        }     
        volumesInProgress.add(volumeOutputFileName);
    }
    
    synchronized static private void removeVolumeInProgress(String volumeOutputFileName){
        volumesInProgress.remove(volumeOutputFileName);
    }
    
    synchronized static private boolean isVolumeInProgress(String volumeOutputFileName){
        return volumesInProgress.contains(volumeOutputFileName);
    }
    
    synchronized static private void addImageInProgress(Long id) throws TskCoreException {
        if(imagesInProgress.contains(id)){
            throw new TskCoreException("Image " + id + " is in use");
        }     
        imagesInProgress.add(id);
    }
    
    synchronized static private void removeImageInProgress(Long id){
        imagesInProgress.remove(id);
    }   
    
    synchronized static private boolean isImageInProgress(Long id){
        return imagesInProgress.contains(id);
    }
    
    
    /**
     * Private class for dispatching the file IO in a background thread.
     */
    private class ExtractUnallocWorker extends SwingWorker<Integer, Integer> {

        private ProgressHandle progress;
        private boolean canceled = false;
        private List<OutputFileData> outputFileDataList = new ArrayList<>();
        private File currentlyProcessing;
        private int totalSizeinMegs;
        long totalBytes = 0;

        ExtractUnallocWorker(OutputFileData outputFileData) throws TskCoreException {
            //Getting the total megs this worker is going to be doing
            addVolumeInProgress(outputFileData.getFileName());
            outputFileDataList.add(outputFileData);
            totalBytes = outputFileData.getSizeInBytes();
            totalSizeinMegs = toMb(totalBytes);
        }

        ExtractUnallocWorker(List<OutputFileData> outputFileDataList) throws TskCoreException {
            addImageInProgress(currentImage);
            
            //Getting the total megs this worker is going to be doing            
            for (OutputFileData outputFileData : outputFileDataList) {
                try{
                    // If a volume is locked, skip it but continue trying to process any other requested volumes
                    addVolumeInProgress(outputFileData.getFileName());
                    totalBytes += outputFileData.getSizeInBytes();
                    this.outputFileDataList.add(outputFileData);
                } catch (TskCoreException ex){
                    logger.log(Level.WARNING, "Already extracting data into " + outputFileData.getFileName());
                }
            }
            
            // If we don't have anything to output (because of locking), throw an exception
            if(this.outputFileDataList.isEmpty()){
                throw new TskCoreException("No unallocated files can be extracted");
            }
            
            totalSizeinMegs = toMb(totalBytes);
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
                progress = ProgressHandle.createHandle(
                        NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.progress.extractUnalloc.title"), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        logger.log(Level.INFO, "Canceling extraction of unallocated space"); //NON-NLS
                        canceled = true;
                        if (progress != null) {
                            progress.setDisplayName(NbBundle.getMessage(this.getClass(),
                                    "ExtractUnallocAction.progress.displayName.cancelling.text"));
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
                for (OutputFileData outputFileData : this.outputFileDataList) {
                    currentlyProcessing = outputFileData.getFile();
                    logger.log(Level.INFO, "Writing Unalloc file to " + currentlyProcessing.getPath()); //NON-NLS
                    OutputStream outputStream = new FileOutputStream(currentlyProcessing);
                    long bytes = 0;
                    int i = 0;
                    while (i < outputFileData.getLayouts().size() && bytes != outputFileData.getSizeInBytes()) {
                        LayoutFile layoutFile = outputFileData.getLayouts().get(i);
                        long offsetPerFile = 0L;
                        int bytesRead;
                        while (offsetPerFile != layoutFile.getSize() && !canceled) {
                            if (++kbs % 128 == 0) {
                                mbs++;
                                progress.progress(NbBundle.getMessage(this.getClass(),
                                        "ExtractUnallocAction.processing.counter.msg",
                                        mbs, totalSizeinMegs), mbs - 1);
                            }
                            bytesRead = layoutFile.read(buf, offsetPerFile, MAX_BYTES);
                            offsetPerFile += bytesRead;
                            outputStream.write(buf, 0, bytesRead);
                        }
                        bytes += layoutFile.getSize();
                        i++;
                    }
                    outputStream.flush();
                    outputStream.close();

                    if (canceled) {
                        outputFileData.getFile().delete();
                        logger.log(Level.INFO, "Canceled extraction of " + outputFileData.getFileName() + " and deleted file"); //NON-NLS
                    } else {
                        logger.log(Level.INFO, "Finished writing unalloc file " + outputFileData.getFile().getPath()); //NON-NLS
                    }
                }
                progress.finish();

            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not create Unalloc File; error writing file", ex); //NON-NLS
                return -1;
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Could not create Unalloc File; error getting image info", ex); //NON-NLS
                return -1;
            }
            return 1;
        }

        @Override
        protected void done() {
            if (isImage) {
                removeImageInProgress(currentImage);
            }
            for (OutputFileData u : outputFileDataList) {
                removeVolumeInProgress(u.getFileName());
            }

            try {
                get();
                if (!canceled && !outputFileDataList.isEmpty()) {
                    MessageNotifyUtil.Notify.info(NbBundle.getMessage(this.getClass(),
                            "ExtractUnallocAction.done.notifyMsg.completedExtract.title"),
                            NbBundle.getMessage(this.getClass(),
                                    "ExtractUnallocAction.done.notifyMsg.completedExtract.msg",
                                    outputFileDataList.get(0).getFile().getParent()));
                }
            } catch (InterruptedException | ExecutionException ex) {
                MessageNotifyUtil.Notify.error(
                        NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.done.errMsg.title"),
                        NbBundle.getMessage(this.getClass(), "ExtractUnallocAction.done.errMsg.msg", ex.getMessage()));
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            }
        }
    }

    /**
     * Determines if an image has a volume system or not.
     *
     * @param img The Image to analyze
     *
     * @return True if there are Volume Systems present
     */
    private boolean hasVolumeSystem(Image img) {
        try {
            for (Content c : img.getChildren()) {
                if (c instanceof VolumeSystem) {
                    return true;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to determine if image has a volume system, extraction may be incomplete", ex); //NON-NLS
        }
        return false;
    }

    /**
     * Gets the volumes on an given image.
     *
     * @param img The image to analyze
     *
     * @return A list of volumes from the image. Returns an empty list if no
     *         matches.
     */
    private List<Volume> getVolumes(Image img) {
        List<Volume> volumes = new ArrayList<>();
        try {
            for (Content v : img.getChildren().get(0).getChildren()) {
                if (v instanceof Volume) {
                    volumes.add((Volume) v);
                }
            }
        } catch (TskCoreException tce) {
            logger.log(Level.WARNING, "Could not get volume information from image. Extraction may be incomplete", tce); //NON-NLS
        }
        return volumes;
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
         *
         * @return A list<LayoutFile> of size 1
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
         *
         * @return A list<LayoutFile> containing the layout files from
         *         subsequent Visits(), or an empty list
         */
        @Override
        public List<LayoutFile> visit(FileSystem fs) {
            try {
                for (Content c : fs.getChildren()) {
                    if (c instanceof AbstractFile) {
                        if (((AbstractFile) c).isRoot()) {
                            return c.accept(this);
                        }
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Couldn't get a list of Unallocated Files, failed at visiting FileSystem " + fs.getId(), ex); //NON-NLS
            }
            return Collections.emptyList();
        }

        /**
         * LayoutDirectory has all the Layout(Unallocated) files
         *
         * @param vd VirtualDirectory the visitor encountered
         *
         * @return A list<LayoutFile> containing all the LayoutFile in ld, or an
         *         empty list.
         */
        @Override
        public List<LayoutFile> visit(VirtualDirectory vd) {
            try {
                List<LayoutFile> layoutFiles = new ArrayList<>();
                for (Content layout : vd.getChildren()) {
                    if (layout instanceof LayoutFile) {
                        layoutFiles.add((LayoutFile) layout);
                    }
                }
                return layoutFiles;
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Could not get list of Layout Files, failed at visiting Layout Directory", ex); //NON-NLS
            }
            return Collections.emptyList();
        }

        /**
         * The only time this visitor should ever encounter a directory is when
         * parsing over Root
         *
         * @param dir the directory this visitor encountered
         *
         * @return A list<LayoutFile> containing LayoutFiles encountered during
         *         subsequent Visits(), or an empty list.
         */
        @Override
        public List<LayoutFile> visit(Directory dir) {
            try {
                for (Content c : dir.getChildren()) {
                    // Only the $Unalloc dir will contain unallocated files
                    if ((c instanceof VirtualDirectory) && (c.getName().equals(VirtualDirectory.NAME_UNALLOC))) {
                        return c.accept(this);
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Couldn't get a list of Unallocated Files, failed at visiting Directory " + dir.getId(), ex); //NON-NLS
            }
            return Collections.emptyList();
        }

        @Override
        protected List<LayoutFile> defaultVisit(Content cntnt) {
            return Collections.emptyList();
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
    private class OutputFileData {

        private List<LayoutFile> layoutFiles;
        private final long sizeInBytes;
        private long volumeId;
        private long imageId;
        private String imageName;
        private final String fileName;
        private File fileInstance;

        /**
         * Contingency constructor in event no VolumeSystem exists on an Image.
         *
         * @param img Image file to be analyzed
         */
        OutputFileData(Image img) {
            this.layoutFiles = getUnallocFiles(img);
            Collections.sort(layoutFiles, new SortObjId());
            this.volumeId = 0;
            this.imageId = img.getId();
            this.imageName = img.getName();
            this.fileName = this.imageName + "-Unalloc-" + this.imageId + "-" + 0 + ".dat"; //NON-NLS
            this.fileInstance = new File(Case.getCurrentCase().getExportDirectory() + File.separator + this.fileName);
            this.sizeInBytes = calcSizeInBytes();
        }

        /**
         * Default constructor for extracting info from Volumes.
         *
         * @param volume Volume file to be analyzed
         */
        OutputFileData(Volume volume) {
            try {
                this.imageName = volume.getDataSource().getName();
                this.imageId = volume.getDataSource().getId();
                this.volumeId = volume.getId();
            } catch (TskCoreException tce) {
                logger.log(Level.WARNING, "Unable to properly create ExtractUnallocAction, extraction may be incomplete", tce); //NON-NLS
                this.imageName = "";
                this.imageId = 0;
            }
            this.fileName = this.imageName + "-Unalloc-" + this.imageId + "-" + volumeId + ".dat"; //NON-NLS
            this.fileInstance = new File(Case.getCurrentCase().getExportDirectory() + File.separator + this.fileName);
            this.layoutFiles = getUnallocFiles(volume);
            Collections.sort(layoutFiles, new SortObjId());
            this.sizeInBytes = calcSizeInBytes();
        }

        //Getters
        int size() {
            return layoutFiles.size();
        }

        private long calcSizeInBytes() {
            long size = 0L;
            for (LayoutFile f : layoutFiles) {
                size += f.getSize();
            }
            return size;
        }

        long getSizeInBytes() {
            return this.sizeInBytes;
        }

        long getVolumeId() {
            return this.volumeId;
        }

        long getImageId() {
            return this.imageId;
        }

        String getImageName() {
            return this.imageName;
        }

        List<LayoutFile> getLayouts() {
            return this.layoutFiles;
        }

        String getFileName() {
            return this.fileName;
        }

        File getFile() {
            return this.fileInstance;
        }

        void setPath(String path) {
            this.fileInstance = new File(path + File.separator + this.fileName);
        }
    }
}
