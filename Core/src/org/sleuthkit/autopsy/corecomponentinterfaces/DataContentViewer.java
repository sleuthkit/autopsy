/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.Component;
import org.openide.nodes.Node;

/**
 * Interface that DataContentViewer modules must implement. These modules
 * analyze an individual file that the user has selected and display results in
 * some form of JPanel. We find it easiest to use the NetBeans IDE to first make
 * a "JPanel Form" class and then have it implement DataContentViewer. This
 * allows you to easily use the UI builder for the layout.
 *
 * DataContentViewer panels should handle their own vertical scrolling, the
 * horizontal scrolling when under their panel's preferred size will be handled
 * by the DataContentPanel which contains them.
 */
public interface DataContentViewer {

    /**
     * Autopsy will call this when this panel is focused with the file that
     * should be analyzed. When called with null, must clear all references to
     * previous nodes.
     *
     * @param selectedNode the node which is used to determine what is displayed
     *                     in this viewer
     */
    public void setNode(Node selectedNode);

    /**
     * Returns the title of this viewer to display in the tab.
     *
     * @return the title of DataContentViewer
     *
     */
    public String getTitle();
    
    /**
     * Returns the title of this viewer to display in the tab.
     *
     * @param node The node to be viewed in the DataContentViewer.
     * @return the title of DataContentViewer.
     */
    public default String getTitle(Node node) {
        return getTitle();
    }

    /**
     * Returns a short description of this viewer to use as a tool tip for its
     * tab.
     *
     * @return the tooltip for this TextViewer
     */
    public String getToolTip();

    /**
     * Create and return a new instance of your viewer. The reason that this is
     * needed is because the specific viewer modules will be found via NetBeans
     * Lookup and the type will only be DataContentViewer. This method is used
     * to get an instance of your specific type.
     *
     * @return A new instance of the viewer
     */
    public DataContentViewer createInstance();

    /**
     * Return the Swing Component to display. Implementations of this method
     * that extend JPanel and do a 'return this;'. Otherwise return an internal
     * instance of the JPanel.
     *
     * @return the component which is displayed for this viewer
     */
    public Component getComponent();

    /**
     * Resets the contents of the viewer / component.
     */
    public void resetComponent();

    /**
     * Checks whether the given node is supported by the viewer. This will be
     * used to enable or disable the tab for the viewer.
     *
     * @param node Node to check for support
     *
     * @return True if the node can be displayed / processed, else false
     */
    public boolean isSupported(Node node);

    /**
     * Checks whether the given viewer is preferred for the Node. This is a bit
     * subjective, but the idea is that Autopsy wants to display the most
     * relevant tab. The more generic the viewer, the lower the return value
     * should be. This will only be called on viewers that support the given
     * node (i.e., isSupported() has already returned true).
     *
     * The following are some examples of the current levels in use. If the
     * selected node is an artifact, the level may be determined by both the
     * artifact and its associated file.
     *
     * Level 8 - Used for viewers that summarize a data artifact and display a 
     * relevant subset to help the examiner decide if they should look into it
     * further. Not currently used by any modules, but an example would be a
     * module that summarizes an email message.
     * 
     * Level 7 - Used for data artifact viewers. These have higher priority over
     * file content viewers because a Node will likely have the ‘source’ file
     * for a data artifact and we want to give the artifact priority. Currently
     * used by the Data Artifacts viewer.
     * 
     * Level 6 - Used for type-specific
     * file content viewers that summarize the file content and display a
     * relevant subset. These viewers help the examiner determine if the file is
     * worth looking into further. Examples of this would be Video Triage Module
     * that displays a subset of a video or a document.
     *
     * Level 5 - Used for type-specific file content viewers that are optimized
     * for that type, such as displaying an image or a PDF file with images and
     * proper layout. Currently used by the Application viewer.
     *
     * Level 4 - Used for type-specific file content viewers that are not
     * optimized. For example, displaying only the plain text from a PDF would
     * be at this level, but displaying the same PDF with images and layout
     * would be level 5. Currently used by the Text viewer that returns text
     * from Solr.
     *
     * Level 3 - Used for viewing Data Artifacts that refer to files and the
     * user may want to view the files more than the artifact itself. This is
     * currently used by the Data Artifact viewer when a Web Download artifact
     * is selected.
     *
     * Level 2 - Used for viewing Analysis Results. This is a lower priority
     * than Data Artifacts and file content because Analysis Results are used to
     * identify content of interest and therefore the content itself should be
     * shown. Currently used by the Analysis Results viewer.      *
     * Level 1 - Used for metadata viewers that give more information and
     * context about the primary file or artifact. Currently used by Metadata,
     * Annotations, Context, Other Occurrences, and OS Account.
     *
     * Level 0 - Used for general purpose file content viewers that are not file
     * specific and will always be enabled. Currently used by Text/Strings and
     * Hex.
     *
     * @param node Node to check for preference
     *
     * @return an int (0-10) higher return means the viewer has higher priority
     */
    public int isPreferred(Node node);
}
