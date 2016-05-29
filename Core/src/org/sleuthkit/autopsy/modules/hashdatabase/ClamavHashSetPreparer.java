package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JProgressBar;
import org.openide.util.lookup.ServiceProvider;
import org.python.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.python.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.python.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

@ServiceProvider(service = HashSetPreparer.class)
public class ClamavHashSetPreparer extends HashSetPreparer {

    private static final String OUTPUT_FILE_NAME = "ClamAV-combined.md5";

    private static final String[] SOURCES = {"http://database.clamav.net/main.cvd", "http://database.clamav.net/daily.cvd", "http://database.clamav.net/bytecode.cvd"};
    private List<String> downloadedFiles = new LinkedList<>();
    private List<String> extractedFiles = new LinkedList<>();
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
    public void downloadFullHashSet() throws HashSetUpdateException {
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
        extractMdbFilesFromDownloadedArchives();
        combineMdbFiles();
    }

    private void extractMdbFilesFromDownloadedArchives() throws HashSetUpdateException {
        for (String fileName : downloadedFiles) {
            try {
                TarArchiveInputStream tarArchiveStream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(fileName)));
                TarArchiveEntry tarArchiveEntry;
                while ((tarArchiveEntry = tarArchiveStream.getNextTarEntry()) != null) {
                    if (tarArchiveEntry.getName().substring(tarArchiveEntry.getName().lastIndexOf('.') + 1).equals("mdb")) {
                        String extractedFileName = getDirectory() + tarArchiveEntry.getName();
                        OutputStream extractedFile = new FileOutputStream(new File(extractedFileName));
                        copyStreams(tarArchiveStream, extractedFile);
                        extractedFiles.add(extractedFileName);
                    }

                }
            } catch (Exception ex) {
                throw new HashSetUpdateException("Error while extracting ClamAV HashSets");
            }
        }
    }

    private void combineMdbFiles() throws HashSetUpdateException {
        progressbar.setMaximum(extractedFiles.size());
        int counter = 0;

        try {
            PrintWriter targetFile = new PrintWriter(new File(getDirectory() + OUTPUT_FILE_NAME));

            for (String fileName : extractedFiles) {
                writeValidMd5SumsToTargetFile(fileName, targetFile);
                updateProgressBar(counter++, progressbar);
            }

        } catch (IOException ex) {
            throw new HashSetUpdateException("Error while combining ClamAV parts");
        }
        extractedFile = getDirectory() + OUTPUT_FILE_NAME;
    }

    private void writeValidMd5SumsToTargetFile(String fileName, PrintWriter targetFile) throws IOException {
        int md5lenth = 32;
        BufferedReader inputStream;
        inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName))));
        String line;
        while ((line = inputStream.readLine()) != null) {
            if (line.matches("[0-9]*:[a-f0-9]{32}:(.*)")) {
                int md5start = line.indexOf(':') + 1;
                targetFile.println(line.substring(md5start, md5start + md5lenth));
            }
        }
    }

    @Override
    public HashDbManager.HashDb.KnownFilesType getHashSetType() {
        return HashDbManager.HashDb.KnownFilesType.KNOWN_BAD;
    }

    @Override
    public void downloadDeltaHashSet() throws HashSetUpdateException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
