package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JProgressBar;
import org.apache.commons.io.FileUtils;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = HashSetPreparer.class)

public class VirusShareHashSetPreparer extends HashSetPreparer {

    public static final String BASE_URL = "http://virusshare.com/hashes/";
    public static final String OUTPUT_FILE_NAME = "VirusShare_combined.md5";

    List<String> downloadedFiles;

    private JProgressBar progressbar;

    public VirusShareHashSetPreparer() {
        super("");
    }

    private VirusShareHashSetPreparer(JProgressBar progressbar, String outputDirectory) {
        super(outputDirectory);
        this.progressbar = progressbar;

    }

    @Override
    public String getName() {
        return "VirusShare";
    }

    @Override
    public HashSetPreparer createInstance(JProgressBar progressbar, String outputDirectory) {
        return new VirusShareHashSetPreparer(progressbar, outputDirectory);
    }

    @Override
    public void downloadFullHashSet() throws HashSetUpdateException {
        List<URL> urls = getAllPartUrls(BASE_URL);
        createTargetDircectoryIfNotExists(getDirectory());

        downloadedFiles = new LinkedList<>();
        progressbar.setMaximum(urls.size());
        int counter = 0;

        for (URL url : urls) {
            try {
                String fileName = getDirectory() + getFileNameFromURL(url.toString());
                FileUtils.copyURLToFile(url, new File(fileName));
                downloadedFiles.add(fileName);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            updateProgressBar(counter++, progressbar);
        }

    }

    private List<URL> getAllPartUrls(String baseURL) throws HashSetUpdateException {
        URL url;
        List<URL> urls = new LinkedList<>();
        try {
            url = new URL(baseURL);
            InputStream inputstream = url.openConnection().getInputStream();
            StringBuilder baseWebsiteContent = new StringBuilder();
            int letter;
            while ((letter = inputstream.read()) != -1) {
                baseWebsiteContent.append((char) letter);
            }
            urls = getLinks(baseWebsiteContent.toString());
        } catch (IOException ex) {
            throw new HashSetUpdateException("Error while downloading Virusshare Parts");
        }
        return urls;
    }

    @Override
    public void extract() throws HashSetUpdateException {
        progressbar.setMaximum(downloadedFiles.size());
        int counter = 0;

        try {
            PrintWriter targetFile = new PrintWriter(new File(getDirectory() + OUTPUT_FILE_NAME));

            for (String fileName : downloadedFiles) {
                writeValidMd5SumsToTargetFile(fileName, targetFile);
                updateProgressBar(counter++, progressbar);
            }

        } catch (IOException ex) {
            throw new HashSetUpdateException("Error while combining Virusshare parts");
        }
        extractedFile = getDirectory() + OUTPUT_FILE_NAME;
    }

    private void writeValidMd5SumsToTargetFile(String fileName, PrintWriter targetFile) throws IOException, FileNotFoundException {
        BufferedReader inputStream;
        inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName))));
        String line;
        while ((line = inputStream.readLine()) != null) {
            if (line.matches("[a-f0-9]{32}")) {
                targetFile.println(line);
            }
        }
    }

    String reg = "<li><a href=\"VirusShare_[0-9]{5}.md5\"> VirusShare_[0-9]{5}.md5</a></li>";

    private List<URL> getLinks(String content) throws MalformedURLException {
        List<URL> result = new LinkedList<>();
        for (String line : content.split("\n")) {
            if (line.matches(reg)) {
                String url = BASE_URL + line.substring(13, 33);
                result.add(new URL(url));
            }
        }
        return result;
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
