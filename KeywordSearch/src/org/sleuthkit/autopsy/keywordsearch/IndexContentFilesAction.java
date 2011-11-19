package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;

public class IndexContentFilesAction extends AbstractAction {

    private Content c;
    private String name;
    private static final Logger logger = Logger.getLogger(IndexContentFilesAction.class.getName());

    public IndexContentFilesAction(Content c, String name) {
        super("Index files...");
        this.c = c;
        this.name = name;
    }

    @Override
    public void actionPerformed(ActionEvent e) {


        // create the popUp window for it
        String title = "Indexing files in " + name;
        
        final JFrame frame = new JFrame(title);
        final JDialog popUpWindow = new JDialog(frame, title, true); // to make the popUp Window  modal

        // initialize panel
        final IndexProgressPanel panel = new IndexProgressPanel();

        final SwingWorker task = new SwingWorker<Void, String>() {

            @Override
            protected Void doInBackground() throws Exception {
                Ingester ingester = new Ingester("http://localhost:8983/solr");

                Collection<FsContent> files = c.accept(new GetIngestableFilesContentVisitor());

                setProgress(0);

                int fileCount = files.size();
                int finishedFiles = 0;

                for (FsContent f : files) {
                    if (isCancelled()) {
                        return null;
                    }

                    this.publish("Indexing " + (finishedFiles+1) + "/" + fileCount + ": " + f.getName());

                    try {
                        ingester.ingest(f);
                    } catch (IngesterException ex) {
                        logger.log(Level.INFO, "Ingester had a problem with file '" + f.getName() + "' (id: " + f.getId() + ").", ex);
                    }

                    setProgress(++finishedFiles * 100 / fileCount);
                }

                ingester.commit();

                return null;
            }

            @Override
            protected void done() {
                try {
                    if (!this.isCancelled()) {
                        get();
                    }
                    
                } catch (InterruptedException ex) {
                    // shouldn't be interrupted except by cancel
                    throw new RuntimeException(ex);
                } catch (ExecutionException ex) {
                    logger.log(Level.SEVERE, "Fatal error during ingest.", ex);
                } finally {
                    popUpWindow.setVisible(false);
                    popUpWindow.dispose();
                }
            }

            @Override
            protected void process(List<String> messages) {

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
}