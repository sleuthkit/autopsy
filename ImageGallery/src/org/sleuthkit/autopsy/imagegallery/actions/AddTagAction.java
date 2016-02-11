/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.util.Set;
import org.sleuthkit.datamodel.TagName;

/**
 * An abstract base class for actions that allow users to tag SleuthKit data
 * model objects.
 *
 * //TODO: this class started as a cut and paste from
 * org.sleuthkit.autopsy.actions.AddTagAction and needs to be refactored or
 * reintegrated to the AddTagAction hierarchy of Autopysy.
 */
@Deprecated
abstract class AddTagAction {

   

    /**
     * Template method to allow derived classes to provide a string for a menu
     * item label.
     */
    abstract protected String getActionDisplayName();

    /**
     * Template method to allow derived classes to add the indicated tag and
     * comment to one or more a SleuthKit data model objects.
     */
    abstract protected void addTag(TagName tagName, String comment);

    /**
     * Template method to allow derived classes to add the indicated tag and
     * comment to a list of one or more file IDs.
     */
    abstract protected void addTagsToFiles(TagName tagName, String comment, Set<Long> selectedFiles);

  
    /**
     * @return the Window containing the ImageGalleryTopComponent
     */
  
}
