"""
    pyexcel.plugins.sources.sheet_stream_source
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of array source

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.source import AbstractSource, MemorySourceMixin
from pyexcel.plugins.sources import params


class SheetStreamSource(AbstractSource, MemorySourceMixin):
    """
    Internal Sheet Stream as data source
    """

    def __init__(self, sheet_stream, **keywords):
        self.__sheet_stream = sheet_stream
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        return {self.__sheet_stream.name: self.__sheet_stream.payload}

    def get_source_info(self):
        return params.SHEET_STREAM, None
