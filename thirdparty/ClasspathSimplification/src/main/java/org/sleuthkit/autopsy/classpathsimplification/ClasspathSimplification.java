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
package org.sleuthkit.autopsy.classpathsimplification;

import java.io.File;
import java.lang.System.Logger.Level;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Simplifies a class path from a list of jar files separated by ':' to a list
 * of directories ending of format '/dir/path/to/jars/*'
 */
public class ClasspathSimplification extends Task {

    // split on ':' but not 'C:\'
    private static Pattern CLASS_PATH_REGEX = Pattern.compile("((C:\\\\)?.+?)(:|$)");

    /**
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        System.out.println(getSimplifiedClasspath(args.length < 1 ? null : args[0]));
    }

    String originalClassPath;
    String outputprop;

    public void setClasspath(String originalClassPath) {
        this.originalClassPath = originalClassPath;
    }

    public void setOutputprop(String outputprop) {
        this.outputprop = outputprop;
    }

    @Override
    public void execute() throws BuildException {
        if (outputprop != null && !outputprop.trim().isEmpty()) {
            log("Simplifying path...");
            String simplified = getSimplifiedClasspath(originalClassPath);
            getProject().setProperty(outputprop, simplified);
        } else {
            log("No output property provided!", Level.WARNING.getSeverity());
        }
    }

    /**
     * Simplifies a class path from a list of jar files separated by ':' to a
     * list of directories ending of format '/dir/path/to/jars/*'
     *
     * @param origPath The original path with jar file paths separated by ':'
     * @return The parent folders ending with '*' separated by ':'.
     */
    public static String getSimplifiedClasspath(String origPath) {
        Set<String> directories = new HashSet<>();
        if (origPath == null) {
            return "";
        }

        Matcher pathMatch = CLASS_PATH_REGEX.matcher(origPath);
        while (pathMatch.find()) {
            String thisPath = pathMatch.group(1).trim();
            if (thisPath.toLowerCase().endsWith(".jar")) {
                directories.add(Paths.get(thisPath).getParent().toAbsolutePath().toString());
            }
        }

        return directories.stream()
                .sorted((a, b) -> a.compareToIgnoreCase(b))
                .map(path -> path.endsWith(File.separator) ? path + "*" : path + File.separator + "*")
                .collect(Collectors.joining(":"));
    }
}
