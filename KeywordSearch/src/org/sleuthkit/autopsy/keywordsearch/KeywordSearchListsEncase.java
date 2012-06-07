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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;

/**
 *
 * @author dfickling
 */
public class KeywordSearchListsEncase extends KeywordSearchListsAbstract{
    
    public KeywordSearchListsEncase(String encasePath) {
        super(encasePath);
    }

    @Override
    public boolean save() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean load() {
        try {
            File theFile = new File(filePath);
            BufferedReader readBuffer = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "utf-16"));
            String line;
            // If any terms are grep terms, we'll show the user a dialog at the end.
            boolean anyGrep = false;
            List<Keyword> words = new ArrayList<Keyword>();
            while ((line = readBuffer.readLine()) != null) {
                String[] tabDelim = line.split("\t");
                if (tabDelim.length > 2) {
                    String expr = tabDelim[2];
                    if (tabDelim.length > 8) {
                        boolean literal = tabDelim[8].isEmpty() || !tabDelim[8].equals("1");
                        anyGrep = anyGrep || !literal;
                        //TODO: Stop skipping non-literal search terms
                        if (!expr.isEmpty() && !expr.equals("t") && literal) {
                            words.add(new Keyword(expr, literal));
                        }
                    }
                }
            }
            theLists.put(theFile.getName(), 
                    new KeywordSearchList(theFile.getName(),
                            new Date(theFile.lastModified()),
                            new Date(theFile.lastModified()),
                            true, true, words));
            if(anyGrep) {
                JOptionPane.showMessageDialog(null, "Importing grep (regular expression) keywords is not currently supported. Any that were in the list "
                                                    + theFile.getName() + " have not been imported.");
            }
            
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.INFO, "File at " + filePath + " does not exist!", ex);
        } catch (IOException ex) {
            logger.log(Level.INFO, "Failed to read file at " + filePath, ex);
        }
        return false;
    }
    
}
