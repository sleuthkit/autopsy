/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.tester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.VideoUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.datamodel.Content;

@ServiceProvider(service = IngestModuleFactory.class)
public class TestFactory  implements IngestModuleFactory {

    @Override
    public String getModuleDisplayName() {
        return "Kellys Test Module";
    }

    @Override
    public String getModuleDescription() {
        return "A happy fun time.";
    }

    @Override
    public String getModuleVersionNumber() {
        return "1";
    }
    
    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }
    
    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        return new TestModule();
    }
    
    class TestModule implements DataSourceIngestModule {

        @Override
        public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
            File outputFile = new File("C:\\Temp\\output\\output.avi");
            File outputDirectory = new File("C:\\Temp\\output");
//            File inputFile = new File("C:\\Temp\\video\\nasa45.mp4");
            File inputFile = new File("C:\\Temp\\video\\cf785edc-2735-42e7-9576-8868bb9de2ee1080p.mp4");
            File strout = new File("C:\\Temp\\output\\out.txt");
            File strerr = new File("C:\\Temp\\output\\err.txt");
            
//            Path path = Paths.get(inputFile.getAbsolutePath());
//            String fileName = path.getFileName().toString();
//            if(fileName.contains(".")) {
//                fileName = fileName.substring(0, fileName.lastIndexOf('.'));
//            }
//            
//            try {
//                File tempFile = File.createTempFile(fileName, ".avi", new File("C:\\Temp\\output\\output"));
//                System.out.println(tempFile.toString());
//            } catch (IOException ex) {
//                Exceptions.printStackTrace(ex);
//            }
            
            List<File> imageList = new ArrayList<>();
            imageList.add(new File("C:\\dependancies\\opencv_new\\textDetection\\images\\sign.jpg"));
            imageList.add(new File("C:\\dependancies\\opencv_new\\textDetection\\images\\car_wash.png"));
            imageList.add(new File("C:\\dependancies\\opencv_new\\textDetection\\images\\lebron_james.jpg"));
            imageList.add(new File("C:\\Users\\kelly\\Pictures\\IMG_880.jpg"));
            
            if(outputFile.exists()) {
                outputFile.delete();
            }
            
            if(strout.exists()) {
                strout.delete();
            }
            
            if(strerr.exists()) {
                strerr.delete();
            }
            
            try {
                for(File f: imageList) {
                    if(ImageUtils.imageHasText(f)) {
                        System.out.println(f.getName() + " has text");
                    } else {
                        System.out.println(f.getName() + " does not have text");
                    }
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
                    
            
            return ProcessResult.OK;
        }
        
    }
    
}
