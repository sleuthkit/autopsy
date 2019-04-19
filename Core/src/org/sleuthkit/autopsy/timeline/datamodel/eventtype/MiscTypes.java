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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import static org.sleuthkit.autopsy.timeline.datamodel.eventtype.ArtifactEventType.getAttributeSafe;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public enum MiscTypes implements EventType, ArtifactEventType {

    MESSAGE(NbBundle.getMessage(MiscTypes.class, "MiscTypes.message.name"), "message.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_MESSAGE),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE)),
            artf -> {
                final BlackboardAttribute dir = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DIRECTION));
                final BlackboardAttribute readStatus = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_READ_STATUS));
                final BlackboardAttribute name = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME));
                final BlackboardAttribute phoneNumber = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER));
                final BlackboardAttribute subject = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SUBJECT));
                List<String> asList = Arrays.asList(stringValueOf(dir), stringValueOf(readStatus), name != null || phoneNumber != null ? toFrom(dir) : "", stringValueOf(name != null ? name : phoneNumber), (subject == null ? "" : stringValueOf(subject)));
                return StringUtils.join(asList, " ");
            },
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_TEXT))),
    GPS_ROUTE(NbBundle.getMessage(MiscTypes.class, "MiscTypes.GPSRoutes.name"), "gps-search.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_GPS_ROUTE),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_LOCATION)),
            artf -> {
                final BlackboardAttribute latStart = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START));
                final BlackboardAttribute longStart = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START));
                final BlackboardAttribute latEnd = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END));
                final BlackboardAttribute longEnd = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END));
                return String.format("from %1$s %2$s to %3$s %4$s", stringValueOf(latStart), stringValueOf(longStart), stringValueOf(latEnd), stringValueOf(longEnd)); // NON-NLS
            }),
    GPS_TRACKPOINT(NbBundle.getMessage(MiscTypes.class, "MiscTypes.GPSTrackpoint.name"), "gps-trackpoint.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_GPS_TRACKPOINT),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)),
            artf -> {
                final BlackboardAttribute longitude = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE));
                final BlackboardAttribute latitude = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE));
                return stringValueOf(latitude) + " " + stringValueOf(longitude); // NON-NLS
            },
            new EmptyExtractor()),
    CALL_LOG(NbBundle.getMessage(MiscTypes.class, "MiscTypes.Calls.name"), "calllog.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_CALLLOG),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_START),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DIRECTION))),
    EMAIL(NbBundle.getMessage(MiscTypes.class, "MiscTypes.Email.name"), "mail-icon-16.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_EMAIL_MSG),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_SENT),
            artf -> {
                final BlackboardAttribute emailFrom = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_FROM));
                final BlackboardAttribute emailTo = getAttributeSafe(artf, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_TO));
                return stringValueOf(emailFrom) + " to " + stringValueOf(emailTo); // NON-NLS
            },
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SUBJECT)),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN))),
    RECENT_DOCUMENTS(NbBundle.getMessage(MiscTypes.class, "MiscTypes.recentDocuments.name"), "recent_docs.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_RECENT_OBJECT),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH)).andThen(
                    (String t) -> (StringUtils.substringBeforeLast(StringUtils.substringBeforeLast(t, "\\"), "\\"))),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH)).andThen(
                    (String t) -> StringUtils.substringBeforeLast(t, "\\")),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH))) {

                @Override
        public AttributeEventDescription parseAttributesHelper(BlackboardArtifact artf) throws TskCoreException {
            final BlackboardAttribute dateTimeAttr = artf.getAttribute(getDateTimeAttributeType());

                    long time = dateTimeAttr.getValueLong();

                    //Non-default description construction
                    String shortDescription = getShortExtractor().apply(artf);
                    String medDescription = getMedExtractor().apply(artf);
                    String fullDescription = getFullExtractor().apply(artf);

                    return new AttributeEventDescription(time, shortDescription, medDescription, fullDescription);
                }
            },
    INSTALLED_PROGRAM(NbBundle.getMessage(MiscTypes.class, "MiscTypes.installedPrograms.name"), "programs.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_INSTALLED_PROG),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)),
            new EmptyExtractor(),
            new EmptyExtractor()),
    EXIF(NbBundle.getMessage(MiscTypes.class, "MiscTypes.exif.name"), "camera-icon-16.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_METADATA_EXIF),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE)),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL)),
            artf -> {
                try {
                    AbstractFile file = artf.getSleuthkitCase().getAbstractFileById(artf.getObjectID());
                    if (file != null) {
                        return file.getName();
                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Exif event type failed to look up backing file name", ex); //NON-NLS
                }
                return "error loading file name";
            }),
    DEVICES_ATTACHED(NbBundle.getMessage(MiscTypes.class, "MiscTypes.devicesAttached.name"), "usb_devices.png", // NON-NLS
            new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED),
            new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE)),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL)),
            new AttributeExtractor(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_ID)));

    static public String stringValueOf(BlackboardAttribute attr) {
        return Optional.ofNullable(attr)
                .map(BlackboardAttribute::getDisplayString)
                .orElse("");
    }

    public static String toFrom(BlackboardAttribute dir) {
        if (dir == null) {
            return "";
        } else {
            switch (dir.getDisplayString()) {
                case "Incoming": // NON-NLS
                    return "from"; // NON-NLS
                case "Outgoing": // NON-NLS
                    return "to"; // NON-NLS
                default:
                    return ""; // NON-NLS
            }
        }
    }

    private final BlackboardAttribute.Type dateTimeAttributeType;

    private final String iconBase;

    private final Image image;

    @Override
    public Image getFXImage() {
        return image;
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

    @Override
    public BlackboardAttribute.Type getDateTimeAttributeType() {
        return dateTimeAttributeType;
    }

    @Override
    public EventTypeZoomLevel getZoomLevel() {
        return EventTypeZoomLevel.SUB_TYPE;
    }

    private final String displayName;

    private final BlackboardArtifact.Type artifactType;

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getIconBase() {
        return iconBase;
    }

    @Override
    public EventType getSubType(String string) {
        return MiscTypes.valueOf(string);
    }

    private MiscTypes(String displayName, String iconBase, BlackboardArtifact.Type artifactType,
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
        return BaseTypes.MISC_TYPES;
    }

    @Override
    public List<? extends EventType> getSubTypes() {
        return Collections.emptyList();
    }

    @Override
    public BlackboardArtifact.Type getArtifactType() {
        return artifactType;
    }

}
