/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel.eventtype;

import com.google.common.net.InternetDomainName;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public enum WebTypes implements EventType, ArtifactEventType {

    WEB_DOWNLOADS(NbBundle.getMessage(WebTypes.class, "WebTypes.webDownloads.name"),
            "downloads.png", // NON-NLS
            new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD),
            new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED),
            TopPrivateDomainExtractor.getInstance(),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH)),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL))) {

                @Override
        public AttributeEventDescription parseAttributesHelper(BlackboardArtifact artf) throws TskCoreException {
            long time = artf.getAttribute(getDateTimeAttributeType()).getValueLong();
                    String domain = getShortExtractor().apply(artf);
                    String path = getMedExtractor().apply(artf);
                    String fileName = StringUtils.substringAfterLast(path, "/");
                    String url = getFullExtractor().apply(artf);

                    //TODO: review non default description construction
                    String shortDescription = fileName + " from " + domain; // NON-NLS
                    String medDescription = fileName + " from " + url; // NON-NLS
                    String fullDescription = path + " from " + url; // NON-NLS
                    return new AttributeEventDescription(time, shortDescription, medDescription, fullDescription);
                }
            },
    //TODO: review description separators
    WEB_COOKIE(NbBundle.getMessage(WebTypes.class, "WebTypes.webCookies.name"),
            "cookies.png", // NON-NLS
            new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE),
            new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME),
            TopPrivateDomainExtractor.getInstance(),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE))),
    //TODO: review description separators
    WEB_BOOKMARK(NbBundle.getMessage(WebTypes.class, "WebTypes.webBookmarks.name"),
            "bookmarks.png", // NON-NLS
            new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK),
            new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED),
            TopPrivateDomainExtractor.getInstance(),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE))),
    //TODO: review description separators
    WEB_HISTORY(NbBundle.getMessage(WebTypes.class, "WebTypes.webHistory.name"),
            "history.png", // NON-NLS
            new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY),
            new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED),
            TopPrivateDomainExtractor.getInstance(),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE))),
    //TODO: review description separators
    WEB_SEARCH(NbBundle.getMessage(WebTypes.class, "WebTypes.webSearch.name"),
            "searchquery.png", // NON-NLS
            new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY),
            new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT)),
            TopPrivateDomainExtractor.getInstance(),
            new AttributeExtractor(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

    private final BlackboardAttribute.Type dateTimeAttributeType;

    private final String iconBase;

    private final Image image;

    @Override
    public Image getFXImage() {
        return image;
    }

    @Override
    public BlackboardAttribute.Type getDateTimeAttributeType() {
        return dateTimeAttributeType;
    }

    @Override
    public EventTypeZoomLevel getZoomLevel() {
        return EventTypeZoomLevel.SUB_TYPE;
    }

    private final Function<BlackboardArtifact, String> longExtractor;

    private final Function<BlackboardArtifact, String> medExtractor;

    private final Function<BlackboardArtifact, String> shortExtractor;

    @Override
    public Function<BlackboardArtifact, String> getFullExtractor() {
        return longExtractor;
    }

    @Override
    public Function<BlackboardArtifact, String> getMedExtractor() {
        return medExtractor;
    }

    @Override
    public Function<BlackboardArtifact, String> getShortExtractor() {
        return shortExtractor;
    }

    private final String displayName;

    private final BlackboardArtifact.Type artifactType;

    @Override
    public String getIconBase() {
        return iconBase;
    }

    @Override
    public BlackboardArtifact.Type getArtifactType() {
        return artifactType;
    }

    private WebTypes(String displayName, String iconBase, BlackboardArtifact.Type artifactType,
            BlackboardAttribute.Type dateTimeAttributeType,
            Function<BlackboardArtifact, String> shortExtractor,
            Function<BlackboardArtifact, String> medExtractor,
            Function<BlackboardArtifact, String> longExtractor) {
        this.displayName = displayName;
        this.iconBase = iconBase;
        this.artifactType = artifactType;
        this.dateTimeAttributeType = dateTimeAttributeType;
        this.shortExtractor = shortExtractor;
        this.medExtractor = medExtractor;
        this.longExtractor = longExtractor;
        this.image = new Image("org/sleuthkit/autopsy/timeline/images/" + iconBase, true); // NON-NLS
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

    private static class TopPrivateDomainExtractor extends AttributeExtractor {

        final private static TopPrivateDomainExtractor instance = new TopPrivateDomainExtractor();

        static TopPrivateDomainExtractor getInstance() {
            return instance;
        }

        @Override
        public String apply(BlackboardArtifact artf) {
            String domainString = StringUtils.substringBefore(super.apply(artf), "/");
            if (InternetDomainName.isValid(domainString)) {
                InternetDomainName domain = InternetDomainName.from(domainString);
                return (domain.isUnderPublicSuffix())
                        ? domain.topPrivateDomain().toString()
                        : domain.toString();
            } else {
                return domainString;
            }
        }

        TopPrivateDomainExtractor() {
            super(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN));
        }
    }
}
