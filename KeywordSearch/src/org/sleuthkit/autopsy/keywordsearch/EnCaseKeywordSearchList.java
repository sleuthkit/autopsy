/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import org.openide.util.NbBundle;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * @author dfickling EnCaseKeywordSearchList adds support for Encase
 * tab-delimited keyword list exports to Autopsy.
 *
 * load() does the I/O operation, converting lines from the text file to an
 * unsorted list of EncaseFileEntrys The next step is to recreate the original
 * folder hierarchy, and finally the EncaseFileEntries are converted to
 * KeywordSearchLists
 *
 */
class EnCaseKeywordSearchList extends KeywordSearchList {

    ArrayList<EncaseFileEntry> entriesUnsorted;
    EncaseFileEntry rootEntry;

    public EnCaseKeywordSearchList(String encasePath) {
        super(encasePath);
    }

    /**
     * Follow the EncaseFileEntry hierarchy starting with given entry Create
     * list for each Folder entry, add keyword for each Expression
     *
     * @param entry
     * @param parentPath
     */
    private void doCreateListsFromEntries(EncaseFileEntry entry, String parentPath) {
        String name;
        if (parentPath.isEmpty()) {
            name = entry.name;
        } else {
            name = parentPath + "/" + entry.name;
        }

        List<Keyword> children = new ArrayList<>();
        for (EncaseFileEntry child : entry.children) {
            switch (child.type) {
                case Folder:
                    doCreateListsFromEntries(child, name);
                    break;
                case Expression:
                    if (child.flags.contains(EncaseFlag.pg)) { // Skip GREP keywords
                        break;
                    }
                    children.add(new Keyword(child.value, true, true));
                    break;
            }
        }
        // Give each list a unique name
        if (theLists.containsKey(name)) {
            int i = 2;
            while (theLists.containsKey(name + "(" + i + ")")) {
                i += 1;
            }
            name = name + "(" + i + ")";
        }
        // Don't create lists if there are no keywords
        if (!children.isEmpty()) {
            KeywordList newList = new KeywordList(name, new Date(), new Date(),
                    true, true, children);
            theLists.put(name, newList);
        }
    }

    /**
     * Convert entriesUnsorted (a list of childless and parentless
     * EncaseFileEntries) into an EncaseFileEntry structure
     */
    private void doCreateEntryStructure(EncaseFileEntry parent) {
        if (!parent.isFull()) {
            EncaseFileEntry child = entriesUnsorted.remove(0);
            child.hasParent = true;
            child.parent = parent;
            parent.addChild(child);
            if (!child.isFull()) {
                doCreateEntryStructure(child);
            }
            if (!parent.isFull()) {
                doCreateEntryStructure(parent);
            }
        }
        if (parent.hasParent) {
            doCreateEntryStructure(parent.parent);
        }
    }

    @Override
    public boolean save() {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "KeywordSearchListsEncase.save.exception.msg"));
    }

    @Override
    public boolean save(boolean isExport) {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "KeywordSearchListsEncase.save2.exception.msg"));
    }

    @Override
    public boolean load() {
        try {
            BufferedReader readBuffer = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "utf-16")); //NON-NLS
            String structLine;
            String metaLine;
            entriesUnsorted = new ArrayList<>();
            for (int line = 1; line < 6; line++) {
                readBuffer.readLine();
            }
            while ((structLine = readBuffer.readLine()) != null && (metaLine = readBuffer.readLine()) != null) {
                String[] structArr = structLine.split("\t");
                String[] metaArr = metaLine.split("\t");
                EncaseMetaType type = EncaseMetaType.getType(metaArr[0]);
                String childCount = structArr[1];
                String name = metaArr[1];
                String value = metaArr[2];
                ArrayList<EncaseFlag> flags = new ArrayList<>();
                for (int i = 0; i < 17; i++) {
                    if (metaArr.length < i + 4) {
                        continue;
                    }
                    if (!metaArr[i + 3].equals("")) {
                        flags.add(EncaseFlag.getFlag(i));
                    }
                }
                entriesUnsorted.add(new EncaseFileEntry(name, value, Integer.parseInt(childCount), false, null, type, flags));
            }
            if (entriesUnsorted.isEmpty()) {
                return false;
            }

            this.rootEntry = entriesUnsorted.remove(0);
            doCreateEntryStructure(this.rootEntry);
            doCreateListsFromEntries(this.rootEntry, "");
            return true;

        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.INFO, "File at " + filePath + " does not exist!", ex); //NON-NLS
        } catch (IOException ex) {
            LOGGER.log(Level.INFO, "Failed to read file at " + filePath, ex); //NON-NLS
        }
        return false;
    }

    private enum EncaseMetaType {

        Expression, Folder;

        static EncaseMetaType getType(String type) {
            if (type.equals("5")) {
                return Folder;
            } else if (type.equals("")) {
                return Expression;
            } else {
                throw new IllegalArgumentException(
                        NbBundle.getMessage(EnCaseKeywordSearchList.class,
                                "KeywordSearchListsEncase.encaseMetaType.exception.msg",
                                type));
            }
        }
    }

    /*
     * Flags for EncaseFileEntries. p8 = UTF-8 p7 = UTF-7 pg = GREP
     */
    private enum EncaseFlag {

        pc, pu, pb, p8, p7, pg, an, ph, or, di, um, st, ww, pr, lo, ta, cp;

        static EncaseFlag getFlag(int i) {
            return EncaseFlag.values()[i];
        }
    }

    /**
     * An entry in the Encase keyword list file.
     */
    private class EncaseFileEntry {

        String name;
        String value;
        int childCount;
        List<EncaseFileEntry> children;
        EncaseFileEntry parent;
        EncaseMetaType type;
        boolean hasParent;
        ArrayList<EncaseFlag> flags;

        EncaseFileEntry(String name, String value, int childCount, boolean hasParent, EncaseFileEntry parent, EncaseMetaType type, ArrayList<EncaseFlag> flags) {
            this.name = name;
            this.value = value;
            this.childCount = childCount;
            this.children = new ArrayList<>();
            this.hasParent = hasParent;
            this.parent = parent;
            this.type = type;
            this.flags = flags;
        }

        boolean isFull() {
            return children.size() == childCount;
        }

        void addChild(EncaseFileEntry child) {
            children.add(child);
        }
    }

}
