package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JProgressBar;
import org.openide.util.lookup.ServiceProvider;
import org.python.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.python.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.python.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

@ServiceProvider(service = HashSetPreparer.class)
public class ClamavHashSetPreparer extends HashSetPreparer {

    private static final String[] SOURCES = {"http://database.clamav.net/main.cvd", "http://database.clamav.net/daily.cvd", "http://database.clamav.net/bytecode.cvd"};
    private List<String> downloadedFiles = new LinkedList<>();
    private String outputDirectory;
    private JProgressBar progressbar;

    public ClamavHashSetPreparer() {
        super("");
    }

    private ClamavHashSetPreparer(JProgressBar progressbar, String outputDirectory) {
        super(outputDirectory);
        this.progressbar = progressbar;
    }

    @Override
    public String getName() {
        return "ClamAV";
    }

    @Override
    public HashSetPreparer createInstance(JProgressBar progressbar, String outputDirectory) {
        return new ClamavHashSetPreparer(progressbar, outputDirectory);
    }

    @Override
    public void download() throws HashSetUpdateException {
        createTargetDircectoryIfNotExists(getDirectory());
        for (String url : SOURCES) {
            String fileName = getFileNameFromURL(url);
            try {
                String targetFile = getDirectory() + fileName;
                downloadFile(url, targetFile, progressbar, 512);
                downloadedFiles.add(targetFile);

            } catch (Exception e) {
                System.out.println("org.sleuthkit.autopsy.modules.hashdatabase.ClamavHashSetPreparer.download()");
            }
        }
    }

    @Override
    public void extract() throws HashSetUpdateException {
        for (String fileName : downloadedFiles) {
            try {
                TarArchiveInputStream a = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(fileName)));
                TarArchiveEntry x;
                while ((x = a.getNextTarEntry()) != null) {
                    if (x.getName().substring(x.getName().lastIndexOf('.')+1).equals("mdb")) {
                        OutputStream o = new FileOutputStream(new File(getDirectory() + x.getName()));
                        copyStreams(a, o);
                    }

                }
            } catch (Exception ex) {
                throw new HashSetUpdateException("YEY");
            }
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
