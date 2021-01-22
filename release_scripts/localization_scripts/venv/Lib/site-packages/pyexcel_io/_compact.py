"""
    pyexcel_io._compact
    ~~~~~~~~~~~~~~~~~~~

    Compatibles

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import sys
import logging
from io import BytesIO, StringIO  # noqa: F401
from collections import OrderedDict  # noqa: F401

try:
    from logging import NullHandler
except ImportError:

    class NullHandler(logging.Handler):
        def emit(self, record):
            pass


text_type = str
irange = range
PY2 = sys.version[0] == 2


def isstream(instance):
    """ check if a instance is a stream """
    try:
        import mmap

        i_am_not_mmap_obj = not isinstance(instance, mmap.mmap)
    except ImportError:
        # Python 2.6 or Google App Engine
        i_am_not_mmap_obj = True

    return hasattr(instance, "read") and i_am_not_mmap_obj


def is_string(atype):
    """find out if a type is str or not"""
    return atype == str
