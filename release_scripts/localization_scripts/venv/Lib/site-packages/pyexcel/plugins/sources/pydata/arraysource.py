"""
    pyexcel.plugins.sources.pydata.arraysource
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of array source

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.source import AbstractSource, MemorySourceMixin
from pyexcel.constants import DEFAULT_SHEET_NAME
from pyexcel.plugins.sources import params

from .common import ArrayReader, _FakeIO


class ArraySource(AbstractSource, MemorySourceMixin):
    """
    A two dimensional array as sheet source
    """

    def __init__(self, array, sheet_name=DEFAULT_SHEET_NAME, **keywords):
        self.__array = array
        self._content = _FakeIO()
        self.__sheet_name = sheet_name
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        array_reader = ArrayReader(self.__array, **self._keywords)
        return {self.__sheet_name: array_reader.to_array()}

    def get_source_info(self):
        return params.ARRAY, None

    def write_data(self, sheet):
        self._content.setvalue(sheet.to_array())
