package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import static org.sleuthkit.autopsy.modules.hashdatabase.NSRLHashSetPreparer.BUFFER_SIZE;

public abstract class HashSetPreparer {

    protected String outputDirectory;
    protected String extractedFile;
    private HashDbManager.HashDb hashDatabase;

    protected HashSetPreparer(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public abstract HashSetPreparer createInstance(JProgressBar progressbar, String outputDirectory);

    public abstract void downloadFullHashSet() throws HashSetUpdateException;

    public abstract void downloadDeltaHashSet() throws HashSetUpdateException;

    public abstract void extract() throws HashSetUpdateException;

    public void index() throws HashSetUpdateException {
        HashDbManager.getInstance().indexHashDatabase(this.hashDatabase);
    }

    public abstract HashDbManager.HashDb.KnownFilesType getHashSetType();

    /**
     *
     * @return
     */
    public abstract String getName();

    public void addHashSetToDatabase() throws HashSetUpdateException {
        
        try {
            if (!HashDbManager.getInstance().getAllHashSets().stream().anyMatch(hashDb -> {
                return hashDb.getHashSetName().equals(getName());
            })) {
                this.hashDatabase = HashDbManager.getInstance().addExistingHashDatabase(getName(), extractedFile, true, false, getHashSetType());
            }
        } catch (HashDbManager.HashDbManagerException ex) {
            throw new HashSetUpdateException("Error while adding HashSet to Autopsy DB");
        }

    }

    protected static void createTargetDircectoryIfNotExists(String directory) {
        File directoryObject = new File(directory);
        if (!directoryObject.exists()) {
            directoryObject.mkdir();
        }
    }

    protected static void copyStreams(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        int counter = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    protected void downloadFile(String source, String destination, JProgressBar progressbar) throws IOException, HashSetUpdateException {
        downloadFile(source, destination, progressbar, 0);
    }

    protected void downloadFile(String source, String destination, JProgressBar progressbar, int offset) throws MalformedURLException, IOException, FileNotFoundException, HashSetUpdateException {
        URL sourceURL = new URL(source);

        HttpURLConnection conn = (HttpURLConnection) sourceURL.openConnection();

        InputStream inputStream = conn.getInputStream();
        long fileSize = conn.getContentLengthLong();

        skipOffset(inputStream, offset);

        File target = new File(destination);
        target.createNewFile();
        OutputStream b;
        b = new FileOutputStream(target);

        progressbar.setMaximum(bytesToWorkUnits(fileSize));

        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        int counter = 0;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            counter += bytesRead;
            b.write(buffer, 0, bytesRead);
            updateProgressBar(bytesToWorkUnits(counter), progressbar);
        }
    }

    protected static String getFileNameFromURL(String URL) {
        return URL.substring(URL.lastIndexOf('/') + 1);
    }

    // the workunit for the progressbar is an int
    // some files have more bytes than Integer.MAX_VALUE
    protected static int bytesToWorkUnits(long bytes) {
        return (int) (bytes / 1024 / 1024);
    }

    protected String getDirectory() {
        return outputDirectory + "/" + getName() + "/";
    }

    private static void skipOffset(InputStream inputStream, int offset) throws IOException, HashSetUpdateException {
        byte[] buffer = new byte[offset];
        if (inputStream.read(buffer, 0, offset) != offset) {
            throw new HashSetUpdateException("was unable to ignore the offset");
        }
    }

    protected void updateProgressBar(int progessValue, JProgressBar progressbar) {
        SwingUtilities.invokeLater(() -> {
            progressbar.setValue(progessValue);
        });
    }

}
