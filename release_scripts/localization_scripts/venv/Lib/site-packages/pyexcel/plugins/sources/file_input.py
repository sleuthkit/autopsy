"""
    pyexcel.plugins.sources.file_input
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of input file sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import os

from pyexcel.source import AbstractSource
from pyexcel.internal import PARSER


# pylint: disable=W0223
class ReadExcelFromFile(AbstractSource):
    """Pick up 'file_name' field and do single sheet based read and write"""

    def __init__(self, file_name=None, parser_library=None, **keywords):
        self.__file_name = file_name

        if "force_file_type" in keywords:
            file_type = keywords.get("force_file_type")
        else:
            file_type = self.__file_name.split(".")[-1]
        self.__parser = PARSER.get_a_plugin(file_type, parser_library)
        AbstractSource.__init__(self, **keywords)

    def get_source_info(self):
        path, file_name = os.path.split(self.__file_name)
        return file_name, path

    def get_data(self):
        """
        Return a dictionary with only one key and one value
        """
        sheets = self.__parser.parse_file(self.__file_name, **self._keywords)
        return sheets
