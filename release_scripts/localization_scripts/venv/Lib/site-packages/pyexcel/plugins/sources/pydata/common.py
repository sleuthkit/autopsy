"""
    pyexcel.plugins.sources.pydata
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of array, dict, records and book dict sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel import constants as constants
from pyexcel._compact import PY2, OrderedDict, zip_longest

from pyexcel_io.sheet import SheetReader


class _FakeIO(object):
    """emulates a stream object"""

    def __init__(self):
        self.__value = None

    def setvalue(self, value):
        """duck method setvalue"""
        self.__value = value

    def getvalue(self):
        """duck method getvalue"""
        return self.__value


# pylint: disable=W0223
class ArrayReader(SheetReader):
    """read data from an array via pyexcel-io interface"""

    def row_iterator(self):
        for row in self._native_sheet:
            yield row

    def column_iterator(self, row):
        for cell in row:
            yield cell


# pylint: disable=W0223
class RecordsReader(ArrayReader):
    """read data from a records via pyexcel-io interface

    By default, all records are assumed to have the keys and
    the keys of the first dictionary of the records will be
    taken as a reference.

    When the keys of the first dictionary is the full list,
    The records reader will fill-in the missing key with
    default n/a, which is ''.

    Otherwise, please supply a complete list of keys as a
    parameter to get_records method, or save_as

    """

    def row_iterator(self):
        headers = self._keywords.get("custom_headers")
        for index, row in enumerate(self._native_sheet):
            if index == 0:
                if headers is None:
                    if isinstance(row, OrderedDict):
                        headers = list(row.keys())
                    else:
                        headers = sorted(row.keys())
                yield headers

            values = []
            for k in headers:
                values.append(row.get(k, constants.DEFAULT_NA))
            yield values


# pylint: disable=W0223
class DictReader(ArrayReader):
    """read data from a dictionary via pyexcel-io interface"""

    def row_iterator(self):
        keys = self._native_sheet.keys()
        if not PY2:
            keys = list(keys)
        if not isinstance(self._native_sheet, OrderedDict):
            keys = sorted(keys)
        if self._keywords.get("with_keys", True):
            yield keys

        if isinstance(self._native_sheet[keys[0]], list):
            sorted_values = (self._native_sheet[key] for key in keys)
            for row in zip_longest(
                *sorted_values, fillvalue=constants.DEFAULT_NA
            ):
                yield row
        else:
            row = [self._native_sheet[key] for key in keys]
            yield row
