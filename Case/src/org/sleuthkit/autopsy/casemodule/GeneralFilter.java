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
import java.util.regex.Pattern;
import javax.swing.filechooser.FileFilter;

/**
 * FileFilter helper class. Matches files based on extension
 */
public class GeneralFilter extends FileFilter{

    String[] ext;
    String desc;
    boolean isMultiple; // whether the filter can accept multiple files.

    public GeneralFilter(String[] ext, String desc, boolean isMultiple){
        this.ext = ext;
        this.desc = desc;
        this.isMultiple = isMultiple;
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
        }
        else{
            Boolean result = false;
            String name = f.getName().toLowerCase();

            if(isMultiple){
                for(int i = 0; i < ext.length; i++){
                    String regex = ext[i];
                    if (Pattern.matches(regex, name)){
                        result = result || true;
                    }
                }
            }
            else{
                for(int i = 0; i < ext.length; i++){
                    if (name.endsWith(ext[i]))
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
