/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.report.video;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;

public class VideoUtilis {
    
    private static final String FFMPEG = "ffmpeg";
    private static final String FFMPEG_EXE = "ffmpeg.exe";
    
    private VideoUtilis() {}
    
    static void compressVideo(Path inputPath, Path outputPath, ProcessTerminator terminator) throws Exception {
        Path executablePath = Paths.get(FFMPEG, FFMPEG_EXE);
        File exeFile = InstalledFileLocator.getDefault().locate(executablePath.toString(), VideoUtilis.class.getPackage().getName(), true);
        if(exeFile == null) {
            throw new IOException("Unable to compress ffmpeg.exe was not found.");
        } 
        
        if(!exeFile.canExecute()) {
            throw new IOException("Unable to compress ffmpeg.exe could not be execute");
        }
        
                
        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + exeFile.getAbsolutePath() + "\"",
                "-i", "\"" + inputPath.toAbsolutePath().toString() + "\"",
                "-vf", "scale=1280:-1",
                "-c:v", "libx264",
                "-preset", "veryslow",
                "-crf", "24",
                "\"" + outputPath.toAbsolutePath().toString() + "\"");
        
        ExecUtil.execute(processBuilder, terminator);
    }
    
    static void scaleVideo(Path inputPath, Path outputPath, int width, int height, ProcessTerminator terminator) throws Exception{
        Path executablePath = Paths.get(FFMPEG, FFMPEG_EXE);
        File exeFile = InstalledFileLocator.getDefault().locate(executablePath.toString(), VideoUtilis.class.getPackage().getName(), true);
        if(exeFile == null) {
            throw new IOException("Unable to compress ffmpeg.exe was not found.");
        } 
        
        if(!exeFile.canExecute()) {
            throw new IOException("Unable to compress ffmpeg.exe could not be execute");
        }
        
        String scaleParam = Integer.toString(width) + ":" + Integer.toString(height);
        
        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + exeFile.getAbsolutePath() + "\"",
                "-i", "\"" + inputPath.toAbsolutePath().toString() + "\"",
                "-s", scaleParam,
                "-c:a", "copy",
                "\"" + outputPath.toAbsolutePath().toString() + "\"");
        
        ExecUtil.execute(processBuilder, terminator);

    }
    
    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force aLeapp to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        return processBuilder;
    }
}
