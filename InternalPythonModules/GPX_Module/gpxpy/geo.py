# -*- coding: utf-8 -*-

# Copyright 2011 Tomo Krajina
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging as mod_logging
import math as mod_math

from . import utils as mod_utils

log = mod_logging.getLogger(__name__)

# Generic geo related function and class(es)

# latitude/longitude in GPX files is always in WGS84 datum
# WGS84 defined the Earth semi-major axis with 6378.137 km
EARTH_RADIUS = 6378.137 * 1000

# One degree in meters:
ONE_DEGREE = (2*mod_math.pi*EARTH_RADIUS) / 360  # ==> 111.319 km


def to_rad(x):
    return x / 180. * mod_math.pi


def haversine_distance(latitude_1, longitude_1, latitude_2, longitude_2):
    """
    Haversine distance between two points, expressed in meters.

    Implemented from http://www.movable-type.co.uk/scripts/latlong.html
    """
    d_lat = to_rad(latitude_1 - latitude_2)
    d_lon = to_rad(longitude_1 - longitude_2)
    lat1 = to_rad(latitude_1)
    lat2 = to_rad(latitude_2)

    a = mod_math.sin(d_lat/2) * mod_math.sin(d_lat/2) + \
        mod_math.sin(d_lon/2) * mod_math.sin(d_lon/2) * mod_math.cos(lat1) * mod_math.cos(lat2)
    c = 2 * mod_math.atan2(mod_math.sqrt(a), mod_math.sqrt(1-a))
    d = EARTH_RADIUS * c

    return d


def length(locations=None, _3d=None):
    locations = locations or []
    if not locations:
        return 0
    length = 0
    for i in range(len(locations)):
        if i > 0:
            previous_location = locations[i - 1]
            location = locations[i]

            if _3d:
                d = location.distance_3d(previous_location)
            else:
                d = location.distance_2d(previous_location)
            if d:
                length += d
    return length


def length_2d(locations=None):
    """ 2-dimensional length (meters) of locations (only latitude and longitude, no elevation). """
    locations = locations or []
    return length(locations, False)


def length_3d(locations=None):
    """ 3-dimensional length (meters) of locations (it uses latitude, longitude, and elevation). """
    locations = locations or []
    return length(locations, True)


def calculate_max_speed(speeds_and_distances):
    """
    Compute average distance and standard deviation for distance. Extremes
    in distances are usually extremes in speeds, so we will ignore them,
    here.

    speeds_and_distances must be a list containing pairs of (speed, distance)
    for every point in a track segment.
    """
    assert speeds_and_distances
    if len(speeds_and_distances) > 0:
        assert len(speeds_and_distances[0]) == 2
        # ...
        assert len(speeds_and_distances[-1]) == 2

    size = len(speeds_and_distances)

    if size < 20:
        log.debug('Segment too small to compute speed, size=%s', size)
        return None

    distances = list(map(lambda x: x[1], speeds_and_distances))
    average_distance = sum(distances) / float(size)
    standard_distance_deviation = mod_math.sqrt(sum(map(lambda distance: (distance-average_distance)**2, distances))/float(size))

    # Ignore items where the distance is too big:
    filtered_speeds_and_distances = filter(lambda speed_and_distance: abs(speed_and_distance[1] - average_distance) <= standard_distance_deviation * 1.5, speeds_and_distances)

    # sort by speed:
    speeds = list(map(lambda speed_and_distance: speed_and_distance[0], filtered_speeds_and_distances))
    if not isinstance(speeds, list):  # python3
        speeds = list(speeds)
    if not speeds:
        return None
    speeds.sort()

    # Even here there may be some extremes => ignore the last 5%:
    index = int(len(speeds) * 0.95)
    if index >= len(speeds):
        index = -1

    return speeds[index]


