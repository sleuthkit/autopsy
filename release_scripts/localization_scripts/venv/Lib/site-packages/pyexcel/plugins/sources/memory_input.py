"""
    pyexcel.plugins.sources.memory_input
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of input file sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.source import AbstractSource
from pyexcel.internal import PARSER

from . import params


# pylint: disable=W0223
class ReadExcelFileMemory(AbstractSource):
    """Pick up 'file_type' and read a sheet from memory"""

    def __init__(
        self,
        file_content=None,
        file_type=None,
        file_stream=None,
        parser_library=None,
        **keywords
    ):
        self.__file_type = file_type
        self.__file_stream = file_stream
        self.__file_content = file_content
        self.__parser = PARSER.get_a_plugin(file_type, parser_library)
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        if self.__file_stream is not None:
            sheets = self.__parser.parse_file_stream(
                self.__file_stream, **self._keywords
            )
        else:
            sheets = self.__parser.parse_file_content(
                self.__file_content, **self._keywords
            )
        return sheets

    def get_source_info(self):
        return params.MEMORY, None
