/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.yara.rules;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a yara rule set which is a collection of yara rule files.
 */
public class RuleSet implements Comparable<RuleSet>, Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final Path path;

    /**
     * Construct a new RuleSet.
     *
     * @param name Name of the rule set.
     * @param path Directory path to the rule set.
     */
    RuleSet(String name, Path path) {
        this.name = name;
        this.path = path;
    }

    /**
     * Returns the name of the rule set.
     *
     * @return Name of rule set.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns location if the rule set files.
     *
     * @return The path for this rule set.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns a list of the rule files in this rule set.
     *
     * @return List of Files in current directory.
     */
    public List<File> getRuleFiles() {
        List<File> fileList = new ArrayList<>();
        if(path.toFile().exists()) {
            File[] fileArray = path.toFile().listFiles();
            if(fileArray != null) {
                fileList.addAll(Arrays.asList(fileArray));
            }
        }
        
        return fileList;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public int compareTo(RuleSet ruleSet) {
        return getName().compareTo(ruleSet.getName());
    }
}
