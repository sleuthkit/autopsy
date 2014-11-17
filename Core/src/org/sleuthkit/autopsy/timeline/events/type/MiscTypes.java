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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public enum MiscTypes implements EventType, ArtifactEventType {

    MESSAGE("Messages", "message.png",
            BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE,
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
            new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE),
            (artf, attrMap) -> {
                final BlackboardAttribute dir = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION);
                final BlackboardAttribute readStatus = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS);
                final BlackboardAttribute name = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
                final BlackboardAttribute phoneNumber = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER);
                final BlackboardAttribute subject = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT);
                List<String> asList = Arrays.asList(stringValueOf(dir), stringValueOf(readStatus), name != null || phoneNumber != null ? toFrom(dir) : "", stringValueOf(name != null ? name : phoneNumber), (subject == null ? "" : stringValueOf(subject)));
                return StringUtils.join(asList, " ");
            },
            new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT)),
    GPS_ROUTE("GPS Routes", "gps-search.png",
              BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE,
              BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
              new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME),
              new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION),
              (BlackboardArtifact artf, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attrMap) -> {
                  final BlackboardAttribute latStart = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
                  final BlackboardAttribute longStart = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);
                  final BlackboardAttribute latEnd = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
                  final BlackboardAttribute longEnd = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);
                  return String.format("from %1$g %2$g to %3$g %4$g", latStart.getValueDouble(), longStart.getValueDouble(), latEnd.getValueDouble(), longEnd.getValueDouble());
              }),
    GPS_TRACKPOINT("Location History", "gps_trackpoint.png",
                   BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT,
                   BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
                   new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME),
                   (artf, attrMap) -> {
                       final BlackboardAttribute longitude = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
                       final BlackboardAttribute latitude = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
                       return (latitude != null ? latitude.getValueDouble() : "") + " " + (longitude != null ? longitude.getValueDouble() : "");
                   },
                   (artf, attrMap) -> ""),
    CALL_LOG("Calls", "calllog.png",
             BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG,
             BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START,
             new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME),
             new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER),
             new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION)),
    EMAIL("Email", "mail-icon-16.png",
          BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG,
          BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT,
          (artifact, attrMap) -> {
              final BlackboardAttribute emailFrom = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM);
              final BlackboardAttribute emailTo = attrMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO);
              return (emailFrom != null ? emailFrom.getValueString() : "") + " to " + (emailTo != null ? emailTo.getValueString() : "");
          },
          new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT),
          new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN)),
    RECENT_DOCUMENTS("Recent Documents", "recent_docs.png",
                     BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT,
                     BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
                     new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH).andThen(
                             (String t) -> (StringUtils.substringBeforeLast(StringUtils.substringBeforeLast(t, "\\"), "\\"))),
                     new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH).andThen(
                             (String t) -> StringUtils.substringBeforeLast(t, "\\")),
                     new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH)) {

                /** Override
                 * {@link ArtifactEventType#parseAttributesHelper(org.sleuthkit.datamodel.BlackboardArtifact, java.util.Map)}
                 * with non-default description construction */
                @Override
        public AttributeEventDescription parseAttributesHelper(BlackboardArtifact artf, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attrMap) throws TskCoreException {
                    final BlackboardAttribute dateTimeAttr = attrMap.get(getDateTimeAttrubuteType());

                    long time = dateTimeAttr.getValueLong();

                    //Non-default description construction
                    String shortDescription = getShortExtractor().apply(artf, attrMap);
                    String medDescription = getMedExtractor().apply(artf, attrMap);
                    String fullDescription = getFullExtractor().apply(artf, attrMap);

                    return new AttributeEventDescription(time, shortDescription, medDescription, fullDescription);
                }
            },
    INSTALLED_PROGRAM("Installed Programs", "programs.png",
                      BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG,
                      BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
                      new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME),
                      new EmptyExtractor(),
                      new EmptyExtractor()),
    EXIF("Exif", "camera-icon-16.png",
         BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF,
         BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
         new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE),
         new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL),
         (BlackboardArtifact t,
          Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> u) -> {
             try {
                 return t.getSleuthkitCase().getAbstractFileById(t.getObjectID()).getName();
             } catch (TskCoreException ex) {
                 Exceptions.printStackTrace(ex);
                 return " error loading file name";
             }
         }),
    DEVICES_ATTACHED("Devices Attached", "usb_devices.png",
                     BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED,
                     BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
                     new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE),
                     new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL),
                     new AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID));

    static public String stringValueOf(BlackboardAttribute attr) {
        return attr != null ? attr.getDisplayString() : "";
    }

    public static String toFrom(BlackboardAttribute dir) {
        if (dir == null) {
            return "";
        } else {
            switch (dir.getDisplayString()) {
                case "Incoming":
                    return "from";
                case "Outgoing":
                    return "to";
                default:
                    return "";
            }
        }
    }

    private final BlackboardAttribute.ATTRIBUTE_TYPE dateTimeAttributeType;

    private final String iconBase;

    private final Image image;

    @Override
    public Image getFXImage() {
        return image;
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

    @Override
    public BlackboardAttribute.ATTRIBUTE_TYPE getDateTimeAttrubuteType() {
        return dateTimeAttributeType;
    }

    @Override
    public EventTypeZoomLevel getZoomLevel() {
        return EventTypeZoomLevel.SUB_TYPE;
    }

    private final String displayName;

    private final BlackboardArtifact.ARTIFACT_TYPE artifactType;

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

    private MiscTypes(String displayName, String iconBase, BlackboardArtifact.ARTIFACT_TYPE artifactType,
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
        return BaseTypes.MISC_TYPES;
    }

    @Override
    public List<? extends EventType> getSubTypes() {
        return Collections.emptyList();
    }

    @Override
    public BlackboardArtifact.ARTIFACT_TYPE getArtifactType() {
        return artifactType;
    }

}
