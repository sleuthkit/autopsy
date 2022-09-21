/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.tester;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
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
//            File imageDir = new File("C:\\DataSources\\Images");
//           
//            imageList.addAll(Arrays.asList(imageDir.listFiles()));
//            
//            imageDir = new File("C:\\dependancies\\opencv_new\\textDetection\\images");
//            imageList.addAll(Arrays.asList(imageDir.listFiles()));

            File imageDir = new File("C:\\Temp\\images");
            imageList.addAll(Arrays.asList(imageDir.listFiles()));
            
            if(outputFile.exists()) {
                outputFile.delete();
            }
            
            if(strout.exists()) {
                strout.delete();
            }
            
            if(strerr.exists()) {
                strerr.delete();
            }
            
//            for(int i = 1; i < imageList.size(); i++) {
//                long start = System.currentTimeMillis();
//                
//                boolean b = false; 
//                
//                try{    
//                    b = ImageUtils.areImagesSimilar(imageList.get(0).getAbsolutePath(), imageList.get(i).getAbsolutePath());
//                } catch (Exception ex) {
//                    System.out.println(ex.getMessage());
//                }
//                
//                long milliseconds = System.currentTimeMillis() - start;
//                if(b) {
//                    System.out.println(imageList.get(0).getName() + " matches " + imageList.get(i).getName());
//                } else {
//                    System.out.println(imageList.get(0).getName() + " does not match " + imageList.get(i).getName());
//                }
//                System.out.println("duration: " + milliseconds);
//                
//                System.out.println("\n-----------\n");
//            }
           
                    
            
            return ProcessResult.OK;
        }
        
    }
    
}
