"""
    pyexcel.internal.sheets._shared
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Locally shared utility functions

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import re
import types
from functools import partial

from pyexcel._compact import PY2

from .formatters import to_format


class CommonPropertyAmongRowNColumn(object):
    """
    Group reusable functions from row and column
    """

    def __init__(self, matrix):
        self._ref = matrix

    def __iadd__(self, other):
        raise NotImplementedError("Not implemented")

    def __add__(self, other):
        """Overload + sign

        :return: self
        """
        self.__iadd__(other)
        return self._ref

    @staticmethod
    def get_converter(theformatter):
        """return the actual converter or a built-in converter"""
        converter = None
        if isinstance(theformatter, types.FunctionType):
            converter = theformatter
        else:
            converter = partial(to_format, theformatter)
        return converter


def analyse_slice(aslice, upper_bound):
    """An internal function to analyze a given slice"""
    if aslice.start is None:
        start = 0
    else:
        start = max(aslice.start, 0)
    if aslice.stop is None:
        stop = upper_bound
    else:
        stop = min(aslice.stop, upper_bound)
    if start > stop:
        raise ValueError
    elif start < stop:
        if aslice.step:
            my_range = range(start, stop, aslice.step)
        else:
            my_range = range(start, stop)
        if not PY2:
            # for py3, my_range is a range object
            my_range = list(my_range)
    else:
        my_range = [start]
    return my_range


def excel_column_index(index_chars):
    """translate MS excel column position to index"""
    if len(index_chars) < 1:
        return -1
    else:
        return _get_index(index_chars.upper())


def excel_cell_position(pos_chars):
    """translate MS excel position to index"""
    if len(pos_chars) < 2:
        return -1, -1
    group = re.match("([A-Za-z]+)([0-9]+)", pos_chars)
    if group:
        return int(group.group(2)) - 1, excel_column_index(group.group(1))
    else:
        raise IndexError


"""
In order to easily compute the actual index of 'X' or 'AX', these utility
functions were written
"""
_INDICES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def _get_index(index_chars):
    length = len(index_chars)
    index_chars_length = len(_INDICES)
    if length > 1:
        index = 0
        for i in range(0, length):
            if i < (length - 1):
                index += (_INDICES.index(index_chars[i]) + 1) * (
                    index_chars_length ** (length - 1 - i)
                )
            else:
                index += _INDICES.index(index_chars[i])
        return index
    else:
        return _INDICES.index(index_chars[0])


def names_to_indices(names, series):
    """translate names to indices"""
    if isinstance(names, str):
        indices = series.index(names)
    elif isinstance(names, list) and isinstance(names[0], str):
        # translate each row name to index
        indices = [series.index(astr) for astr in names]
    else:
        return names
    return indices


def abs(value):
    if value < 0:
        return value * -1

    else:
        return value
