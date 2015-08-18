/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.filters;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.events.TimeLineEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Filter for an individual TagName
 */
public class TagNameFilter extends AbstractFilter {

    private final TagName tagName;
    private final Case autoCase;
    private final TagsManager tagsManager;
    private final SleuthkitCase sleuthkitCase;

    public TagNameFilter(TagName tagName, Case autoCase) {
        this.autoCase = autoCase;
        sleuthkitCase = autoCase.getSleuthkitCase();
        tagsManager = autoCase.getServices().getTagsManager();
        this.tagName = tagName;
        setSelected(Boolean.TRUE);
    }

    public TagName getTagName() {
        return tagName;
    }

    @Override
    synchronized public TagNameFilter copyOf() {
        TagNameFilter filterCopy = new TagNameFilter(getTagName(), autoCase);
        filterCopy.setSelected(isSelected());
        filterCopy.setDisabled(isDisabled());
        return filterCopy;
    }

    @Override
    public String getDisplayName() {
        return tagName.getDisplayName();
    }

    @Override
    public String getHTMLReportString() {
        return getDisplayName() + getStringCheckBox();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.tagName);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TagNameFilter other = (TagNameFilter) obj;
        if (!Objects.equals(this.tagName, other.tagName)) {
            return false;
        }

        return isSelected() == other.isSelected();
    }

    @Override
    public boolean test(TimeLineEvent t) {
        try {
            AbstractFile abstractFileById = sleuthkitCase.getAbstractFileById(t.getFileID());
            List<ContentTag> contentTagsByContent = tagsManager.getContentTagsByContent(abstractFileById);
            boolean tagged = contentTagsByContent.stream()
                    .map(Tag::getName).anyMatch(tagName::equals);

//            System.out.println(t.toString() + (tagged ? " passed " : " failed ") + getDisplayName() + " content filter ");
            if (Objects.nonNull(t.getArtifactID())) {
                BlackboardArtifact blackboardArtifact = sleuthkitCase.getBlackboardArtifact(t.getArtifactID());
                List<BlackboardArtifactTag> blackboardArtifactTagsByArtifact = tagsManager.getBlackboardArtifactTagsByArtifact(blackboardArtifact);
                boolean blackboardTagged = blackboardArtifactTagsByArtifact.stream()
                        .map(Tag::getName).anyMatch(tagName::equals);
//                System.out.println(t.toString() + (blackboardTagged ? " passed " : " failed ") + getDisplayName() + "artifact filter ");
                tagged |= blackboardTagged;
            }

//            System.out.println(t.toString() + (tagged ? " passed " : " failed ") + getDisplayName() + "  filter ");
            return tagged;
        } catch (TskCoreException ex) {
            Logger.getLogger(TagNameFilter.class.getName()).log(Level.SEVERE, "Failed to get tags for event", ex);
            return true; //show the file if there is an error
        }
    }
}
