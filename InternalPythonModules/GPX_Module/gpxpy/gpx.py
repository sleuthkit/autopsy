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

"""
GPX related stuff
"""

import logging as mod_logging
import math as mod_math
import collections as mod_collections
import copy as mod_copy
import datetime as mod_datetime

from . import utils as mod_utils
from . import geo as mod_geo
from . import gpxfield as mod_gpxfield

log = mod_logging.getLogger(__name__)

# GPX date format to be used when writing the GPX output:
DATE_FORMAT = '%Y-%m-%dT%H:%M:%SZ'

# GPX date format(s) used for parsing. The T between date and time and Z after
# time are allowed, too:
DATE_FORMATS = [
    '%Y-%m-%d %H:%M:%S.%f',
    '%Y-%m-%d %H:%M:%S',
]

# Used in smoothing, sum must be 1:
SMOOTHING_RATIO = (0.4, 0.2, 0.4)

# When computing stopped time -- this is the minimum speed between two points,
# if speed is less than this value -- we'll assume it is zero
DEFAULT_STOPPED_SPEED_THRESHOLD = 1

# Fields used for all point elements (route point, track point, waypoint):
GPX_10_POINT_FIELDS = [
        mod_gpxfield.GPXField('latitude', attribute='lat', type=mod_gpxfield.FLOAT_TYPE, mandatory=True),
        mod_gpxfield.GPXField('longitude', attribute='lon', type=mod_gpxfield.FLOAT_TYPE, mandatory=True),
        mod_gpxfield.GPXField('elevation', 'ele', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('time', type=mod_gpxfield.TIME_TYPE),
        mod_gpxfield.GPXField('magnetic_variation', 'magvar', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('geoid_height', 'geoidheight', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('name'),
        mod_gpxfield.GPXField('comment', 'cmt'),
        mod_gpxfield.GPXField('description', 'desc'),
        mod_gpxfield.GPXField('source', 'src'),
        mod_gpxfield.GPXField('link', 'url'),
        mod_gpxfield.GPXField('link_text', 'urlname'),
        mod_gpxfield.GPXField('symbol', 'sym'),
        mod_gpxfield.GPXField('type'),
        mod_gpxfield.GPXField('type_of_gpx_fix', 'fix', possible=('none', '2d', '3d', 'dgps', 'pps',)),
        mod_gpxfield.GPXField('satellites', 'sat', type=mod_gpxfield.INT_TYPE),
        mod_gpxfield.GPXField('horizontal_dilution', 'hdop', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('vertical_dilution', 'vdop', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('position_dilution', 'pdop', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('age_of_dgps_data', 'ageofdgpsdata', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('dgps_id', 'dgpsid'),
]
GPX_11_POINT_FIELDS = [
        # See GPX for description of text fields
        mod_gpxfield.GPXField('latitude', attribute='lat', type=mod_gpxfield.FLOAT_TYPE, mandatory=True),
        mod_gpxfield.GPXField('longitude', attribute='lon', type=mod_gpxfield.FLOAT_TYPE, mandatory=True),
        mod_gpxfield.GPXField('elevation', 'ele', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('time', type=mod_gpxfield.TIME_TYPE),
        mod_gpxfield.GPXField('magnetic_variation', 'magvar', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('geoid_height', 'geoidheight', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('name'),
        mod_gpxfield.GPXField('comment', 'cmt'),
        mod_gpxfield.GPXField('description', 'desc'),
        mod_gpxfield.GPXField('source', 'src'),
        'link:@link',
            mod_gpxfield.GPXField('link', attribute='href'),
            mod_gpxfield.GPXField('link_text', tag='text'),
            mod_gpxfield.GPXField('link_type', tag='type'),
        '/link',
        mod_gpxfield.GPXField('symbol', 'sym'),
        mod_gpxfield.GPXField('type'),
        mod_gpxfield.GPXField('type_of_gpx_fix', 'fix', possible=('none', '2d', '3d', 'dgps', 'pps',)),
        mod_gpxfield.GPXField('satellites', 'sat', type=mod_gpxfield.INT_TYPE),
        mod_gpxfield.GPXField('horizontal_dilution', 'hdop', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('vertical_dilution', 'vdop', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('position_dilution', 'pdop', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('age_of_dgps_data', 'ageofdgpsdata', type=mod_gpxfield.FLOAT_TYPE),
        mod_gpxfield.GPXField('dgps_id', 'dgpsid'),
        mod_gpxfield.GPXExtensionsField('extensions', is_list=True),
]

# GPX1.0 track points have two more fields after time
# Note that this is not true for GPX1.1
GPX_TRACK_POINT_FIELDS = GPX_10_POINT_FIELDS[:4] \
        + [ \
                mod_gpxfield.GPXField('course', type=mod_gpxfield.FLOAT_TYPE), \
                mod_gpxfield.GPXField('speed', type=mod_gpxfield.FLOAT_TYPE) \
          ] \
        + GPX_10_POINT_FIELDS[4:]

# When possible, the result of various methods are named tuples defined here:
TimeBounds = mod_collections.namedtuple(
    'TimeBounds',
    ('start_time', 'end_time'))
MovingData = mod_collections.namedtuple(
    'MovingData',
    ('moving_time', 'stopped_time', 'moving_distance', 'stopped_distance', 'max_speed'))
UphillDownhill = mod_collections.namedtuple(
    'UphillDownhill',
    ('uphill', 'downhill'))
MinimumMaximum = mod_collections.namedtuple(
    'MinimumMaximum',
    ('minimum', 'maximum'))
NearestLocationData = mod_collections.namedtuple(
    'NearestLocationData',
    ('location', 'track_no', 'segment_no', 'point_no'))
PointData = mod_collections.namedtuple(
    'PointData',
    ('point', 'distance_from_start', 'track_no', 'segment_no', 'point_no'))


class GPXException(Exception):
    """
    Exception used for invalid GPX files. It is used when the XML file is
    valid but something is wrong with the GPX data.
    """
    pass


class GPXBounds:
    gpx_10_fields = gpx_11_fields = [
            mod_gpxfield.GPXField('min_latitude', attribute='minlat', type=mod_gpxfield.FLOAT_TYPE),
            mod_gpxfield.GPXField('max_latitude', attribute='maxlat', type=mod_gpxfield.FLOAT_TYPE),
            mod_gpxfield.GPXField('min_longitude', attribute='minlon', type=mod_gpxfield.FLOAT_TYPE),
            mod_gpxfield.GPXField('max_longitude', attribute='maxlon', type=mod_gpxfield.FLOAT_TYPE),
    ]

    __slots__ = ('min_latitude', 'max_latitude', 'min_longitude', 'max_longitude')

    def __init__(self, min_latitude=None, max_latitude=None, min_longitude=None, max_longitude=None):
        self.min_latitude = min_latitude
        self.max_latitude = max_latitude
        self.min_longitude = min_longitude
        self.max_longitude = max_longitude

    def __iter__(self):
        return (self.min_latitude, self.max_latitude, self.min_longitude, self.max_longitude,).__iter__()


class GPXXMLSyntaxException(GPXException):
    """
    Exception used when the XML syntax is invalid.

    The __cause__ can be a minidom or lxml exception (See http://www.python.org/dev/peps/pep-3134/).
    """
    def __init__(self, message, original_exception):
        GPXException.__init__(self, message)
        self.__cause__ = original_exception


class GPXWaypoint(mod_geo.Location):
    gpx_10_fields = GPX_10_POINT_FIELDS
    gpx_11_fields = GPX_11_POINT_FIELDS

    __slots__ = ('latitude', 'longitude', 'elevation', 'time',
                 'magnetic_variation', 'geoid_height', 'name', 'comment',
                 'description', 'source', 'link', 'link_text', 'symbol',
                 'type', 'type_of_gpx_fix', 'satellites',
                 'horizontal_dilution', 'vertical_dilution',
                 'position_dilution', 'age_of_dgps_data', 'dgps_id',
                 'link_type', 'extensions')

    def __init__(self, latitude=None, longitude=None, elevation=None, time=None,
                 name=None, description=None, symbol=None, type=None,
                 comment=None, horizontal_dilution=None, vertical_dilution=None,
                 position_dilution=None):
        mod_geo.Location.__init__(self, latitude, longitude, elevation)

        self.latitude = latitude
        self.longitude = longitude
        self.elevation = elevation
        self.time = time
        self.magnetic_variation = None
        self.geoid_height = None
        self.name = name
        self.comment = comment
        self.description = description
        self.source = None
        self.link = None
        self.link_text = None
        self.link_type = None
        self.symbol = symbol
        self.type = type
        self.type_of_gpx_fix = None
        self.satellites = None
        self.horizontal_dilution = horizontal_dilution
        self.vertical_dilution = vertical_dilution
        self.position_dilution = position_dilution
        self.age_of_dgps_data = None
        self.dgps_id = None
        self.extensions = []

    def __str__(self):
        return '[wpt{%s}:%s,%s@%s]' % (self.name, self.latitude, self.longitude, self.elevation)

    def __repr__(self):
        representation = '%s, %s' % (self.latitude, self.longitude)
        for attribute in 'elevation', 'time', 'name', 'description', 'symbol', 'type', 'comment', \
                'horizontal_dilution', 'vertical_dilution', 'position_dilution':
            value = getattr(self, attribute)
            if value is not None:
                representation += ', %s=%s' % (attribute, repr(value))
        return 'GPXWaypoint(%s)' % representation

    def adjust_time(self, delta):
        """
        Adjusts the time of the point by the specified delta

        Parameters
        ----------
        delta : datetime.timedelta
            Positive time delta will adjust time into the future
            Negative time delta will adjust time into the past
        """
        if self.time:
            self.time += delta

    def remove_time(self):
        """ Will remove time metadata. """
        self.time = None

    def get_max_dilution_of_precision(self):
        """
        Only care about the max dop for filtering, no need to go into too much detail
        """
        return max(self.horizontal_dilution, self.vertical_dilution, self.position_dilution)


class GPXRoutePoint(mod_geo.Location):
    gpx_10_fields = GPX_10_POINT_FIELDS
    gpx_11_fields = GPX_11_POINT_FIELDS

    __slots__ = ('latitude', 'longitude', 'elevation', 'time',
                 'magnetic_variation', 'geoid_height', 'name', 'comment',
                 'description', 'source', 'link', 'link_text', 'symbol',
                 'type', 'type_of_gpx_fix', 'satellites',
                 'horizontal_dilution', 'vertical_dilution',
                 'position_dilution', 'age_of_dgps_data', 'dgps_id',
                 'link_type', 'extensions')

    def __init__(self, latitude=None, longitude=None, elevation=None, time=None, name=None,
                 description=None, symbol=None, type=None, comment=None,
                 horizontal_dilution=None, vertical_dilution=None,
                 position_dilution=None):

        mod_geo.Location.__init__(self, latitude, longitude, elevation)
        self.latitude = latitude
        self.longitude = longitude
        self.elevation = elevation
        self.time = time
        self.magnetic_variation = None
        self.geoid_height = None
        self.name = name
        self.comment = comment
        self.description = description
        self.source = None
        self.link = None
        self.link_text = None
        self.symbol = symbol
        self.type = type
        self.type_of_gpx_fix = None
        self.satellites = None
        self.horizontal_dilution = horizontal_dilution
        self.vertical_dilution = vertical_dilution
        self.position_dilution = position_dilution
        self.age_of_dgps_data = None
        self.dgps_id = None
        self.link_type = None
        self.extensions = []

    def __str__(self):
        return '[rtept{%s}:%s,%s@%s]' % (self.name, self.latitude, self.longitude, self.elevation)

    def __repr__(self):
        representation = '%s, %s' % (self.latitude, self.longitude)
        for attribute in 'elevation', 'time', 'name', 'description', 'symbol', 'type', 'comment', \
                'horizontal_dilution', 'vertical_dilution', 'position_dilution':
            value = getattr(self, attribute)
            if value is not None:
                representation += ', %s=%s' % (attribute, repr(value))
        return 'GPXRoutePoint(%s)' % representation

    def adjust_time(self, delta):
        """
        Adjusts the time of the point by the specified delta

        Parameters
        ----------
        delta : datetime.timedelta
            Positive time delta will adjust time into the future
            Negative time delta will adjust time into the past
        """
        if self.time:
            self.time += delta

    def remove_time(self):
        """ Will remove time metadata. """
        self.time = None


class GPXRoute:
    gpx_10_fields = [
            mod_gpxfield.GPXField('name'),
            mod_gpxfield.GPXField('comment', 'cmt'),
            mod_gpxfield.GPXField('description', 'desc'),
            mod_gpxfield.GPXField('source', 'src'),
            mod_gpxfield.GPXField('link', 'url'),
            mod_gpxfield.GPXField('link_text', 'urlname'),
            mod_gpxfield.GPXField('number', type=mod_gpxfield.INT_TYPE),
            mod_gpxfield.GPXComplexField('points', tag='rtept', classs=GPXRoutePoint, is_list=True),
    ]
    gpx_11_fields = [
            # See GPX for description of text fields
            mod_gpxfield.GPXField('name'),
            mod_gpxfield.GPXField('comment', 'cmt'),
            mod_gpxfield.GPXField('description', 'desc'),
            mod_gpxfield.GPXField('source', 'src'),
            'link:@link',
                mod_gpxfield.GPXField('link', attribute='href'),
                mod_gpxfield.GPXField('link_text', tag='text'),
                mod_gpxfield.GPXField('link_type', tag='type'),
            '/link',
            mod_gpxfield.GPXField('number', type=mod_gpxfield.INT_TYPE),
            mod_gpxfield.GPXField('type'),
            mod_gpxfield.GPXExtensionsField('extensions', is_list=True),
            mod_gpxfield.GPXComplexField('points', tag='rtept', classs=GPXRoutePoint, is_list=True),
    ]

    __slots__ = ('name', 'comment', 'description', 'source', 'link',
                 'link_text', 'number', 'points', 'link_type', 'type',
                 'extensions')

    def __init__(self, name=None, description=None, number=None):
        self.name = name
        self.comment = None
        self.description = description
        self.source = None
        self.link = None
        self.link_text = None
        self.number = number
        self.points = []
        self.link_type = None
        self.type = None
        self.extensions = []

    def adjust_time(self, delta):
        """
        Adjusts the time of the all the points in the route by the specified delta.

        Parameters
        ----------
        delta : datetime.timedelta
            Positive time delta will adjust time into the future
            Negative time delta will adjust time into the past
        """
        for point in self.points:
            point.adjust_time(delta)

    def remove_time(self):
        """ Removes time meta data from route. """
        for point in self.points:
            point.remove_time()

    def remove_elevation(self):
        """ Removes elevation data from route """
        for point in self.points:
            point.remove_elevation()

    def length(self):
        """
        Computes length (2-dimensional) of route.

        Returns:
        -----------
        length: float
            Length returned in meters
        """
        return mod_geo.length_2d(self.points)

    def get_center(self):
        """
        Get the center of the route.

        Returns
        -------
        center: Location
            latitude: latitude of center in degrees
            longitude: longitude of center in degrees
            elevation: not calculated here
        """
        if not self.points:
            return None

        if not self.points:
            return None

        sum_lat = 0.
        sum_lon = 0.
        n = 0.

        for point in self.points:
            n += 1.
            sum_lat += point.latitude
            sum_lon += point.longitude

        if not n:
            return mod_geo.Location(float(0), float(0))

        return mod_geo.Location(latitude=sum_lat / n, longitude=sum_lon / n)

    def walk(self, only_points=False):
        """
        Generator for iterating over route points

        Parameters
        ----------
        only_points: boolean
            Only yield points (no index yielded)

        Yields
        ------
        point: GPXRoutePoint
            A point in the GPXRoute
        point_no: int
            Not included in yield if only_points is true
        """
        for point_no, point in enumerate(self.points):
            if only_points:
                yield point
            else:
                yield point, point_no

    def get_points_no(self):
        """
        Get the number of points in route.

        Returns
        ----------
        num_points : integer
            Number of points in route
        """
        return len(self.points)

    def move(self, location_delta):
        """
        Moves each point in the route.

        Parameters
        ----------
        location_delta: LocationDelta
            LocationDelta to move each point
        """
        for route_point in self.points:
            route_point.move(location_delta)

    def __repr__(self):
        representation = ''
        for attribute in 'name', 'description', 'number':
            value = getattr(self, attribute)
            if value is not None:
                representation += '%s%s=%s' % (', ' if representation else '', attribute, repr(value))
        representation += '%spoints=[%s])' % (', ' if representation else '', '...' if self.points else '')
        return 'GPXRoute(%s)' % representation


class GPXTrackPoint(mod_geo.Location):
    gpx_10_fields = GPX_TRACK_POINT_FIELDS
    gpx_11_fields = GPX_11_POINT_FIELDS

    __slots__ = ('latitude', 'longitude', 'elevation', 'time', 'course',
                 'speed', 'magnetic_variation', 'geoid_height', 'name',
                 'comment', 'description', 'source', 'link', 'link_text',
                 'symbol', 'type', 'type_of_gpx_fix', 'satellites',
                 'horizontal_dilution', 'vertical_dilution',
                 'position_dilution', 'age_of_dgps_data', 'dgps_id',
                 'link_type', 'extensions')

    def __init__(self, latitude=None, longitude=None, elevation=None, time=None, symbol=None, comment=None,
                 horizontal_dilution=None, vertical_dilution=None, position_dilution=None, speed=None,
                 name=None):
        mod_geo.Location.__init__(self, latitude, longitude, elevation)
        self.latitude = latitude
        self.longitude = longitude
        self.elevation = elevation
        self.time = time
        self.course = None
        self.speed = speed
        self.magnetic_variation = None
        self.geoid_height = None
        self.name = name
        self.comment = comment
        self.description = None
        self.source = None
        self.link = None
        self.link_text = None
        self.link_type = None
        self.symbol = symbol
        self.type = None
        self.type_of_gpx_fix = None
        self.satellites = None
        self.horizontal_dilution = horizontal_dilution
        self.vertical_dilution = vertical_dilution
        self.position_dilution = position_dilution
        self.age_of_dgps_data = None
        self.dgps_id = None
        self.extensions = []

    def __repr__(self):
        representation = '%s, %s' % (self.latitude, self.longitude)
        for attribute in 'elevation', 'time', 'symbol', 'comment', 'horizontal_dilution', \
                'vertical_dilution', 'position_dilution', 'speed', 'name':
            value = getattr(self, attribute)
            if value is not None:
                representation += ', %s=%s' % (attribute, repr(value))
        return 'GPXTrackPoint(%s)' % representation

    def adjust_time(self, delta):
        """
        Adjusts the time of the point by the specified delta

        Parameters
        ----------
        delta : datetime.timedelta
            Positive time delta will adjust time into the future
            Negative time delta will adjust time into the past
        """
        if self.time:
            self.time += delta

    def remove_time(self):
        """ Will remove time metadata. """
        self.time = None

    def time_difference(self, track_point):
        """
        Get time difference between specified point and this point.

        Parameters
        ----------
        track_point : GPXTrackPoint

        Returns
        ----------
        time_difference : float
            Time difference returned in seconds
        """
        if not self.time or not track_point or not track_point.time:
            return None

        time_1 = self.time
        time_2 = track_point.time

        if time_1 == time_2:
            return 0

        if time_1 > time_2:
            delta = time_1 - time_2
        else:
            delta = time_2 - time_1

        return mod_utils.total_seconds(delta)

    def speed_between(self, track_point):
        """
        Compute the speed between specified point and this point.

        NOTE: This is a computed speed, not the GPXTrackPoint speed that comes
              the GPX file.

        Parameters
        ----------
        track_point : GPXTrackPoint

        Returns
        ----------
        speed : float
            Speed returned in meters/second
        """
        if not track_point:
            return None

        seconds = self.time_difference(track_point)
        length = self.distance_3d(track_point)
        if not length:
            length = self.distance_2d(track_point)

        if not seconds or length is None:
            return None

        return length / float(seconds)

    def __str__(self):
        return '[trkpt:%s,%s@%s@%s]' % (self.latitude, self.longitude, self.elevation, self.time)


class GPXTrackSegment:
    gpx_10_fields = [
            mod_gpxfield.GPXComplexField('points', tag='trkpt', classs=GPXTrackPoint, is_list=True),
    ]
    gpx_11_fields = [
            mod_gpxfield.GPXComplexField('points', tag='trkpt', classs=GPXTrackPoint, is_list=True),
            mod_gpxfield.GPXExtensionsField('extensions', is_list=True),
    ]

    __slots__ = ('points', 'extensions', )

    def __init__(self, points=None):
        self.points = points if points else []
        self.extensions = []

    def simplify(self, max_distance=None):
        """
        Simplify using the Ramer-Douglas-Peucker algorithm: http://en.wikipedia.org/wiki/Ramer-Douglas-Peucker_algorithm
        """
        if not max_distance:
            max_distance = 10

        self.points = mod_geo.simplify_polyline(self.points, max_distance)

    def reduce_points(self, min_distance):
        """
        Reduces the number of points in the track segment. Segment points will
        be updated in place.

        Parameters
        ----------
        min_distance : float
            The minimum separation in meters between points
        """
        reduced_points = []
        for point in self.points:
            if reduced_points:
                distance = reduced_points[-1].distance_3d(point)
                if distance >= min_distance:
                    reduced_points.append(point)
            else:
                # Leave first point:
                reduced_points.append(point)

        self.points = reduced_points

    def _find_next_simplified_point(self, pos, max_distance):
        for candidate in range(pos + 1, len(self.points) - 1):
            for i in range(pos + 1, candidate):
                d = mod_geo.distance_from_line(self.points[i],
                                               self.points[pos],
                                               self.points[candidate])
                if d > max_distance:
                    return candidate - 1
        return None

    def adjust_time(self, delta):
        """
        Adjusts the time of all points in the segment by the specified delta

        Parameters
        ----------
        delta : datetime.timedelta
            Positive time delta will adjust point times into the future
            Negative time delta will adjust point times into the past
        """
        for track_point in self.points:
            track_point.adjust_time(delta)

    def remove_time(self):
        """ Removes time data for all points in the segment. """
        for track_point in self.points:
            track_point.remove_time()

    def remove_elevation(self):
        """ Removes elevation data for all points in the segment. """
        for track_point in self.points:
            track_point.remove_elevation()

    def length_2d(self):
        """
        Computes 2-dimensional length (meters) of segment (only latitude and
        longitude, no elevation).

        Returns
        ----------
        length : float
            Length returned in meters
        """
        return mod_geo.length_2d(self.points)

    def length_3d(self):
        """
        Computes 3-dimensional length of segment (latitude, longitude, and
        elevation).

        Returns
        ----------
        length : float
            Length returned in meters
        """
        return mod_geo.length_3d(self.points)

    def move(self, location_delta):
        """
        Moves each point in the segment.

        Parameters
        ----------
        location_delta: LocationDelta object
            Delta (distance/angle or lat/lon offset to apply each point in the
            segment
        """
        for track_point in self.points:
            track_point.move(location_delta)

    def walk(self, only_points=False):
        """
        Generator for iterating over segment points

        Parameters
        ----------
        only_points: boolean
            Only yield points (no index yielded)

        Yields
        ------
        point: GPXTrackPoint
            A point in the sement
        point_no: int
            Not included in yield if only_points is true
        """
        for point_no, point in enumerate(self.points):
            if only_points:
                yield point
            else:
                yield point, point_no

    def get_points_no(self):
        """
        Gets the number of points in segment.

        Returns
        ----------
        num_points : integer
            Number of points in segment
        """
        if not self.points:
            return 0
        return len(self.points)

    def split(self, point_no):
        """
        Splits the segment into two parts. If one of the split segments is
        empty it will not be added in the result. The segments will be split
        in place.

        Parameters
        ----------
        point_no : integer
            The index of the track point in the segment to split
        """
        part_1 = self.points[:point_no + 1]
        part_2 = self.points[point_no + 1:]
        return GPXTrackSegment(part_1), GPXTrackSegment(part_2)

    def join(self, track_segment):
        """ Joins with another segment """
        self.points += track_segment.points

    def remove_point(self, point_no):
        """ Removes a point specificed by index from the segment """
        if point_no < 0 or point_no >= len(self.points):
            return

        part_1 = self.points[:point_no]
        part_2 = self.points[point_no + 1:]

        self.points = part_1 + part_2

    def get_moving_data(self, stopped_speed_threshold=None):
        """
        Return a tuple of (moving_time, stopped_time, moving_distance,
        stopped_distance, max_speed) that may be used for detecting the time
        stopped, and max speed. Not that those values are not absolutely true,
        because the "stopped" or "moving" information aren't saved in the segment.

        Because of errors in the GPS recording, it may be good to calculate
        them on a reduced and smoothed version of the track.

        Parameters
        ----------
        stopped_speed_threshold : float
            speeds (km/h) below this threshold are treated as if having no
            movement. Default is 1 km/h.

        Returns
        ----------
        moving_data : MovingData : named tuple
            moving_time : float
                time (seconds) of segment in which movement was occurring
            stopped_time : float
                time (seconds) of segment in which no movement was occurring
            stopped_distance : float
                distance (meters) travelled during stopped times
            moving_distance : float
                distance (meters) travelled during moving times
            max_speed : float
                Maximum speed (m/s) during the segment.
        """
        if not stopped_speed_threshold:
            stopped_speed_threshold = DEFAULT_STOPPED_SPEED_THRESHOLD

        moving_time = 0.
        stopped_time = 0.

        moving_distance = 0.
        stopped_distance = 0.

        speeds_and_distances = []

        for i in range(1, len(self.points)):

            previous = self.points[i - 1]
            point = self.points[i]

            # Won't compute max_speed for first and last because of common GPS
            # recording errors, and because smoothing don't work well for those
            # points:
            if point.time and previous.time:
                timedelta = point.time - previous.time

                if point.elevation and previous.elevation:
                    distance = point.distance_3d(previous)
                else:
                    distance = point.distance_2d(previous)

                seconds = mod_utils.total_seconds(timedelta)
                speed_kmh = 0
                if seconds > 0:
                    # TODO: compute threshold in m/s instead this to kmh every time:
                    speed_kmh = (distance / 1000.) / (mod_utils.total_seconds(timedelta) / 60. ** 2)

                #print speed, stopped_speed_threshold
                if speed_kmh <= stopped_speed_threshold:
                    stopped_time += mod_utils.total_seconds(timedelta)
                    stopped_distance += distance
                else:
                    moving_time += mod_utils.total_seconds(timedelta)
                    moving_distance += distance

                    if distance and moving_time:
                        speeds_and_distances.append((distance / mod_utils.total_seconds(timedelta), distance, ))

        max_speed = None
        if speeds_and_distances:
            max_speed = mod_geo.calculate_max_speed(speeds_and_distances)

        return MovingData(moving_time, stopped_time, moving_distance, stopped_distance, max_speed)

    def get_time_bounds(self):
        """
        Gets the time bound (start and end) of the segment.

        returns
        ----------
        time_bounds : TimeBounds named tuple
            start_time : datetime
                Start time of the first segment in track
            end time : datetime
                End time of the last segment in track
        """
        start_time = None
        end_time = None

        for point in self.points:
            if point.time:
                if not start_time:
                    start_time = point.time
                if point.time:
                    end_time = point.time

        return TimeBounds(start_time, end_time)

    def get_bounds(self):
        """
        Gets the latitude and longitude bounds of the segment.

        Returns
        ----------
        bounds : Bounds named tuple
            min_latitude : float
                Minimum latitude of segment in decimal degrees [-90, 90]
            max_latitude : float
                Maximum latitude of segment in decimal degrees [-90, 90]
            min_longitude : float
                Minimum longitude of segment in decimal degrees [-180, 180]
            max_longitude : float
                Maximum longitude of segment in decimal degrees [-180, 180]
        """
        min_lat = None
        max_lat = None
        min_lon = None
        max_lon = None

        for point in self.points:
            if min_lat is None or point.latitude < min_lat:
                min_lat = point.latitude
            if max_lat is None or point.latitude > max_lat:
                max_lat = point.latitude
            if min_lon is None or point.longitude < min_lon:
                min_lon = point.longitude
            if max_lon is None or point.longitude > max_lon:
                max_lon = point.longitude

        return GPXBounds(min_lat, max_lat, min_lon, max_lon)

    def get_speed(self, point_no):
        """
        Computes the speed at the specified point index.

        Parameters
        ----------
        point_no : integer
            index of the point used to compute speed

        Returns
        ----------
        speed : float
            Speed returned in m/s
        """
        point = self.points[point_no]

        previous_point = None
        next_point = None

        if 0 < point_no < len(self.points):
            previous_point = self.points[point_no - 1]
        if 0 <= point_no < len(self.points) - 1:
            next_point = self.points[point_no + 1]

        #log.debug('previous: %s' % previous_point)
        #log.debug('next: %s' % next_point)

        speed_1 = point.speed_between(previous_point)
        speed_2 = point.speed_between(next_point)

        if speed_1:
            speed_1 = abs(speed_1)
        if speed_2:
            speed_2 = abs(speed_2)

        if speed_1 and speed_2:
            return (speed_1 + speed_2) / 2.

        if speed_1:
            return speed_1

        return speed_2

    def add_elevation(self, delta):
        """
        Adjusts elevation data for segment.

        Parameters
        ----------
        delta : float
            Elevation delta in meters to apply to track
        """
        log.debug('delta = %s' % delta)

        if not delta:
            return

        for track_point in self.points:
            if track_point.elevation is not None:
                track_point.elevation += delta

    def add_missing_data(self, get_data_function, add_missing_function):
        """
        Calculate missing data.

        Parameters
        ----------
        get_data_function : object
            Returns the data from point
        add_missing_function : void
            Function with the following arguments: array with points with missing data, the point before them (with data),
            the point after them (with data), and distance ratios between points in the interval (the sum of distances ratios
            will be 1)
        """
        if not get_data_function:
            raise GPXException('Invalid get_data_function: %s' % get_data_function)
        if not add_missing_function:
            raise GPXException('Invalid add_missing_function: %s' % add_missing_function)

        # Points (*without* data) between two points (*with* data):
        interval = []
        # Point (*with* data) before and after the interval:
        start_point = None

        previous_point = None
        for track_point in self.points:
            data = get_data_function(track_point)
            if data is None and previous_point:
                if not start_point:
                    start_point = previous_point
                interval.append(track_point)
            else:
                if interval:
                    distances_ratios = self._get_interval_distances_ratios(interval,
                                                                           start_point, track_point)
                    add_missing_function(interval, start_point, track_point,
                                         distances_ratios)
                    start_point = None
                    interval = []
            previous_point = track_point

    def _get_interval_distances_ratios(self, interval, start, end):
        assert start, start
        assert end, end
        assert interval, interval
        assert len(interval) > 0, interval

        distances = []
        distance_from_start = 0
        previous_point = start
        for point in interval:
            distance_from_start += float(point.distance_3d(previous_point))
            distances.append(distance_from_start)
            previous_point = point

        from_start_to_end = distances[-1] + interval[-1].distance_3d(end)

        assert len(interval) == len(distances)

        return list(map(
                lambda distance: (distance / from_start_to_end) if from_start_to_end else 0,
                distances))

    def get_duration(self):
        """
        Calculates duration or track segment

        Returns
        -------
        duration: float
            Duration in seconds
        """
        if not self.points or len(self.points) < 2:
            return 0

        # Search for start:
        first = self.points[0]
        if not first.time:
            first = self.points[1]

        last = self.points[-1]
        if not last.time:
            last = self.points[-2]

        if not last.time or not first.time:
            log.debug('Can\'t find time')
            return None

        if last.time < first.time:
            log.debug('Not enough time data')
            return None

        return mod_utils.total_seconds(last.time - first.time)

    def get_uphill_downhill(self):
        """
        Calculates the uphill and downhill elevation climbs for the track
        segment. If elevation for some points is not found those are simply
        ignored.

        Returns
        -------
        uphill_downhill: UphillDownhill named tuple
            uphill: float
                Uphill elevation climbs in meters
            downhill: float
                Downhill elevation descent in meters
        """
        if not self.points:
            return UphillDownhill(0, 0)

        elevations = list(map(lambda point: point.elevation, self.points))
        uphill, downhill = mod_geo.calculate_uphill_downhill(elevations)

        return UphillDownhill(uphill, downhill)

    def get_elevation_extremes(self):
        """
        Calculate elevation extremes of track segment

        Returns
        -------
        min_max_elevation: MinimumMaximum named tuple
            minimum: float
                Minimum elevation in meters
            maximum: float
                Maximum elevation in meters
        """
        if not self.points:
            return MinimumMaximum(None, None)

        elevations = map(lambda location: location.elevation, self.points)
        elevations = filter(lambda elevation: elevation is not None, elevations)
        elevations = list(elevations)

        if len(elevations) == 0:
            return MinimumMaximum(None, None)

        return MinimumMaximum(min(elevations), max(elevations))

    def get_location_at(self, time):
        """
        Gets approx. location at given time. Note that, at the moment this
        method returns an instance of GPXTrackPoint in the future -- this may
        be a mod_geo.Location instance with approximated latitude, longitude
        and elevation!
        """
        if not self.points:
            return None

        if not time:
            return None

        first_time = self.points[0].time
        last_time = self.points[-1].time

        if not first_time and not last_time:
            log.debug('No times for track segment')
            return None

        if not first_time <= time <= last_time:
            log.debug('Not in track (search for:%s, start:%s, end:%s)' % (time, first_time, last_time))
            return None

        for point in self.points:
            if point.time and time <= point.time:
                # TODO: If between two points -- approx position!
                # return mod_geo.Location(point.latitude, point.longitude)
                return point

    def get_nearest_location(self, location):
        """ Return the (location, track_point_no) on this track segment """
        if not self.points:
            return None, None

        result = None
        current_distance = None
        result_track_point_no = None
        for i in range(len(self.points)):
            track_point = self.points[i]
            if not result:
                result = track_point
            else:
                distance = track_point.distance_2d(location)
                #print current_distance, distance
                if not current_distance or distance < current_distance:
                    current_distance = distance
                    result = track_point
                    result_track_point_no = i

        return result, result_track_point_no

    def smooth(self, vertical=True, horizontal=False, remove_extremes=False):
        """ "Smooths" the elevation graph. Can be called multiple times. """
        if len(self.points) <= 3:
            return

        elevations = []
        latitudes = []
        longitudes = []

        for point in self.points:
            elevations.append(point.elevation)
            latitudes.append(point.latitude)
            longitudes.append(point.longitude)

        avg_distance = 0
        avg_elevation_delta = 1
        if remove_extremes:
            # compute the average distance between two points:
            distances = []
            elevations_delta = []
            for i in range(len(self.points))[1:]:
                distances.append(self.points[i].distance_2d(self.points[i - 1]))
                elevation_1 = self.points[i].elevation
                elevation_2 = self.points[i - 1].elevation
                if elevation_1 is not None and elevation_2 is not None:
                    elevations_delta.append(abs(elevation_1 - elevation_2))
            if distances:
                avg_distance = 1.0 * sum(distances) / len(distances)
            if elevations_delta:
                avg_elevation_delta = 1.0 * sum(elevations_delta) / len(elevations_delta)

        # If The point moved more than this number * the average distance between two
        # points -- then is a candidate for deletion:
        # TODO: Make this a method parameter
        remove_2d_extremes_threshold = 1.75 * avg_distance
        remove_elevation_extremes_threshold = avg_elevation_delta * 5  # TODO: Param

        new_track_points = [self.points[0]]

        for i in range(len(self.points))[1:-1]:
            new_point = None
            point_removed = False
            if vertical and elevations[i - 1] and elevations[i] and elevations[i + 1]:
                old_elevation = self.points[i].elevation
                new_elevation = SMOOTHING_RATIO[0] * elevations[i - 1] + \
                    SMOOTHING_RATIO[1] * elevations[i] + \
                    SMOOTHING_RATIO[2] * elevations[i + 1]

                if not remove_extremes:
                    self.points[i].elevation = new_elevation

                if remove_extremes:
                    # The point must be enough distant to *both* neighbours:
                    d1 = abs(old_elevation - elevations[i - 1])
                    d2 = abs(old_elevation - elevations[i + 1])
                    #print d1, d2, remove_2d_extremes_threshold

                    # TODO: Remove extremes threshold is meant only for 2D, elevation must be
                    # computed in different way!
                    if min(d1, d2) < remove_elevation_extremes_threshold and abs(old_elevation - new_elevation) < remove_2d_extremes_threshold:
                        new_point = self.points[i]
                    else:
                        #print 'removed elevation'
                        point_removed = True
                else:
                    new_point = self.points[i]
            else:
                new_point = self.points[i]

            if horizontal:
                old_latitude = self.points[i].latitude
                new_latitude = SMOOTHING_RATIO[0] * latitudes[i - 1] + \
                    SMOOTHING_RATIO[1] * latitudes[i] + \
                    SMOOTHING_RATIO[2] * latitudes[i + 1]
                old_longitude = self.points[i].longitude
                new_longitude = SMOOTHING_RATIO[0] * longitudes[i - 1] + \
                    SMOOTHING_RATIO[1] * longitudes[i] + \
                    SMOOTHING_RATIO[2] * longitudes[i + 1]

                if not remove_extremes:
                    self.points[i].latitude = new_latitude
                    self.points[i].longitude = new_longitude

                # TODO: This is not ideal.. Because if there are points A, B and C on the same
                # line but B is very close to C... This would remove B (and possibly) A even though
                # it is not an extreme. This is the reason for this algorithm:
                d1 = mod_geo.distance(latitudes[i - 1], longitudes[i - 1], None, latitudes[i], longitudes[i], None)
                d2 = mod_geo.distance(latitudes[i + 1], longitudes[i + 1], None, latitudes[i], longitudes[i], None)
                d = mod_geo.distance(latitudes[i - 1], longitudes[i - 1], None, latitudes[i + 1], longitudes[i + 1], None)

                #print d1, d2, d, remove_extremes

                if d1 + d2 > d * 1.5 and remove_extremes:
                    d = mod_geo.distance(old_latitude, old_longitude, None, new_latitude, new_longitude, None)
                    #print "d, threshold = ", d, remove_2d_extremes_threshold
                    if d < remove_2d_extremes_threshold:
                        new_point = self.points[i]
                    else:
                        #print 'removed 2d'
                        point_removed = True
                else:
                    new_point = self.points[i]

            if new_point and not point_removed:
                new_track_points.append(new_point)

        new_track_points.append(self.points[- 1])

        #print 'len=', len(new_track_points)

        self.points = new_track_points

    def has_times(self):
        """
        Returns if points in this segment contains timestamps.

        The first point, the last point, and 75% of the points must have times
        for this method to return true.
        """
        if not self.points:
            return True
            # ... or otherwise one empty track segment would change the entire
            # track's "has_times" status!

        found = 0
        for track_point in self.points:
            if track_point.time:
                found += 1

        return len(self.points) > 2 and float(found) / float(len(self.points)) > .75

    def has_elevations(self):
        """
        Returns if points in this segment contains elevation.

        The first point, the last point, and at least 75% of the points must
        have elevation for this method to return true.
        """
        if not self.points:
            return True
            # ... or otherwise one empty track segment would change the entire
            # track's "has_times" status!

        found = 0
        for track_point in self.points:
            if track_point.elevation:
                found += 1

        return len(self.points) > 2 and float(found) / float(len(self.points)) > .75


    def __repr__(self):
        return 'GPXTrackSegment(points=[%s])' % ('...' if self.points else '')

    def clone(self):
        return mod_copy.deepcopy(self)


class GPXTrack:
    gpx_10_fields = [
            mod_gpxfield.GPXField('name'),
            mod_gpxfield.GPXField('comment', 'cmt'),
            mod_gpxfield.GPXField('description', 'desc'),
            mod_gpxfield.GPXField('source', 'src'),
            mod_gpxfield.GPXField('link', 'url'),
            mod_gpxfield.GPXField('link_text', 'urlname'),
            mod_gpxfield.GPXField('number', type=mod_gpxfield.INT_TYPE),
            mod_gpxfield.GPXComplexField('segments', tag='trkseg', classs=GPXTrackSegment, is_list=True),
    ]
    gpx_11_fields = [
            # See GPX for text field description
            mod_gpxfield.GPXField('name'),
            mod_gpxfield.GPXField('comment', 'cmt'),
            mod_gpxfield.GPXField('description', 'desc'),
            mod_gpxfield.GPXField('source', 'src'),
            'link:@link',
                mod_gpxfield.GPXField('link', attribute='href'),
                mod_gpxfield.GPXField('link_text', tag='text'),
                mod_gpxfield.GPXField('link_type', tag='type'),
            '/link',
            mod_gpxfield.GPXField('number', type=mod_gpxfield.INT_TYPE),
            mod_gpxfield.GPXField('type'),
            mod_gpxfield.GPXExtensionsField('extensions', is_list=True),
            mod_gpxfield.GPXComplexField('segments', tag='trkseg', classs=GPXTrackSegment, is_list=True),
    ]

    __slots__ = ('name', 'comment', 'description', 'source', 'link',
                 'link_text', 'number', 'segments', 'link_type', 'type',
                 'extensions')

    def __init__(self, name=None, description=None, number=None):
        self.name = name
        self.comment = None
        self.description = description
        self.source = None
        self.link = None
        self.link_text = None
        self.number = number
        self.segments = []
        self.link_type = None
        self.type = None
        self.extensions = []

    def simplify(self, max_distance=None):
        """
        Simplify using the Ramer-Douglas-Peucker algorithm: http://en.wikipedia.org/wiki/Ramer-Douglas-Peucker_algorithm
        """
        for segment in self.segments:
            segment.simplify(max_distance=max_distance)

    def reduce_points(self, min_distance):
        """
        Reduces the number of points in the track. Segment points will be
        updated in place.

        Parameters
        ----------
        min_distance : float
            The minimum separation in meters between points
        """
        for segment in self.segments:
            segment.reduce_points(min_distance)

    def adjust_time(self, delta):
        """
        Adjusts the time of all segments in the track by the specified delta

        Parameters
        ----------
        delta : datetime.timedelta
            Positive time delta will adjust time into the future
            Negative time delta will adjust time into the past
        """
        for segment in self.segments:
            segment.adjust_time(delta)

    def remove_time(self):
        """ Removes time data for all points in all segments of track. """
        for segment in self.segments:
            segment.remove_time()

    def remove_elevation(self):
        """ Removes elevation data for all points in all segments of track. """
        for segment in self.segments:
            segment.remove_elevation()

    def remove_empty(self):
        """ Removes empty segments in track """
        result = []

        for segment in self.segments:
            if len(segment.points) > 0:
                result.append(segment)

        self.segments = result

    def length_2d(self):
        """
        Computes 2-dimensional length (meters) of track (only latitude and
        longitude, no elevation). This is the sum of the 2D length of all
        segments.

        Returns
        ----------
        length : float
            Length returned in meters
        """
        length = 0
        for track_segment in self.segments:
            d = track_segment.length_2d()
            if d:
                length += d
        return length

    def get_time_bounds(self):
        """
        Gets the time bound (start and end) of the track.

        Returns
        ----------
        time_bounds : TimeBounds named tuple
            start_time : datetime
                Start time of the first segment in track
            end time : datetime
                End time of the last segment in track
        """
        start_time = None
        end_time = None

        for track_segment in self.segments:
            point_start_time, point_end_time = track_segment.get_time_bounds()
            if not start_time and point_start_time:
                start_time = point_start_time
            if point_end_time:
                end_time = point_end_time

        return TimeBounds(start_time, end_time)

    def get_bounds(self):
        """
        Gets the latitude and longitude bounds of the track.

        Returns
        ----------
        bounds : Bounds named tuple
            min_latitude : float
                Minimum latitude of track in decimal degrees [-90, 90]
            max_latitude : float
                Maximum latitude of track in decimal degrees [-90, 90]
            min_longitude : float
                Minimum longitude of track in decimal degrees [-180, 180]
            max_longitude : float
                Maximum longitude of track in decimal degrees [-180, 180]
        """
        min_lat = None
        max_lat = None
        min_lon = None
        max_lon = None
        for track_segment in self.segments:
            bounds = track_segment.get_bounds()

            if not mod_utils.is_numeric(min_lat) or (bounds.min_latitude and bounds.min_latitude < min_lat):
                min_lat = bounds.min_latitude
            if not mod_utils.is_numeric(max_lat) or (bounds.max_latitude and bounds.max_latitude > max_lat):
                max_lat = bounds.max_latitude
            if not mod_utils.is_numeric(min_lon) or (bounds.min_longitude and bounds.min_longitude < min_lon):
                min_lon = bounds.min_longitude
            if not mod_utils.is_numeric(max_lon) or (bounds.max_longitude and bounds.max_longitude > max_lon):
                max_lon = bounds.max_longitude

        return GPXBounds(min_lat, max_lat, min_lon, max_lon)

    def walk(self, only_points=False):
        """
        Generator used to iterates through track

        Parameters
        ----------
        only_point s: boolean
            Only yield points while walking

        Yields
        ----------
        point : GPXTrackPoint
            Point in the track
        segment_no : integer
            Index of segment containint point. This is suppressed if only_points
            is True.
        point_no : integer
            Index of point. This is suppressed if only_points is True.
        """
        for segment_no, segment in enumerate(self.segments):
            for point_no, point in enumerate(segment.points):
                if only_points:
                    yield point
                else:
                    yield point, segment_no, point_no

    def get_points_no(self):
        """
        Get the number of points in all segments in the track.

        Returns
        ----------
        num_points : integer
            Number of points in track
        """
        result = 0

        for track_segment in self.segments:
            result += track_segment.get_points_no()

        return result

    def length_3d(self):
        """
        Computes 3-dimensional length of track (latitude, longitude, and
        elevation). This is the sum of the 3D length of all segments.

        Returns
        ----------
        length : float
            Length returned in meters
        """
        length = 0
        for track_segment in self.segments:
            d = track_segment.length_3d()
            if d:
                length += d
        return length

    def split(self, track_segment_no, track_point_no):
        """
        Splits one of the segments in the track in two parts. If one of the
        split segments is empty it will not be added in the result. The
        segments will be split in place.

        Parameters
        ----------
        track_segment_no : integer
            The index of the segment to split
        track_point_no : integer
            The index of the track point in the segment to split
        """
        new_segments = []
        for i in range(len(self.segments)):
            segment = self.segments[i]
            if i == track_segment_no:
                segment_1, segment_2 = segment.split(track_point_no)
                if segment_1:
                    new_segments.append(segment_1)
                if segment_2:
                    new_segments.append(segment_2)
            else:
                new_segments.append(segment)
        self.segments = new_segments

    def join(self, track_segment_no, track_segment_no_2=None):
        """
        Joins two segments of this track. The segments will be split in place.

        Parameters
        ----------
        track_segment_no : integer
            The index of the first segment to join
        track_segment_no_2 : integer
            The index of second segment to join. If track_segment_no_2 is not
            provided,the join will be with the next segment after
            track_segment_no.
        """
        if not track_segment_no_2:
            track_segment_no_2 = track_segment_no + 1

        if track_segment_no_2 >= len(self.segments):
            return

        new_segments = []
        for i in range(len(self.segments)):
            segment = self.segments[i]
            if i == track_segment_no:
                second_segment = self.segments[track_segment_no_2]
                segment.join(second_segment)

                new_segments.append(segment)
            elif i == track_segment_no_2:
                # Nothing, it is already joined
                pass
            else:
                new_segments.append(segment)
        self.segments = new_segments

    def get_moving_data(self, stopped_speed_threshold=None):
        """
        Return a tuple of (moving_time, stopped_time, moving_distance,
        stopped_distance, max_speed) that may be used for detecting the time
        stopped, and max speed. Not that those values are not absolutely true,
        because the "stopped" or "moving" information aren't saved in the track.

        Because of errors in the GPS recording, it may be good to calculate
        them on a reduced and smoothed version of the track.

        Parameters
        ----------
        stopped_speed_threshold : float
            speeds (km/h) below this threshold are treated as if having no
            movement. Default is 1 km/h.

        Returns
        ----------
        moving_data : MovingData : named tuple
            moving_time : float
                time (seconds) of track in which movement was occurring
            stopped_time : float
                time (seconds) of track in which no movement was occurring
            stopped_distance : float
                distance (meters) travelled during stopped times
            moving_distance : float
                distance (meters) travelled during moving times
            max_speed : float
                Maximum speed (m/s) during the track.
        """
        moving_time = 0.
        stopped_time = 0.

        moving_distance = 0.
        stopped_distance = 0.

        max_speed = 0.

        for segment in self.segments:
            track_moving_time, track_stopped_time, track_moving_distance, track_stopped_distance, track_max_speed = segment.get_moving_data(stopped_speed_threshold)
            moving_time += track_moving_time
            stopped_time += track_stopped_time
            moving_distance += track_moving_distance
            stopped_distance += track_stopped_distance

            if track_max_speed is not None and track_max_speed > max_speed:
                max_speed = track_max_speed

        return MovingData(moving_time, stopped_time, moving_distance, stopped_distance, max_speed)

    def add_elevation(self, delta):
        """
        Adjusts elevation data for track.

        Parameters
        ----------
        delta : float
            Elevation delta in meters to apply to track
        """
        for track_segment in self.segments:
            track_segment.add_elevation(delta)

    def add_missing_data(self, get_data_function, add_missing_function):
        for track_segment in self.segments:
            track_segment.add_missing_data(get_data_function, add_missing_function)

    def move(self, location_delta):
        """
        Moves each point in the track.

        Parameters
        ----------
        location_delta: LocationDelta object
            Delta (distance/angle or lat/lon offset to apply each point in each
            segment of the track
        """
        for track_segment in self.segments:
            track_segment.move(location_delta)

    def get_duration(self):
        """
        Calculates duration or track

        Returns
        -------
        duration: float
            Duration in seconds or None if any time data is missing
        """
        if not self.segments:
            return 0

        result = 0
        for track_segment in self.segments:
            duration = track_segment.get_duration()
            if duration or duration == 0:
                result += duration
            elif duration is None:
                return None

        return result

    def get_uphill_downhill(self):
        """
        Calculates the uphill and downhill elevation climbs for the track.
        If elevation for some points is not found those are simply ignored.

        Returns
        -------
        uphill_downhill: UphillDownhill named tuple
            uphill: float
                Uphill elevation climbs in meters
            downhill: float
                Downhill elevation descent in meters
        """
        if not self.segments:
            return UphillDownhill(0, 0)

        uphill = 0
        downhill = 0

        for track_segment in self.segments:
            current_uphill, current_downhill = track_segment.get_uphill_downhill()

            uphill += current_uphill
            downhill += current_downhill

        return UphillDownhill(uphill, downhill)

    def get_location_at(self, time):
        """
        Gets approx. location at given time. Note that, at the moment this
        method returns an instance of GPXTrackPoint in the future -- this may
        be a mod_geo.Location instance with approximated latitude, longitude
        and elevation!
        """
        result = []
        for track_segment in self.segments:
            location = track_segment.get_location_at(time)
            if location:
                result.append(location)

        return result

    def get_elevation_extremes(self):
        """
        Calculate elevation extremes of track

        Returns
        -------
        min_max_elevation: MinimumMaximum named tuple
            minimum: float
                Minimum elevation in meters
            maximum: float
                Maximum elevation in meters
        """
        if not self.segments:
            return MinimumMaximum(None, None)

        elevations = []

        for track_segment in self.segments:
            (_min, _max) = track_segment.get_elevation_extremes()
            if _min is not None:
                elevations.append(_min)
            if _max is not None:
                elevations.append(_max)

        if len(elevations) == 0:
            return MinimumMaximum(None, None)

        return MinimumMaximum(min(elevations), max(elevations))

    def get_center(self):
        """
        Get the center of the route.

        Returns
        -------
        center: Location
            latitude: latitude of center in degrees
            longitude: longitude of center in degrees
            elevation: not calculated here
        """
        if not self.segments:
            return None
        sum_lat = 0
        sum_lon = 0
        n = 0
        for track_segment in self.segments:
            for point in track_segment.points:
                n += 1.
                sum_lat += point.latitude
                sum_lon += point.longitude

        if not n:
            return mod_geo.Location(float(0), float(0))

        return mod_geo.Location(latitude=sum_lat / n, longitude=sum_lon / n)

    def smooth(self, vertical=True, horizontal=False, remove_extremes=False):
        """ See: GPXTrackSegment.smooth() """
        for track_segment in self.segments:
            track_segment.smooth(vertical, horizontal, remove_extremes)

    def has_times(self):
        """ See GPXTrackSegment.has_times() """
        if not self.segments:
            return None

        result = True
        for track_segment in self.segments:
            result = result and track_segment.has_times()

        return result

    def has_elevations(self):
        """ Returns true if track data has elevation for all segments """
        if not self.segments:
            return None

        result = True
        for track_segment in self.segments:
            result = result and track_segment.has_elevations()

        return result

    def get_nearest_location(self, location):
        """ Returns (location, track_segment_no, track_point_no) for nearest location on track """
        if not self.segments:
            return None

        result = None
        distance = None
        result_track_segment_no = None
        result_track_point_no = None

        for i in range(len(self.segments)):
            track_segment = self.segments[i]
            nearest_location, track_point_no = track_segment.get_nearest_location(location)
            nearest_location_distance = None
            if nearest_location:
                nearest_location_distance = nearest_location.distance_2d(location)

            if not distance or nearest_location_distance < distance:
                if nearest_location:
                    distance = nearest_location_distance
                    result = nearest_location
                    result_track_segment_no = i
                    result_track_point_no = track_point_no

        return result, result_track_segment_no, result_track_point_no

    def clone(self):
        return mod_copy.deepcopy(self)


    def __repr__(self):
        representation = ''
        for attribute in 'name', 'description', 'number':
            value = getattr(self, attribute)
            if value is not None:
                representation += '%s%s=%s' % (', ' if representation else '', attribute, repr(value))
        representation += '%ssegments=%s' % (', ' if representation else '', repr(self.segments))
        return 'GPXTrack(%s)' % representation


class GPX:
    gpx_10_fields = [
            mod_gpxfield.GPXField('version', attribute=True),
            mod_gpxfield.GPXField('creator', attribute=True),
            mod_gpxfield.GPXField('name'),
            mod_gpxfield.GPXField('description', 'desc'),
            mod_gpxfield.GPXField('author_name', 'author'),
            mod_gpxfield.GPXField('author_email', 'email'),
            mod_gpxfield.GPXField('link', 'url'),
            mod_gpxfield.GPXField('link_text', 'urlname'),
            mod_gpxfield.GPXField('time', type=mod_gpxfield.TIME_TYPE),
            mod_gpxfield.GPXField('keywords'),
            mod_gpxfield.GPXComplexField('bounds', classs=GPXBounds),
            mod_gpxfield.GPXComplexField('waypoints', classs=GPXWaypoint, tag='wpt', is_list=True),
            mod_gpxfield.GPXComplexField('routes', classs=GPXRoute, tag='rte', is_list=True),
            mod_gpxfield.GPXComplexField('tracks', classs=GPXTrack, tag='trk', is_list=True),
    ]
    # Text fields serialize as empty container tags, dependents are
    # are listed after as 'tag:dep1:dep2:dep3'. If no dependents are
    # listed, it will always serialize. The container is closed with
    # '/tag'. Required dependents are preceded by an @. If a required
    # dependent is empty, nothing in the container will serialize. The
    # format is 'tag:@dep2'. No optional dependents need to be listed.
    # Extensions not yet supported
    gpx_11_fields = [
            mod_gpxfield.GPXField('version', attribute=True),
            mod_gpxfield.GPXField('creator', attribute=True),
            'metadata:name:description:author_name:author_email:author_link:copyright_author:copyright_year:copyright_license:link:time:keywords:bounds',
                mod_gpxfield.GPXField('name', 'name'),
                mod_gpxfield.GPXField('description', 'desc'),
                'author:author_name:author_email:author_link',
                    mod_gpxfield.GPXField('author_name', 'name'),
                    mod_gpxfield.GPXEmailField('author_email', 'email'),
                    'link:@author_link',
                        mod_gpxfield.GPXField('author_link', attribute='href'),
                        mod_gpxfield.GPXField('author_link_text', tag='text'),
                        mod_gpxfield.GPXField('author_link_type', tag='type'),
                    '/link',
                '/author',
                'copyright:copyright_author:copyright_year:copyright_license',
                    mod_gpxfield.GPXField('copyright_author', attribute='author'),
                    mod_gpxfield.GPXField('copyright_year', tag='year'),
                    mod_gpxfield.GPXField('copyright_license', tag='license'),
                '/copyright',
                'link:@link',
                    mod_gpxfield.GPXField('link', attribute='href'),
                    mod_gpxfield.GPXField('link_text', tag='text'),
                    mod_gpxfield.GPXField('link_type', tag='type'),
                '/link',
                mod_gpxfield.GPXField('time', type=mod_gpxfield.TIME_TYPE),
                mod_gpxfield.GPXField('keywords'),
                mod_gpxfield.GPXComplexField('bounds', classs=GPXBounds),
                mod_gpxfield.GPXExtensionsField('metadata_extensions', tag='extensions'),
            '/metadata',
            mod_gpxfield.GPXComplexField('waypoints', classs=GPXWaypoint, tag='wpt', is_list=True),
            mod_gpxfield.GPXComplexField('routes', classs=GPXRoute, tag='rte', is_list=True),
            mod_gpxfield.GPXComplexField('tracks', classs=GPXTrack, tag='trk', is_list=True),
            mod_gpxfield.GPXExtensionsField('extensions', is_list=True),
    ]

    __slots__ = ('version', 'creator', 'name', 'description', 'author_name',
                 'author_email', 'link', 'link_text', 'time', 'keywords',
                 'bounds', 'waypoints', 'routes', 'tracks', 'author_link',
                 'author_link_text', 'author_link_type', 'copyright_author',
                 'copyright_year', 'copyright_license', 'link_type',
                 'metadata_extensions', 'extensions', 'nsmap',
                 'schema_locations')

    def __init__(self):
        self.version = None
        self.creator = None
        self.name = None
        self.description = None
        self.link = None
        self.link_text = None
        self.link_type = None
        self.time = None
        self.keywords = None
        self.bounds = None
        self.author_name = None
        self.author_email = None
        self.author_link = None
        self.author_link_text = None
        self.author_link_type = None
        self.copyright_author = None
        self.copyright_year = None
        self.copyright_license = None
        self.metadata_extensions = []
        self.extensions = []
        self.waypoints = []
        self.routes = []
        self.tracks = []
        self.nsmap = {}
        self.schema_locations = []

    def simplify(self, max_distance=None):
        """
        Simplify using the Ramer-Douglas-Peucker algorithm: http://en.wikipedia.org/wiki/Ramer-Douglas-Peucker_algorithm
        """
        for track in self.tracks:
            track.simplify(max_distance=max_distance)

    def reduce_points(self, max_points_no=None, min_distance=None):
        """
        Reduces the number of points. Points will be updated in place.

        Parameters
        ----------

        max_points : int
            The maximum number of points to include in the GPX
        min_distance : float
            The minimum separation in meters between points
        """
        if max_points_no is None and min_distance is None:
            raise ValueError("Either max_point_no or min_distance must be supplied")

        if max_points_no is not None and max_points_no < 2:
            raise ValueError("max_points_no must be greater than or equal to 2")

        points_no = len(list(self.walk()))
        if max_points_no is not None and points_no <= max_points_no:
            # No need to reduce points only if no min_distance is specified:
            if not min_distance:
                return

        length = self.length_3d()

        min_distance = min_distance or 0
        max_points_no = max_points_no or 1000000000

        min_distance = max(min_distance, mod_math.ceil(length / float(max_points_no)))

        for track in self.tracks:
            track.reduce_points(min_distance)

        # TODO
        log.debug('Track reduced to %s points' % self.get_track_points_no())

    def adjust_time(self, delta, all=False):
        """
        Adjusts the time of all points in all of the segments of all tracks by
        the specified delta.

        If all=True, waypoints and routes will also be adjusted by the specified delta.

        Parameters
        ----------
        delta : datetime.timedelta
            Positive time delta will adjust times into the future
            Negative time delta will adjust times into the past
        all : bool
            When true, also adjusts time for waypoints and routes.
        """
        if self.time:
            self.time += delta
        for track in self.tracks:
            track.adjust_time(delta)

        if all:
            for waypoint in self.waypoints:
                waypoint.adjust_time(delta)
            for route in self.routes:
                route.adjust_time(delta)

    def remove_time(self, all=False):
        """
        Removes time data of all points in all of the segments of all tracks.

        If all=True, time date will also be removed from waypoints and routes.

        Parameters
        ----------
        all : bool
            When true, also removes time data for waypoints and routes.
        """
        for track in self.tracks:
            track.remove_time()

        if all:
            for waypoint in self.waypoints:
                waypoint.remove_time()
            for route in self.routes:
                route.remove_time()

    def remove_elevation(self, tracks=True, routes=False, waypoints=False):
        """ Removes elevation data. """
        if tracks:
            for track in self.tracks:
                track.remove_elevation()
        if routes:
            for route in self.routes:
                route.remove_elevation()
        if waypoints:
            for waypoint in self.waypoints:
                waypoint.remove_elevation()

    def get_time_bounds(self):
        """
        Gets the time bounds (start and end) of the GPX file.

        Returns
        ----------
        time_bounds : TimeBounds named tuple
            start_time : datetime
                Start time of the first segment in track
            end time : datetime
                End time of the last segment in track
        """
        start_time = None
        end_time = None

        for track in self.tracks:
            track_start_time, track_end_time = track.get_time_bounds()
            if not start_time:
                start_time = track_start_time
            if track_end_time:
                end_time = track_end_time

        return TimeBounds(start_time, end_time)

    def get_bounds(self):
        """
        Gets the latitude and longitude bounds of the GPX file.

        Returns
        ----------
        bounds : Bounds named tuple
            min_latitude : float
                Minimum latitude of track in decimal degrees [-90, 90]
            max_latitude : float
                Maximum latitude of track in decimal degrees [-90, 90]
            min_longitude : float
                Minimum longitude of track in decimal degrees [-180, 180]
            max_longitude : float
                Maximum longitude of track in decimal degrees [-180, 180]
        """
        min_lat = None
        max_lat = None
        min_lon = None
        max_lon = None
        for track in self.tracks:
            bounds = track.get_bounds()

            if not mod_utils.is_numeric(min_lat) or bounds.min_latitude < min_lat:
                min_lat = bounds.min_latitude
            if not mod_utils.is_numeric(max_lat) or bounds.max_latitude > max_lat:
                max_lat = bounds.max_latitude
            if not mod_utils.is_numeric(min_lon) or bounds.min_longitude < min_lon:
                min_lon = bounds.min_longitude
            if not mod_utils.is_numeric(max_lon) or bounds.max_longitude > max_lon:
                max_lon = bounds.max_longitude

        return GPXBounds(min_lat, max_lat, min_lon, max_lon)

    def get_points_no(self):
        """
        Get the number of points in all segments of all track.

        Returns
        ----------
        num_points : integer
            Number of points in GPX
        """
        result = 0
        for track in self.tracks:
            result += track.get_points_no()
        return result

    def refresh_bounds(self):
        """
        Compute bounds and reload min_latitude, max_latitude, min_longitude
        and max_longitude properties of this object
        """

        bounds = self.get_bounds()

        self.bounds = bounds

    def smooth(self, vertical=True, horizontal=False, remove_extremes=False):
        """ See GPXTrackSegment.smooth(...) """
        for track in self.tracks:
            track.smooth(vertical=vertical, horizontal=horizontal, remove_extremes=remove_extremes)

    def remove_empty(self):
        """ Removes segments, routes """

        routes = []

        for route in self.routes:
            if len(route.points) > 0:
                routes.append(route)

        self.routes = routes

        for track in self.tracks:
            track.remove_empty()

    def get_moving_data(self, stopped_speed_threshold=None):
        """
        Return a tuple of (moving_time, stopped_time, moving_distance, stopped_distance, max_speed)
        that may be used for detecting the time stopped, and max speed. Not that those values are not
        absolutely true, because the "stopped" or "moving" information aren't saved in the track.

        Because of errors in the GPS recording, it may be good to calculate them on a reduced and
        smoothed version of the track. Something like this:

        cloned_gpx = gpx.clone()
        cloned_gpx.reduce_points(2000, min_distance=10)
        cloned_gpx.smooth(vertical=True, horizontal=True)
        cloned_gpx.smooth(vertical=True, horizontal=False)
        moving_time, stopped_time, moving_distance, stopped_distance, max_speed_ms = cloned_gpx.get_moving_data
        max_speed_kmh = max_speed_ms * 60. ** 2 / 1000.

        Experiment with your own variations to get the values you expect.

        Max speed is in m/s.
        """
        moving_time = 0.
        stopped_time = 0.

        moving_distance = 0.
        stopped_distance = 0.

        max_speed = 0.

        for track in self.tracks:
            track_moving_time, track_stopped_time, track_moving_distance, track_stopped_distance, track_max_speed = track.get_moving_data(stopped_speed_threshold)
            moving_time += track_moving_time
            stopped_time += track_stopped_time
            moving_distance += track_moving_distance
            stopped_distance += track_stopped_distance

            if track_max_speed > max_speed:
                max_speed = track_max_speed

        return MovingData(moving_time, stopped_time, moving_distance, stopped_distance, max_speed)

    def split(self, track_no, track_segment_no, track_point_no):
        """
        Splits one of the segments of a track in two parts. If one of the
        split segments is empty it will not be added in the result. The
        segments will be split in place.

        Parameters
        ----------
        track_no : integer
            The index of the track to split
        track_segment_no : integer
            The index of the segment to split
        track_point_no : integer
            The index of the track point in the segment to split
        """
        track = self.tracks[track_no]

        track.split(track_segment_no=track_segment_no, track_point_no=track_point_no)

    def length_2d(self):
        """
        Computes 2-dimensional length of the GPX file (only latitude and
        longitude, no elevation). This is the sum of 2D length of all segments
        in all tracks.

        Returns
        ----------
        length : float
            Length returned in meters
        """
        result = 0
        for track in self.tracks:
            length = track.length_2d()
            if length:
                result += length
        return result

    def length_3d(self):
        """
        Computes 3-dimensional length of the GPX file (latitude, longitude, and
        elevation). This is the sum of 3D length of all segments in all tracks.

        Returns
        ----------
        length : float
            Length returned in meters
        """
        result = 0
        for track in self.tracks:
            length = track.length_3d()
            if length:
                result += length
        return result

    def walk(self, only_points=False):
        """
        Generator used to iterates through points in GPX file

        Parameters
        ----------
        only_point s: boolean
            Only yield points while walking

        Yields
        ----------
        point : GPXTrackPoint
            Point in the track
        track_no : integer
            Index of track containint point. This is suppressed if only_points
            is True.
        segment_no : integer
            Index of segment containint point. This is suppressed if only_points
            is True.
        point_no : integer
            Index of point. This is suppressed if only_points is True.
        """
        for track_no, track in enumerate(self.tracks):
            for segment_no, segment in enumerate(track.segments):
                for point_no, point in enumerate(segment.points):
                    if only_points:
                        yield point
                    else:
                        yield point, track_no, segment_no, point_no

    def get_track_points_no(self):
        """ Number of track points, *without* route and waypoints """
        result = 0

        for track in self.tracks:
            for segment in track.segments:
                result += len(segment.points)

        return result

    def get_duration(self):
        """
        Calculates duration of GPX file

        Returns
        -------
        duration: float
            Duration in seconds or None if time data is not fully populated.
        """
        if not self.tracks:
            return 0

        result = 0
        for track in self.tracks:
            duration = track.get_duration()
            if duration or duration == 0:
                result += duration
            elif duration is None:
                return None

        return result

    def get_uphill_downhill(self):
        """
        Calculates the uphill and downhill elevation climbs for the gpx file.
        If elevation for some points is not found those are simply ignored.

        Returns
        -------
        uphill_downhill: UphillDownhill named tuple
            uphill: float
                Uphill elevation climbs in meters
            downhill: float
                Downhill elevation descent in meters
        """
        if not self.tracks:
            return UphillDownhill(0, 0)

        uphill = 0
        downhill = 0

        for track in self.tracks:
            current_uphill, current_downhill = track.get_uphill_downhill()

            uphill += current_uphill
            downhill += current_downhill

        return UphillDownhill(uphill, downhill)

    def get_location_at(self, time):
        """
        Gets approx. location at given time. Note that, at the moment this
        method returns an instance of GPXTrackPoint in the future -- this may
        be a mod_geo.Location instance with approximated latitude, longitude
        and elevation!
        """
        result = []
        for track in self.tracks:
            locations = track.get_location_at(time)
            for location in locations:
                result.append(location)

        return result

    def get_elevation_extremes(self):
        """
        Calculate elevation extremes of GPX file

        Returns
        -------
        min_max_elevation: MinimumMaximum named tuple
            minimum: float
                Minimum elevation in meters
            maximum: float
                Maximum elevation in meters
        """
        if not self.tracks:
            return MinimumMaximum(None, None)

        elevations = []

        for track in self.tracks:
            (_min, _max) = track.get_elevation_extremes()
            if _min is not None:
                elevations.append(_min)
            if _max is not None:
                elevations.append(_max)

        if len(elevations) == 0:
            return MinimumMaximum(None, None)

        return MinimumMaximum(min(elevations), max(elevations))

    def get_points_data(self, distance_2d=False):
        """
        Returns a list of tuples containing the actual point, its distance from the start,
        track_no, segment_no, and segment_point_no
        """
        distance_from_start = 0
        previous_point = None

        # (point, distance_from_start) pairs:
        points = []

        for track_no in range(len(self.tracks)):
            track = self.tracks[track_no]
            for segment_no in range(len(track.segments)):
                segment = track.segments[segment_no]
                for point_no in range(len(segment.points)):
                    point = segment.points[point_no]
                    if previous_point and point_no > 0:
                        if distance_2d:
                            distance = point.distance_2d(previous_point)
                        else:
                            distance = point.distance_3d(previous_point)

                        distance_from_start += distance

                    points.append(PointData(point, distance_from_start, track_no, segment_no, point_no))

                    previous_point = point

        return points

    def get_nearest_locations(self, location, threshold_distance=0.01):
        """
        Returns a list of locations of elements like
        consisting of points where the location may be on the track

        threshold_distance is the minimum distance from the track
        so that the point *may* be counted as to be "on the track".
        For example 0.01 means 1% of the track distance.
        """

        assert location
        assert threshold_distance

        result = []

        points = self.get_points_data()

        if not points:
            return ()

        distance = points[- 1][1]

        threshold = distance * threshold_distance

        min_distance_candidate = None
        distance_from_start_candidate = None
        track_no_candidate = None
        segment_no_candidate = None
        point_no_candidate = None

        for point, distance_from_start, track_no, segment_no, point_no in points:
            distance = location.distance_3d(point)
            if distance < threshold:
                if min_distance_candidate is None or distance < min_distance_candidate:
                    min_distance_candidate = distance
                    distance_from_start_candidate = distance_from_start
                    track_no_candidate = track_no
                    segment_no_candidate = segment_no
                    point_no_candidate = point_no
            else:
                if distance_from_start_candidate is not None:
                    result.append((distance_from_start_candidate, track_no_candidate, segment_no_candidate, point_no_candidate))
                min_distance_candidate = None
                distance_from_start_candidate = None
                track_no_candidate = None
                segment_no_candidate = None
                point_no_candidate = None

        if distance_from_start_candidate is not None:
            result.append(NearestLocationData(distance_from_start_candidate, track_no_candidate, segment_no_candidate, point_no_candidate))

        return result

    def get_nearest_location(self, location):
        """ Returns (location, track_no, track_segment_no, track_point_no) for the
        nearest location on map """
        if not self.tracks:
            return None

        result = None
        distance = None
        result_track_no = None
        result_segment_no = None
        result_point_no = None
        for i in range(len(self.tracks)):
            track = self.tracks[i]
            nearest_location, track_segment_no, track_point_no = track.get_nearest_location(location)
            nearest_location_distance = None
            if nearest_location:
                nearest_location_distance = nearest_location.distance_2d(location)
            if not distance or nearest_location_distance < distance:
                result = nearest_location
                distance = nearest_location_distance
                result_track_no = i
                result_segment_no = track_segment_no
                result_point_no = track_point_no

        return NearestLocationData(result, result_track_no, result_segment_no, result_point_no)

    def add_elevation(self, delta):
        """
        Adjusts elevation data of GPX data.

        Parameters
        ----------
        delta : float
            Elevation delta in meters to apply to GPX data
        """
        for track in self.tracks:
            track.add_elevation(delta)

    def add_missing_data(self, get_data_function, add_missing_function):
        for track in self.tracks:
            track.add_missing_data(get_data_function, add_missing_function)

    def add_missing_elevations(self):
        def _add(interval, start, end, distances_ratios):
            if (start.elevation is None) or (end.elevation is None):
                return
            assert start
            assert end
            assert interval
            assert len(interval) == len(distances_ratios)
            for i in range(len(interval)):
                interval[i].elevation = start.elevation + distances_ratios[i] * (end.elevation - start.elevation)

        self.add_missing_data(get_data_function=lambda point: point.elevation,
                              add_missing_function=_add)

    def add_missing_times(self):
        def _add(interval, start, end, distances_ratios):
            if (not start) or (not end) or (not start.time) or (not end.time):
                return
            assert interval
            assert len(interval) == len(distances_ratios)

            seconds_between = float(mod_utils.total_seconds(end.time - start.time))

            for i in range(len(interval)):
                point = interval[i]
                ratio = distances_ratios[i]
                point.time = start.time + mod_datetime.timedelta(
                    seconds=ratio * seconds_between)

        self.add_missing_data(get_data_function=lambda point: point.time,
                              add_missing_function=_add)

    def add_missing_speeds(self):
        """
        The missing speeds are added to a segment.

        The weighted harmonic mean is used to approximate the speed at
        a :obj:'~.GPXTrackPoint'.
        For this to work the speed of the first and last track point in a
        segment needs to be known.
        """
        def _add(interval, start, end, distances_ratios):
            if (not start) or (not end) or (not start.time) or (not end.time):
                return
            assert interval
            assert len(interval) == len(distances_ratios)

            time_dist_before = (interval[0].time_difference(start),
                                interval[0].distance_3d(start))
            time_dist_after = (interval[-1].time_difference(end),
                               interval[-1].distance_3d(end))

            # Assemble list of times and distance to neighbour points
            times_dists = [(interval[i].time_difference(interval[i+1]),
                            interval[i].distance_3d(interval[i+1]))
                            for i in range(len(interval) - 1)]
            times_dists.insert(0, time_dist_before)
            times_dists.append(time_dist_after)

            for i, point in enumerate(interval):
                time_left, dist_left = times_dists[i]
                time_right, dist_right = times_dists[i+1]
                point.speed = float(dist_left + dist_right) / (time_left + time_right)

        self.add_missing_data(get_data_function=lambda point: point.speed,
                              add_missing_function=_add)

    def fill_time_data_with_regular_intervals(self, start_time=None, time_delta=None, end_time=None, force=True):
        """
        Fills the time data for all points in the GPX file. At least two of the parameters start_time, time_delta, and
        end_time have to be provided. If the three are provided, time_delta will be ignored and will be recalculated
        using start_time and end_time.

        The first GPX point will have a time equal to start_time. Then points are assumed to be recorded at regular
        intervals time_delta.

        If the GPX file currently contains time data, it will be overwritten, unless the force flag is set to False, in
        which case the function will return a GPXException error.

        Parameters
        ----------
        start_time: datetime.datetime object
            Start time of the GPX file (corresponds to the time of the first point)
        time_delta: datetime.timedelta object
            Time interval between two points in the GPX file
        end_time: datetime.datetime object
            End time of the GPX file (corresponds to the time of the last point)
        force: bool
            Overwrite current data if the GPX file currently contains time data
        """
        if not (start_time and end_time) and not (start_time and time_delta) and not (time_delta and end_time):
            raise GPXException('You must provide at least two parameters among start_time, time_step, and end_time')

        if self.has_times() and not force:
            raise GPXException('GPX file currently contains time data. Use force=True to overwrite.')

        point_no = self.get_points_no()

        if start_time and end_time:
            if start_time > end_time:
                raise GPXException('Invalid parameters: end_time must occur after start_time')
            time_delta = (end_time - start_time) / (point_no - 1)
        elif not start_time:
            start_time = end_time - (point_no - 1) * time_delta

        self.time = start_time

        i = 0
        for point in self.walk(only_points=True):
            if i == 0:
                point.time = start_time
            else:
                point.time = start_time + i * time_delta
            i += 1

    def move(self, location_delta):
        """
        Moves each point in the gpx file (routes, waypoints, tracks).

        Parameters
        ----------
        location_delta: LocationDelta
            LocationDelta to move each point
        """
        for route in self.routes:
            route.move(location_delta)

        for waypoint in self.waypoints:
            waypoint.move(location_delta)

        for track in self.tracks:
            track.move(location_delta)

    def to_xml(self, version=None, prettyprint=True):
        """
        FIXME: Note, this method will change self.version
        """
        if not version:
            if self.version:
                version = self.version
            else:
                version = '1.1'

        if version != '1.0' and version != '1.1':
            raise GPXException('Invalid version %s' % version)

        self.version = version
        if not self.creator:
            self.creator = 'gpx.py -- https://github.com/tkrajina/gpxpy'

        self.nsmap['xsi'] = 'http://www.w3.org/2001/XMLSchema-instance'

        version_path = version.replace('.', '/')

        self.nsmap['defaultns'] = 'http://www.topografix.com/GPX/{0}'.format(
            version_path
        )

        if not self.schema_locations:
            self.schema_locations = [
                p.format(version_path) for p in (
                    'http://www.topografix.com/GPX/{0}',
                    'http://www.topografix.com/GPX/{0}/gpx.xsd',
                )
            ]

        content = mod_gpxfield.gpx_fields_to_xml(
            self, 'gpx', version,
            custom_attributes={
                'xsi:schemaLocation': ' '.join(self.schema_locations)
            },
            nsmap=self.nsmap,
            prettyprint=prettyprint
        )

        return '<?xml version="1.0" encoding="UTF-8"?>\n' + content.strip()

    def has_times(self):
        """ See GPXTrackSegment.has_times() """
        if not self.tracks:
            return None

        result = True
        for track in self.tracks:
            result = result and track.has_times()

        return result

    def has_elevations(self):
        """ See GPXTrackSegment.has_elevations()) """
        if not self.tracks:
            return None

        result = True
        for track in self.tracks:
            result = result and track.has_elevations()

        return result

    def __repr__(self):
        representation = ''
        for attribute in 'waypoints', 'routes', 'tracks':
            value = getattr(self, attribute)
            if value:
                representation += '%s%s=%s' % (', ' if representation else '', attribute, repr(value))
        return 'GPX(%s)' % representation

    def clone(self):
        return mod_copy.deepcopy(self)

# Add attributes and fill default values (lists or None) for all GPX elements:
for var_name in dir():
    var_value = vars()[var_name]
    if hasattr(var_value, 'gpx_10_fields') or hasattr(var_value, 'gpx_11_fields'):
        #print('Check/fill %s' % var_value)
        mod_gpxfield.gpx_check_slots_and_default_values(var_value)
