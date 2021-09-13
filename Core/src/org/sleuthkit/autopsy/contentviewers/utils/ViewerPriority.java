/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.utils;

/**
 * A utility class that defines the content viewers priority
 *
 */
public final class ViewerPriority {

    private ViewerPriority() {
        //private constructor for utility class
    }

/**
 * The following are some examples of the current levels in use. If the
 * selected node is an artifact, the level may be determined by both the
 * artifact and its associated file.
 * 
 * The details of these levels is repeated in DataContentViewer. Any changes here
 * need to be reflected in DataContentViewer.
 * 
 * Level 8 - Used for viewers that summarize a data artifact and display a 
 * relevant subset to help the examiner decide if they should look into it 
 * further. Not currently used by any modules, but an example would be a 
 * module that summarizes an email message.
 *
 * Level 7 - Used for data artifact viewers. These have higher priority over
 * file content viewers because a Node will likely have the ‘source’ file for a
 * data artifact and we want to give the artifact priority. Currently used by
 * the Data Artifacts viewer.
 * 
 * Level 6 - Used for type-specific file content viewers that summarize the file
 * content and display a relevant subset. These viewers help the examiner 
 * determine if the file is worth looking into further. Examples of this would
 * be Video Triage Module that displays a subset of a video or a document.
 *
 * Level 5 - Used for type-specific file content viewers that are optimized for
 * that type, such as displaying an image or a PDF file with images and proper
 * layout. Currently used by the Application viewer.
 *
 * Level 4 - Used for type-specific file content viewers that are not optimized.
 * For example, displaying only the plain text from a PDF would be at this
 * level, but displaying the same PDF with images and layout would be level 5.
 * Currently used by the Text viewer that returns text from Solr.
 *
 * Level 3 - Used for viewing Data Artifacts that refer to files and the user
 * may want to view the files more than the artifact itself. This is currently
 * used by the Data Artifact viewer when a Web Download artifact is selected.
 *
 * Level 2 - Used for viewing Analysis Results. This is a lower priority than
 * Data Artifacts and file content because Analysis Results are used to identify
 * content of interest and therefore the content itself should be shown.
 * Currently used by the Analysis Results viewer.  *
 * Level 1 - Used for metadata viewers that give more information and context
 * about the primary file or artifact. Currently used by Metadata, Annotations,
 * Context, Other Occurrences, and OS Account.
 *
 * Level 0 - Used for general purpose file content viewers that are not file
 * specific and will always be enabled. Currently used by Text/Strings and Hex.
 */
    public enum viewerPriority {
        LevelZero(0),
        LevelOne(1),
        LevelTwo(2),
        LevelThree(3),
        LevelFour(4),
        LevelFive(5),
        LevelSix(6),
        LevelSeven(7),
        Level(8);

    private final int flag;

    private viewerPriority(int flag) {
        this.flag = flag;
    }

    public int getFlag() {
        return flag;
    }
    
    }
}
