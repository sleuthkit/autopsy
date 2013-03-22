/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.util.List;
import javax.swing.filechooser.FileFilter;

/**
 * FileFilter helper class. Matches files based on extension
 */
public class GeneralFilter extends FileFilter{

    private List<String> extensions;
    private String desc;

    public GeneralFilter(List<String> ext, String desc){
        super();
        this.extensions = ext;
        this.desc = desc;
    }

    /**
     * Checks whether the given file is accepted by this filter.
     *
     * @param f         the given file
     * @return boolean  return true if accepted, false otherwise
     */
    @Override
    public boolean accept(File f) {
        if(f.isDirectory()){
            return true;
        } else {
            Boolean result = false;
            String name = f.getName().toLowerCase();

            for (String ext : extensions) {
                if (name.endsWith(ext)) {
                    result = result || true;
                }
            }
            return result;
        }
    }

    /**
     * Returns the description of this file filter
     *
     * @return desc  return the description
     */
    @Override
    public String getDescription() {
        return desc;
    }

}
