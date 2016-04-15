package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    public static String BASE_URL = "http://virusshare.com/hashes/";
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
    public void download() {
        URL url;
        List<URL> urls = new LinkedList<>();
        try {
            url = new URL(BASE_URL);
            InputStream x = url.openConnection().getInputStream();
            StringBuilder y = new StringBuilder();
            int f;
            while ((f = x.read()) != -1) {
                y.append((char) f);
            }
            urls = getLinks(y.toString());
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        createTargetDircectoryIfNotExists(getDirectory());
        progressbar.setMaximum(urls.size());
        int counter = 0;

        for (URL u : urls) {
            try {
                FileUtils.copyURLToFile(u, new File(getDirectory() + getFileNameFromURL(u.toString())));
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            progressbar.setValue(counter++);
        }

    }

    @Override
    public void extract() {
        return;
    }

    @Override
    public void index() {
        return;
    }

    @Override
    public boolean newVersionAvailable() {
        return true;
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

}