def calculate_uphill_downhill(elevations):
    if not elevations:
        return 0, 0

    size = len(elevations)

    def __filter(n):
        current_ele = elevations[n]
        if current_ele is None:
            return False
        if 0 < n < size - 1:
            previous_ele = elevations[n-1]
            next_ele = elevations[n+1]
            if previous_ele is not None and current_ele is not None and next_ele is not None:
                return previous_ele*.3 + current_ele*.4 + next_ele*.3
        return current_ele

    smoothed_elevations = list(map(__filter, range(size)))

    uphill, downhill = 0., 0.

    for n, elevation in enumerate(smoothed_elevations):
        if n > 0 and elevation is not None and smoothed_elevations is not None:
            d = elevation - smoothed_elevations[n-1]
            if d > 0:
                uphill += d
            else:
                downhill -= d

    return uphill, downhill


def distance(latitude_1, longitude_1, elevation_1, latitude_2, longitude_2, elevation_2,
             haversine=None):
    """
    Distance between two points. If elevation is None compute a 2d distance

    if haversine==True -- haversine will be used for every computations,
    otherwise...

    Haversine distance will be used for distant points where elevation makes a
    small difference, so it is ignored. That's because haversine is 5-6 times
    slower than the dummy distance algorithm (which is OK for most GPS tracks).
    """

    # If points too distant -- compute haversine distance:
    if haversine or (abs(latitude_1 - latitude_2) > .2 or abs(longitude_1 - longitude_2) > .2):
        return haversine_distance(latitude_1, longitude_1, latitude_2, longitude_2)

    coef = mod_math.cos(latitude_1 / 180. * mod_math.pi)
    x = latitude_1 - latitude_2
    y = (longitude_1 - longitude_2) * coef

    distance_2d = mod_math.sqrt(x * x + y * y) * ONE_DEGREE

    if elevation_1 is None or elevation_2 is None or elevation_1 == elevation_2:
        return distance_2d

    return mod_math.sqrt(distance_2d ** 2 + (elevation_1 - elevation_2) ** 2)


def elevation_angle(location1, location2, radians=False):
    """ Uphill/downhill angle between two locations. """
    if location1.elevation is None or location2.elevation is None:
        return None

    b = float(location2.elevation - location1.elevation)
    a = location2.distance_2d(location1)

    if a == 0:
        return 0

    angle = mod_math.atan(b / a)

    if radians:
        return angle

    return 180 * angle / mod_math.pi


def distance_from_line(point, line_point_1, line_point_2):
    """ Distance of point from a line given with two points. """
    assert point, point
    assert line_point_1, line_point_1
    assert line_point_2, line_point_2

    a = line_point_1.distance_2d(line_point_2)

    if a == 0:
        return line_point_1.distance_2d(point)

    b = line_point_1.distance_2d(point)
    c = line_point_2.distance_2d(point)

    s = (a + b + c) / 2.

    return 2. * mod_math.sqrt(abs(s * (s - a) * (s - b) * (s - c))) / a


def get_line_equation_coefficients(location1, location2):
    """
    Get line equation coefficients for:
        latitude * a + longitude * b + c = 0

    This is a normal cartesian line (not spherical!)
    """
    if location1.longitude == location2.longitude:
        # Vertical line:
        return float(0), float(1), float(-location1.longitude)
    else:
        a = float(location1.latitude - location2.latitude) / (location1.longitude - location2.longitude)
        b = location1.latitude - location1.longitude * a
        return float(1), float(-a), float(-b)


def simplify_polyline(points, max_distance):
    """Does Ramer-Douglas-Peucker algorithm for simplification of polyline """

    if len(points) < 3:
        return points

    begin, end = points[0], points[-1]

    # Use a "normal" line just to detect the most distant point (not its real distance)
    # this is because this is faster to compute than calling distance_from_line() for
    # every point.
    #
    # This is an approximation and may have some errors near the poles and if
    # the points are too distant, but it should be good enough for most use
    # cases...
    a, b, c = get_line_equation_coefficients(begin, end)

    # Initialize to safe values
    tmp_max_distance = 0
    tmp_max_distance_position = 1
    
    # Check distance of all points between begin and end, exclusive
    for point_no in range(1,len(points)-1):
        point = points[point_no]
        d = abs(a * point.latitude + b * point.longitude + c)
        if d > tmp_max_distance:
            tmp_max_distance = d
            tmp_max_distance_position = point_no

    # Now that we have the most distance point, compute its real distance:
    real_max_distance = distance_from_line(points[tmp_max_distance_position], begin, end)

    # If furthest point is less than max_distance, remove all points between begin and end
    if real_max_distance < max_distance:
        return [begin, end]
    
    # If furthest point is more than max_distance, use it as anchor and run
    # function again using (begin to anchor) and (anchor to end), remove extra anchor
    return (simplify_polyline(points[:tmp_max_distance_position + 1], max_distance) +
            simplify_polyline(points[tmp_max_distance_position:], max_distance)[1:])


