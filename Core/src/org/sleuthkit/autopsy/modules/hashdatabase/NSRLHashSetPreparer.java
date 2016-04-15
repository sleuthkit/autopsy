package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = HashSetPreparer.class)
public class NSRLHashSetPreparer extends HashSetPreparer {

    public static final int BUFFER_SIZE = 1024000;
    public static final String NIST_DEFAULT_CHECK = "http://www.nsrl.nist.gov/RDS/rds_2.51/zip-hash.txt";
    public static final String NIST_DEFAULT = "http://www.nsrl.nist.gov/RDS/rds_2.51/rds_251m.zip";
    //public static final String NIST_DEFAULT = "http://mirror.switch.ch/ftp/mirror/centos/7/isos/x86_64/CentOS-7-x86_64-NetInstall-1511.iso";
    private JProgressBar progressbar;
    private String downloadedfile;

    @Override
    public String getName() {
        return "NSRL";
    }

    public NSRLHashSetPreparer() {
        super("");
    }

    private NSRLHashSetPreparer(JProgressBar progressbar, String outputDirectory) {
        super(outputDirectory);
        
        this.progressbar = progressbar;
    }

    public HashSetPreparer createInstance(JProgressBar progressbar, String outputDirectory) {
        return new NSRLHashSetPreparer(progressbar, outputDirectory);
    }

    @Override
    public void download() throws HashSetUpdateException {
        try {
            createTargetDircectoryIfNotExists(getDirectory());
            downloadedfile = getDirectory() +  getFileNameFromURL(NIST_DEFAULT);
            downloadFile(NIST_DEFAULT, downloadedfile, progressbar);

        } catch (Exception e) {
            System.out.println(e);
        }
    }


    @Override
    public void extract() throws HashSetUpdateException {
        try {
            ZipFile zipFile = new ZipFile(downloadedfile);

            Enumeration<? extends ZipEntry> u = zipFile.entries();
            progressbar.setValue(0);
            progressbar.setMaximum(zipFile.size());
            int counter = 0;

            while (u.hasMoreElements()) {
                ZipEntry zipEntry = u.nextElement();
                InputStream inputStream = zipFile.getInputStream(zipEntry);
                File out = new File(getDirectory() + zipEntry.getName());
                OutputStream outputStream = new FileOutputStream(out);

                copyStreams(inputStream, outputStream);
                progressbar.setValue(++counter);
            }
        } catch (Exception e) {
            System.out.println("org.sleuthkit.autopsy.modules.hashdatabase.NSRLHashSetPreparer.extract()");
        }
    }



    @Override
    public void index() throws HashSetUpdateException {
        return;
    }



    @Override
    public boolean newVersionAvailable() {
        return true;
    }

}

class DownloadHashSet extends SwingWorker<Object, Object> {

    private static final int BUFFER_SIZE = 102400;

    private final String sourceURL;
    private final String targetFile;
    private ProgressHandle progressHandle;
    private final String targetDirectory;

    public DownloadHashSet(String sourceURL, String targetDirectory, String displayName) {
        this.sourceURL = sourceURL;
        this.targetFile = targetDirectory + getFileNameFromURL(sourceURL);
        this.targetDirectory = targetDirectory;

        initProgressHandle(displayName);

    }

    private void initProgressHandle(String displayName) {
        progressHandle = ProgressHandleFactory.createHandle(displayName, () -> {
            DownloadHashSet.this.cancel(true);
            return true;
        });
    }

    private String getFileNameFromURL(String URL) {
        return URL.substring(URL.lastIndexOf('/') + 1);
    }

    @Override
    protected Object doInBackground() throws Exception {
        try {
            createTargetDircectoryIfNotExists();
            URL source = new URL(sourceURL);

            HttpURLConnection conn = (HttpURLConnection) source.openConnection();

            InputStream a = conn.getInputStream();
            long fileSize = conn.getContentLengthLong();

            File target = new File(targetFile);
            target.createNewFile();
            OutputStream b;
            b = new FileOutputStream(target);

            progressHandle.start(bytesToWorkUnits(fileSize));

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            int counter = 0;
            while ((bytesRead = a.read(buffer)) != -1) {
                counter += bytesRead;
                b.write(buffer, 0, bytesRead);
                progressHandle.progress(bytesToWorkUnits(counter));
            }

            progressHandle.finish();

        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
    }

    // the workunit for the progressbar is an int
    // some files have more bytes than Integer.MAX_VALUE
    private int bytesToWorkUnits(long bytes) {
        return (int) (bytes / 1024 / 1024);
    }

    @Override
    public void done() {
        new ExtractHashSet(targetFile, "Extract NSRL HashSet").execute();

    }

    private void createTargetDircectoryIfNotExists() {
        File directory = new File(targetDirectory);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }
}

class ExtractHashSet extends SwingWorker<Object, Object> {

    private static final int BUFFER_SIZE = 102400;

    private ProgressHandle progressHandle;
    private final String file;

    public ExtractHashSet(String file, String displayName) {
        this.file = file;
        initProgressHandle(displayName);

    }

    private String getDirectory() {
        return file.substring(0, file.lastIndexOf('/'));
    }

    private void initProgressHandle(String displayName) {
        progressHandle = ProgressHandleFactory.createHandle(displayName, () -> {
            ExtractHashSet.this.cancel(true);
            return true;
        });
    }

    @Override
    protected Object doInBackground() throws Exception {

        ZipFile zipFile = new ZipFile(file);

        Enumeration<? extends ZipEntry> u = zipFile.entries();
        progressHandle.start(zipFile.size());
        int counter = 0;

        while (u.hasMoreElements()) {
            ZipEntry zipEntry = u.nextElement();
            InputStream inputStream = zipFile.getInputStream(zipEntry);
            File out = new File(getDirectory() + zipEntry.getName());
            OutputStream outputStream = new FileOutputStream(out);

            copyStreams(inputStream, outputStream);
            progressHandle.progress(++counter);
        }
        progressHandle.finish();
        return null;
    }

    private void copyStreams(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        int counter = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

}
