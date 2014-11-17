/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.events.type;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 */
public enum WebTypes implements EventType, ArtifactEventType {

    WEB_DOWNLOADS("Web Downloads",
                  "downloads.png",
                  BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD,
                  BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                  new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN),
                  new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH),
                  new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)) {

                /** Override
                 * {@link ArtifactEventType#parseAttributesHelper(org.sleuthkit.datamodel.BlackboardArtifact, java.util.Map)}
                 * with non default description construction */
                @Override
                public AttributeEventDescription parseAttributesHelper(BlackboardArtifact artf, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attrMap) {
                    long time = attrMap.get(getDateTimeAttrubuteType()).getValueLong();
                    String domain = getShortExtractor().apply(artf, attrMap);
                    String path = getMedExtractor().apply(artf, attrMap);
                    String fileName = StringUtils.substringAfterLast(path, "/");
                    String url = getFullExtractor().apply(artf, attrMap);

                    //TODO: review non default descritpion construction 
                    String shortDescription = fileName + " from " + domain;
                    String medDescription = fileName + " from " + url;
                    String fullDescription = path + " from " + url;
                    return new AttributeEventDescription(time, shortDescription, medDescription, fullDescription);
                }
            },
    //TODO: review description seperators
    WEB_COOKIE("Web Cookies",
               "cookies.png",
               BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE,
               BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
               new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN),
               new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME),
               new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE)),
    //TODO: review description seperators
    WEB_BOOKMARK("Web Bookmarks",
                 "bookmarks.png",
                 BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK,
                 BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                 new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN),
                 new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL),
                 new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE)),
    //TODO: review description seperators
    WEB_HISTORY("Web History",
                "history.png",
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY,
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN),
                new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL),
                new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE)),
    //TODO: review description seperators
    WEB_SEARCH("Web Searches",
               "searchquery.png",
               BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY,
               BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
               new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT),
               new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN),
               new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME));

    private final BlackboardAttribute.ATTRIBUTE_TYPE dateTimeAttributeType;

    private final String iconBase;

    private final Image image;

    @Override
    public Image getFXImage() {
        return image;
    }

    @Override
    public BlackboardAttribute.ATTRIBUTE_TYPE getDateTimeAttrubuteType() {
        return dateTimeAttributeType;
    }

    @Override
    public EventTypeZoomLevel getZoomLevel() {
        return EventTypeZoomLevel.SUB_TYPE;
    }

    private final BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> longExtractor;

    private final BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> medExtractor;

    private final BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> shortExtractor;

    @Override
    public BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> getFullExtractor() {
        return longExtractor;
    }

    @Override
    public BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> getMedExtractor() {
        return medExtractor;
    }

    @Override
    public BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> getShortExtractor() {
        return shortExtractor;
    }

    private final String displayName;

    BlackboardArtifact.ARTIFACT_TYPE artifactType;

    @Override
    public String getIconBase() {
        return iconBase;
    }

    @Override
    public BlackboardArtifact.ARTIFACT_TYPE getArtifactType() {
        return artifactType;
    }

    private WebTypes(String displayName, String iconBase, BlackboardArtifact.ARTIFACT_TYPE artifactType,
                     BlackboardAttribute.ATTRIBUTE_TYPE dateTimeAttributeType,
                     BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> shortExtractor,
                     BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> medExtractor,
                     BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> longExtractor) {
        this.displayName = displayName;
        this.iconBase = iconBase;
        this.artifactType = artifactType;
        this.dateTimeAttributeType = dateTimeAttributeType;
        this.shortExtractor = shortExtractor;
        this.medExtractor = medExtractor;
        this.longExtractor = longExtractor;
        this.image = new Image("org/sleuthkit/autopsy/timeline/images/" + iconBase, true);
    }

    @Override
    public EventType getSuperType() {
        return BaseTypes.WEB_ACTIVITY;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public EventType getSubType(String string) {
        return WebTypes.valueOf(string);
    }

    @Override
    public List<? extends EventType> getSubTypes() {
        return Collections.emptyList();
    }

}