class Location:
    """ Generic geographical location """

    latitude = None
    longitude = None
    elevation = None

    def __init__(self, latitude, longitude, elevation=None):
        self.latitude = latitude
        self.longitude = longitude
        self.elevation = elevation

    def has_elevation(self):
        return self.elevation or self.elevation == 0

    def remove_elevation(self):
        self.elevation = None

    def distance_2d(self, location):
        if not location:
            return None

        return distance(self.latitude, self.longitude, None, location.latitude, location.longitude, None)

    def distance_3d(self, location):
        if not location:
            return None

        return distance(self.latitude, self.longitude, self.elevation, location.latitude, location.longitude, location.elevation)

    def elevation_angle(self, location, radians=False):
        return elevation_angle(self, location, radians)

    def move(self, location_delta):
        self.latitude, self.longitude = location_delta.move(self)

    def __add__(self, location_delta):
        latitude, longitude = location_delta.move(self)
        return Location(latitude, longitude)

    def __str__(self):
        return '[loc:%s,%s@%s]' % (self.latitude, self.longitude, self.elevation)

    def __repr__(self):
        if self.elevation is None:
            return 'Location(%s, %s)' % (self.latitude, self.longitude)
        else:
            return 'Location(%s, %s, %s)' % (self.latitude, self.longitude, self.elevation)

    def __hash__(self):
        return mod_utils.hash_object(self, ('latitude', 'longitude', 'elevation'))


class LocationDelta:
    """
    Intended to use similar to timestamp.timedelta, but for Locations.
    """

    NORTH = 0
    EAST = 90
    SOUTH = 180
    WEST = 270

    def __init__(self, distance=None, angle=None, latitude_diff=None, longitude_diff=None):
        """
        Version 1:
            Distance (in meters).
            angle_from_north *clockwise*.
            ...must be given
        Version 2:
            latitude_diff and longitude_diff
            ...must be given
        """
        if (distance is not None) and (angle is not None):
            if (latitude_diff is not None) or (longitude_diff is not None):
                raise Exception('No lat/lon diff if using distance and angle!')
            self.distance = distance
            self.angle_from_north = angle
            self.move_function = self.move_by_angle_and_distance
        elif (latitude_diff is not None) and (longitude_diff is not None):
            if (distance is not None) or (angle is not None):
                raise Exception('No distance/angle if using lat/lon diff!')
            self.latitude_diff  = latitude_diff
            self.longitude_diff = longitude_diff
            self.move_function = self.move_by_lat_lon_diff

    def move(self, location):
        """
        Move location by this timedelta.
        """
        return self.move_function(location)

    def move_by_angle_and_distance(self, location):
        coef = mod_math.cos(location.latitude / 180. * mod_math.pi)
        vertical_distance_diff   = mod_math.sin((90 - self.angle_from_north) / 180. * mod_math.pi) / ONE_DEGREE
        horizontal_distance_diff = mod_math.cos((90 - self.angle_from_north) / 180. * mod_math.pi) / ONE_DEGREE
        lat_diff = self.distance * vertical_distance_diff
        lon_diff = self.distance * horizontal_distance_diff / coef
        return location.latitude + lat_diff, location.longitude + lon_diff

    def move_by_lat_lon_diff(self, location):
        return location.latitude + self.latitude_diff, location.longitude + self.longitude_diff
