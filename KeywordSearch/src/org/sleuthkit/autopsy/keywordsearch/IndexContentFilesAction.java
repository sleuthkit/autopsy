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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.AddImageAction;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskException;

/**
 * Action adds all supported files from the given Content object and its
 * children to the Solr index.
 */
public class IndexContentFilesAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(IndexContentFilesAction.class.getName());
    private static final int MAX_STRING_EXTRACT_SIZE = 10 * (1 << 10) * (1 << 10);
    private Content c;
    private String name;
    private Server.Core solrCore;

    public enum IngestStatus {

        NOT_INGESTED, INGESTED, EXTRACTED_INGESTED, SKIPPED_EXTRACTION,};
    //keep track of ingest status for various types of content
    //could also be useful for reporting
    private Map<Long, IngestStatus> ingestStatus;
    private int problemFilesCount;

    /**
     * New action
     * @param c source Content object to get files from
     * @param name name to refer to the source by when displaying progress
     */
    public IndexContentFilesAction(Content c, String name) {
        this(c, name, KeywordSearch.getServer().getCore());
    }

    IndexContentFilesAction(Content c, String name, Server.Core solrCore) {
        super("Index files...");
        this.c = c;
        this.name = name;
        this.solrCore = solrCore;
        ingestStatus = new HashMap<Long, IngestStatus>();
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        // create the popUp window to display progress
        String title = "Indexing files in " + name;

        final JFrame frame = new JFrame(title);
        final JDialog popUpWindow = new JDialog(frame, title, true); // to make the popUp Window  modal

        // initialize panel
        final IndexProgressPanel panel = new IndexProgressPanel();

        final SwingWorker task = new SwingWorker<Integer, String>() {

            @Override
            protected Integer doInBackground() throws Exception {
                Ingester ingester = solrCore.getIngester();

                this.publish("Categorizing files to index. ");

                GetFilesContentVisitor ingestableV = new GetIngestableFilesContentVisitor();
                GetFilesContentVisitor allV = new GetAllFilesContentVisitor();

                Collection<FsContent> ingestableFiles = c.accept(ingestableV);
                Collection<FsContent> allFiles = c.accept(allV);

                //calculate non ingestable Collection (complement of allFiles / ingestableFiles)
                Collection<FsContent> nonIngestibleFiles = new TreeSet<FsContent>(new Comparator<FsContent>() {

                    @Override
                    public int compare(FsContent fsc1, FsContent fsc2) {
                        return (int) (fsc1.getId() - fsc2.getId());

                    }
                });
                nonIngestibleFiles.addAll(allFiles);
                nonIngestibleFiles.removeAll(ingestableFiles);

                // track number complete or with errors
                problemFilesCount = 0;
                ingestStatus.clear();

                //work on known files first
                Collection<FsContent> ingestFailedFiles = processIngestible(ingester, ingestableFiles);
                nonIngestibleFiles.addAll(ingestFailedFiles);

                //work on unknown files
                //TODO should be an option somewhere in GUI (known vs unknown files)
                processNonIngestible(ingester, nonIngestibleFiles);

                ingester.commit();

                //signal a potential change in number of indexed files
                try {
                    final int numIndexedFiles = KeywordSearch.getServer().getCore().queryNumIndexedFiles();
                    KeywordSearch.changeSupport.firePropertyChange(KeywordSearch.NUM_FILES_CHANGE_EVT, null, new Integer(numIndexedFiles));
                } catch (SolrServerException se) {
                    logger.log(Level.SEVERE, "Error executing Solr query to check number of indexed files: ", se);
                }

                return problemFilesCount;
            }

            private Collection<FsContent> processIngestible(Ingester ingester, Collection<FsContent> fscc) {
                Collection<FsContent> ingestFailedCol = new ArrayList<FsContent>();
                
                setProgress(0);
                int finishedFiles = 0;
                final int totalFilesCount = fscc.size();
                for (FsContent f : fscc) {
                    if (isCancelled()) {
                        return ingestFailedCol;
                    }
                    this.publish("Indexing " + (finishedFiles + 1) + "/" + totalFilesCount + ": " + f.getName());
                    try {
                        ingester.ingest(f);
                        ingestStatus.put(f.getId(), IngestStatus.INGESTED);
                    } catch (IngesterException ex) {
                        ingestFailedCol.add(f);
                        ingestStatus.put(f.getId(), IngestStatus.NOT_INGESTED);
                        logger.log(Level.INFO, "Ingester failed with file '" + f.getName() + "' (id: " + f.getId() + ").", ex);
                    }
                    setProgress(++finishedFiles * 100 / totalFilesCount);
                }
                return ingestFailedCol;
            }

            private void processNonIngestible(Ingester ingester, Collection<FsContent> fscc) {
                setProgress(0);
                int finishedFiles = 0;
                final int totalFilesCount = fscc.size();
                
                for (FsContent f : fscc) {
                    if (isCancelled()) {
                        return;
                    }
                    this.publish("String extracting/Indexing " + (finishedFiles + 1) + "/" + totalFilesCount + ": " + f.getName());

                    if (f.getSize() < MAX_STRING_EXTRACT_SIZE) {
                        if (!extractAndIngest(ingester, f)) {
                            ingestStatus.put(f.getId(), IngestStatus.NOT_INGESTED);
                            problemFilesCount++;
                            logger.log(Level.INFO, "Failed to extract strings and ingest, file '" + f.getName() + "' (id: " + f.getId() + ").");
                        } else {
                            ingestStatus.put(f.getId(), IngestStatus.EXTRACTED_INGESTED);
                        }
                    } else {
                        ingestStatus.put(f.getId(), IngestStatus.SKIPPED_EXTRACTION);
                    }

                    setProgress(++finishedFiles * 100 / totalFilesCount);
                }
            }

            @Override
            protected void done() {
                int problemFiles = 0;

                try {
                    if (!this.isCancelled()) {
                        problemFiles = get();
                    }

                } catch (InterruptedException ex) {
                    // shouldn't be interrupted except by cancel
                    throw new RuntimeException(ex);
                } catch (ExecutionException ex) {
                    logger.log(Level.SEVERE, "Fatal error during ingest.", ex);
                } finally {
                    popUpWindow.setVisible(false);
                    popUpWindow.dispose();

                    // notify user if there were problem files
                    if (problemFiles > 0) {
                        displayProblemFilesDialog(problemFiles);
                    }
                }
            }

            @Override
            protected void process(List<String> messages) {

                // display the latest message
                if (!messages.isEmpty()) {
                    panel.setStatusText(messages.get(messages.size() - 1));
                }

                panel.setProgressBar(getProgress());
            }
        };

        panel.addCancelButtonActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                task.cancel(true);
            }
        });

        popUpWindow.add(panel);
        popUpWindow.pack();
        popUpWindow.setResizable(false);

        // set the location of the popUp Window on the center of the screen
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        double w = popUpWindow.getSize().getWidth();
        double h = popUpWindow.getSize().getHeight();
        popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));

        popUpWindow.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                // deal with being Xed out of
                if (!task.isDone()) {
                    task.cancel(true);
                }
            }
        });


        task.execute();
        // display the window
        popUpWindow.setVisible(true);
    }

    private boolean extractAndIngest(Ingester ingester, FsContent f) {
        boolean success = false;
        FsContentStringStream fscs = new FsContentStringStream(f, FsContentStringStream.Encoding.ASCII);
        try {
            fscs.convert();
            ingester.ingest(fscs);
            success = true;
        } catch (TskException tskEx) {
            logger.log(Level.INFO, "Problem extracting string from file: '" + f.getName() + "' (id: " + f.getId() + ").", tskEx);
        } catch (IngesterException ingEx) {
            logger.log(Level.INFO, "Ingester had a problem with extracted strings from file '" + f.getName() + "' (id: " + f.getId() + ").", ingEx);
        }
        return success;
    }

    private void displayProblemFilesDialog(int problemFiles) {
        final Component parentComponent = null; // Use default window frame.
        final String message = "Had trouble indexing " + problemFiles + " of the files. See the log for details.";
        final String title = "Problem indexing some files";
        final int messageType = JOptionPane.WARNING_MESSAGE;
        JOptionPane.showMessageDialog(
                parentComponent,
                message,
                title,
                messageType);
    }

    @ServiceProvider(service = AddImageAction.IndexImageTask.class)
    public static class IndexImageTask implements AddImageAction.IndexImageTask {

        @Override
        public void runTask(Image newImage) {
            (new IndexContentFilesAction(newImage, "new image")).actionPerformed(null);
        }
    }
}