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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

class HashDbSearchThread extends SwingWorker<Object, Void> {

    private Logger logger = Logger.getLogger(HashDbSearchThread.class.getName());
    private ProgressHandle progress;
    private Map<String, List<AbstractFile>> map;
    private ArrayList<String> hashes = new ArrayList<>();
    private AbstractFile file;

    HashDbSearchThread(AbstractFile file) {
        this.file = file;
        this.hashes.add(this.file.getMd5Hash());
    }

    HashDbSearchThread(ArrayList<String> hashes) {
        this.hashes = hashes;
    }

    @Override
    protected Object doInBackground() throws Exception {
        logger.log(Level.INFO, "Starting background processing for file search by MD5 hash."); //NON-NLS

        // Setup progress bar
        final String displayName = NbBundle.getMessage(this.getClass(), "HashDbSearchThread.name.searching");
        progress = ProgressHandle.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                if (progress != null) {
                    progress.setDisplayName(
                            NbBundle.getMessage(this.getClass(), "HashDbSearchThread.progress.cancellingSearch",
                                    displayName));
                }
                return HashDbSearchThread.this.cancel(true);
            }
        });
        // Start the progress bar as indeterminate
        progress.start();
        progress.switchToIndeterminate();

        // Do the querying
        map = HashDbSearcher.findFilesBymd5(hashes, progress, this);
        logger.log(Level.INFO, "Done background processing"); //NON-NLS

        return null;
    }

    @Override
    protected void done() {
        try {
            super.get(); //block and get all exceptions thrown while doInBackground()
        } catch (CancellationException ex) {
            logger.log(Level.INFO, "File search by MD5 hash was canceled."); //NON-NLS
        } catch (InterruptedException ex) {
            logger.log(Level.INFO, "File search by MD5 hash was interrupted."); //NON-NLS
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Fatal error during file search by MD5 hash.", ex); //NON-NLS
        } finally {
            progress.finish();
            if (!this.isCancelled()) {
                logger.log(Level.INFO, "File search by MD5 hash completed without cancellation."); //NON-NLS
                // If its a right click action, we are given an FsContent which
                // is the file right clicked, so we can remove that from the search
                if (file != null) {
                    boolean quit = true;
                    for (List<AbstractFile> files : map.values()) {
                        files.remove(file);
                        if (!files.isEmpty()) {
                            quit = false;
                        }
                    }
                    if (quit) {
                        JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                NbBundle.getMessage(this.getClass(),
                                        "HashDbSearchThread.noMoreFilesWithMD5Msg"));
                        return;
                    }
                }
                HashDbSearchManager man = new HashDbSearchManager(map);
                man.execute();
            } else {
                logger.log(Level.INFO, "File search by MD5 hash was canceled."); //NON-NLS
            }
        }
    }

}
