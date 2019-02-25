package org.sleuthkit.datamodel;

public class PublicTagName extends TagName {

    public PublicTagName(long id, String displayName, String description, HTML_COLOR color, TskData.FileKnown knownStatus) {
        super(id, displayName, description, color, knownStatus);
    }
}
