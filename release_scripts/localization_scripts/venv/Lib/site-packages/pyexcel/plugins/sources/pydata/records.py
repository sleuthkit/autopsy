"""
    pyexcel.plugins.sources.pydata.recordssource
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of records source

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.source import AbstractSource, MemorySourceMixin
from pyexcel.constants import DEFAULT_SHEET_NAME
from pyexcel.plugins.sources import params

from .common import RecordsReader, _FakeIO


class RecordsSource(AbstractSource, MemorySourceMixin):
    """
    A list of dictionaries as data source

    The dictionaries should have identical fields.
    """

    def __init__(self, records, sheet_name=DEFAULT_SHEET_NAME, **keywords):
        self.__records = records
        self._content = _FakeIO()
        self.__sheet_name = sheet_name
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        records_reader = RecordsReader(self.__records, **self._keywords)
        return {self.__sheet_name: records_reader.to_array()}

    def get_source_info(self):
        return params.RECORDS, None

    def write_data(self, sheet):
        self._content.setvalue(sheet.to_records())

    def get_content(self):
        return self._content
