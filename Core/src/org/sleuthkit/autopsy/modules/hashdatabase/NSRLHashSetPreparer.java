package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JProgressBar;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = HashSetPreparer.class)
public class NSRLHashSetPreparer extends HashSetPreparer {

    public static final int BUFFER_SIZE = 1024000;
    public static final String NIST_DEFAULT_CHECK = "http://www.nsrl.nist.gov/RDS/rds_2.51/zip-hash.txt";
    public static final String NIST_DEFAULT = "http://www.nsrl.nist.gov/RDS/rds_2.51/rds_251m.zip";
    //public static final String NIST_DEFAULT = "http://mirror.switch.ch/ftp/mirror/centos/7/isos/x86_64/CentOS-7-x86_64-NetInstall-1511.iso";
    private final JProgressBar progressbar;
    private String downloadedfile;

    @Override
    public String getName() {
        return "NSRL";
    }

    public NSRLHashSetPreparer() {
        super("");
        progressbar = null;
    }

    private NSRLHashSetPreparer(JProgressBar progressbar, String outputDirectory) {
        super(outputDirectory);

        this.progressbar = progressbar;
    }

    public HashSetPreparer createInstance(JProgressBar progressbar, String outputDirectory) {
        return new NSRLHashSetPreparer(progressbar, outputDirectory);
    }

    @Override
    public void downloadFullHashSet() throws HashSetUpdateException {
        try {
            createTargetDircectoryIfNotExists(getDirectory());
            downloadedfile = getDirectory() + getFileNameFromURL(NIST_DEFAULT);
            downloadFile(NIST_DEFAULT, downloadedfile, progressbar);

        } catch (Exception e) {
            throw new HashSetUpdateException("error while downloading NSRL HashSet");
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
                updateProgressBar(counter++, progressbar);
            }
        } catch (Exception e) {
            throw new HashSetUpdateException("error while extracting NSRL HashSet");
        }
        extractedFile = getDirectory() + "NSRLFile.txt";
    }

    @Override
    public HashDbManager.HashDb.KnownFilesType getHashSetType() {
        return HashDbManager.HashDb.KnownFilesType.KNOWN;
    }

    @Override
    public void downloadDeltaHashSet() throws HashSetUpdateException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
