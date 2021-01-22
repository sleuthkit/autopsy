"""
    pyexcel_io.database.querysets
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    The lower level handler for querysets

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import datetime
from itertools import chain

from pyexcel_io.constants import DEFAULT_SHEET_NAME
from pyexcel_io.plugin_api.abstract_sheet import ISheet


class QuerysetsReader(ISheet):
    """ turn querysets into an array """

    def __init__(self, query_sets, column_names):
        self.name = DEFAULT_SHEET_NAME
        self.__column_names = column_names
        self.__query_sets = query_sets

    def row_iterator(self):
        return chain([self.__column_names], self.__query_sets)

    def to_array(self):
        """
        Convert query sets into an array
        """
        if len(self.__query_sets) == 0:
            yield []

        for row in self.row_iterator():
            row_values = []
            for value in self.column_iterator(row):
                row_values.append(value)
            yield row_values

    def column_iterator(self, row):
        if self.__column_names is None:
            return

        if isinstance(row, list):
            for element in row:
                yield element

        else:
            for column in self.__column_names:
                if "__" in column:
                    value = get_complex_attribute(row, column)
                else:
                    value = get_simple_attribute(row, column)
                yield value


def get_complex_attribute(row, attribute):
    """ recursively get an attribute """
    attributes = attribute.split("__")
    value = row
    try:
        for attributee in attributes:
            value = get_simple_attribute(value, attributee)
    except AttributeError:
        value = None
    return value


def get_simple_attribute(row, attribute):
    """ get dotted attribute """
    value = getattr(row, attribute)
    if isinstance(value, (datetime.date, datetime.time)):
        value = value.isoformat()
    return value
