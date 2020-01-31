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

import sys as mod_sys
import math as mod_math
import xml.sax.saxutils as mod_saxutils

PYTHON_VERSION = mod_sys.version.split(' ')[0]


def to_xml(tag, attributes=None, content=None, default=None, escape=False):
    attributes = attributes or {}
    result = '\n<%s' % tag

    if content is None and default:
        content = default

    if attributes:
        for attribute in attributes.keys():
            result += make_str(' %s="%s"' % (attribute, attributes[attribute]))

    if content is None:
        result += '/>'
    else:
        if escape:
            result += make_str('>%s</%s>' % (mod_saxutils.escape(content), tag))
        else:
            result += make_str('>%s</%s>' % (content, tag))

    result = make_str(result)

    return result


def is_numeric(object):
    try:
        float(object)
        return True
    except TypeError:
        return False
    except ValueError:
        return False


def to_number(s, default=0, nan_value=None):
    try:
        result = float(s)
        if mod_math.isnan(result):
            return nan_value
        return result
    except TypeError:
        pass
    except ValueError:
        pass
    return default


def total_seconds(timedelta):
    """ Some versions of python dont have timedelta.total_seconds() method. """
    if timedelta is None:
        return None
    return (timedelta.days * 86400) + timedelta.seconds

# Hash utilities:


def __hash(obj):
    result = 0

    if obj is None:
        return result
    elif isinstance(obj, dict):
        raise RuntimeError('__hash_single_object for dict not yet implemented')
    elif isinstance(obj, list) or isinstance(obj, tuple):
        return hash_list_or_tuple(obj)

    return hash(obj)


def hash_list_or_tuple(iteration):
    result = 17

    for obj in iteration:
        result = result * 31 + __hash(obj)

    return result


def hash_object(obj, attributes):
    result = 19

    for attribute in attributes:
        result = result * 31 + __hash(getattr(obj, attribute))

    return result


def make_str(s):
    """ Convert a str or unicode object into a str type. """
    if PYTHON_VERSION[0] == '2':
        if isinstance(s, unicode):
            return s.encode("utf-8")
    return str(s)
