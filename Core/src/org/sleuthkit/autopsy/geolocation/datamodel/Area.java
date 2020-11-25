/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil.InvalidJsonException;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoAreaPoints;

/**
 * A GPS track with which wraps the TSK_GPS_AREA artifact.
 */
public final class Area extends GeoPath {
    /**
     * Construct a new Area for the given artifact.
     *
     * @param artifact
     *
     * @throws GeoLocationDataException
     */
    public Area(BlackboardArtifact artifact) throws GeoLocationDataException {
        this(artifact, Waypoint.getAttributesFromArtifactAsMap(artifact));
    }

    /**
     * Construct an Area for the given artifact and attributeMap.
     *
     * @param artifact     TSK_GPD_TRACK artifact
     * @param attributeMap Map of the artifact attributes
     *
     * @throws GeoLocationDataException
     */
    private Area(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        super(artifact, getAreaName(attributeMap));

        GeoAreaPoints points = getPointsList(attributeMap);
        buildPath(points, artifact);
    }

    /**
     * Return the name of the area from the attributeMap. Track name is stored
     * in the attribute TSK_NAME
     *
     * @param attributeMap
     *
     * @return Area name or empty string if none was available.
     */
    private static String getAreaName(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) {
        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);

        return attribute != null ? attribute.getValueString() : "";
    }

    /**
     * Create the list of AreaWaypoints from the GeoTrackPoint list.
     *
     * @param points   GeoAreaPoints object.
     * @param artifact The artifact to which these points belong
     *
     * @throws GeoLocationDataException
     */
    @Messages({
        "# {0} - area name",
        "GEOArea_point_label_header=Area outline point for area: {0}"
    })
    private void buildPath(GeoAreaPoints points, BlackboardArtifact artifact) throws GeoLocationDataException {
        for (GeoAreaPoints.AreaPoint point : points) {
            addToPath(new AreaWaypoint(artifact, Bundle.GEOArea_point_label_header(getLabel()), point));
        }
    }

    /**
     * Returns the list of GeoAreaPoints from the attributeMap. Creates the
     * GeoAreaPoint list from the TSK_GEO_AREAPOINTS attribute.
     *
     * @param attributeMap Map of artifact attributes.
     *
     * @return GeoTrackPoint list empty list if the attribute was not found.
     *
     * @throws GeoLocationDataException
     */
    private GeoAreaPoints getPointsList(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_AREAPOINTS);
        if (attribute == null) {
            throw new GeoLocationDataException("No TSK_GEO_AREAPOINTS attribute present in attribute map to parse.");
        }

        try {
            return BlackboardJsonAttrUtil.fromAttribute(attribute, GeoAreaPoints.class);
        } catch (InvalidJsonException ex) {
            throw new GeoLocationDataException("Unable to parse area points in TSK_GEO_AREAPOINTS attribute", ex);
        }
    }

    /**
     * A Waypoint subclass for the points of an area outline.
     */
    final class AreaWaypoint extends Waypoint {

        private final List<Waypoint.Property> propertyList;

        /**
         * Construct a AreaWaypoint.
         *
         * @param artifact   the artifact to which this waypoint belongs
         *
         * @param pointLabel the label for the waypoint
         *
         * @param point      GeoAreaPoint
         *
         * @throws GeoLocationDataException
         */
        AreaWaypoint(BlackboardArtifact artifact, String pointLabel, GeoAreaPoints.AreaPoint point) throws GeoLocationDataException {
            super(artifact, pointLabel,
                    null,
                    point.getLatitude(),
                    point.getLongitude(),
                    null,
                    null,
                    null,
                    Area.this);

            propertyList = createPropertyList(point);
        }

        /**
         * Overloaded to return a property list that is generated from the
         * GeoTrackPoint instead of an artifact.
         *
         * @return unmodifiable list of Waypoint.Property
         */
        @Override
        public List<Waypoint.Property> getOtherProperties() {
            return Collections.unmodifiableList(propertyList);
        }

        /**
         * Create a propertyList specific to GeoAreaPoints.
         *
         * @param point GeoAreaPoint to get values from.
         *
         * @return A list of Waypoint.properies.
         */
        private List<Waypoint.Property> createPropertyList(GeoAreaPoints.AreaPoint point) {
            List<Waypoint.Property> list = new ArrayList<>();
            return list;
        }
    }
}
