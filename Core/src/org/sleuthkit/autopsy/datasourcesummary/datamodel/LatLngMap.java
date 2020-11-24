/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.geolocation.KdTree;
import org.sleuthkit.autopsy.geolocation.KdTree.XYZPoint;

/**
 * Divides map into grid and places each grid square in separate index in a hashmap.
 */
class LatLngMap<E extends KdTree.XYZPoint> {
    // radius of Earth in meters
    private static final double EARTH_RADIUS = 6371e3;
    
    // 300 km buckets with 150km accuracy
    private static final double BUCKET_SIZE = 300 * 1000;

    
    private final Map<Pair<Integer, Integer>, KdTree<E>> latLngMap;
    
    private static Pair<Double, Double> vectorDistance(XYZPoint point) {
        double y = euclideanDistance(new XYZPoint(0D, 0D), new XYZPoint(0D, point.getY())) / BUCKET_SIZE;
        if (point.getY() < 0) {
            y = -y;
        }
        
        double x = euclideanDistance(new XYZPoint(0D, point.getY()), new XYZPoint(point.getX(), point.getY())) / BUCKET_SIZE;
        if (point.getX() < 0) {
            x = -x;
        }
        
        return Pair.of(x, y);
    }
    
    private static final Function<XYZPoint, Pair<Integer, Integer>> bucketCalculator = (point) -> {
        Pair<Double, Double> dPair = vectorDistance(point);
        return Pair.of((int) (double) dPair.getLeft(), (int) (double) dPair.getRight());
    };

    LatLngMap(List<E> pointsToAdd) {
        Map<Pair<Integer, Integer>, List<E>> latLngBuckets = pointsToAdd.stream()
                .collect(Collectors.groupingBy((pt) -> bucketCalculator.apply(pt)));

        latLngMap = latLngBuckets.entrySet().stream()
                .map(e -> Pair.of(e.getKey(), new KdTree<E>(e.getValue())))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
    }

    E findClosest(E point) {
        Pair<Double, Double> calculated = vectorDistance(point);
        int latBucket = (int) (double) calculated.getLeft();
        int latBucket2 = Math.round(calculated.getLeft()) == latBucket ? latBucket - 1 : latBucket + 1;

        int lngBucket = (int) (double) calculated.getRight();
        int lngBucket2 = Math.round(calculated.getRight()) == lngBucket ? lngBucket - 1 : lngBucket + 1;

        E closest1 = findClosestInBucket(latBucket, lngBucket, point);
        E closest2 = findClosestInBucket(latBucket2, lngBucket, point);
        E closest3 = findClosestInBucket(latBucket, lngBucket2, point);
        E closest4 = findClosestInBucket(latBucket2, lngBucket2, point);

        return Stream.of(closest1, closest2, closest3, closest4)
                .filter(c -> c != null && euclideanDistance(point, c) <= BUCKET_SIZE / 2)
                .min((a,b) -> Double.compare(euclideanDistance(point, a), euclideanDistance(point, b)))
                .orElse(null);
    }

    private E findClosestInBucket(int lat, int lng, E point) {
        KdTree<E> thisLatLngMap = latLngMap.get(Pair.of(lat, lng));
        if (thisLatLngMap == null) {
            return null;
        }
        
        Collection<E> closest =  thisLatLngMap.nearestNeighbourSearch(1, point);
        if (closest != null && closest.size() > 0) {
            return closest.iterator().next();
        } else {
            return null;
        }
    }

    /**
     * Computes the Euclidean distance from one point to the other.
     *
     * Source for the distance calculation:
     * https://www.movable-type.co.uk/scripts/latlong.html
     *
     * @param o1 first point.
     * @param o2 second point.
     *
     * @return euclidean distance.
     */
    private static double euclideanDistance(KdTree.XYZPoint o1, KdTree.XYZPoint o2) {
        if (o1.equals(o2)) {
            return 0;
        }

        double lat1Radians = Math.toRadians(o1.getX());
        double lat2Radians = Math.toRadians(o2.getX());

        double deltaLatRadians = Math.toRadians(o2.getX() - o1.getX());
        double deltaLongRadians = Math.toRadians(o2.getY() - o1.getY());

        double a = Math.sin(deltaLatRadians / 2) * Math.sin(deltaLatRadians / 2)
                + Math.cos(lat1Radians) * Math.cos(lat2Radians)
                * Math.sin(deltaLongRadians / 2) * Math.sin(deltaLongRadians / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}
