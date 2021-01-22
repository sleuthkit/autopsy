"""
    pyexcel_io.fileformat.csvz
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~

    The lower level csvz file format handler.

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import zipfile
from io import BytesIO

import chardet
from pyexcel_io import constants
from pyexcel_io.sheet import NamedContent
from pyexcel_io._compact import StringIO
from pyexcel_io.readers.csv_sheet import CSVinMemoryReader
from pyexcel_io.plugin_api.abstract_reader import IReader


class FileReader(IReader):
    def __init__(self, file_alike_object, file_type, **keywords):
        self.content_array = []
        try:
            self.zipfile = zipfile.ZipFile(file_alike_object, "r")
            sheets = [
                NamedContent(_get_sheet_name(name), name)
                for name in self.zipfile.namelist()
            ]
            self.content_array = sheets
            self.keywords = keywords
            if file_type == constants.FILE_FORMAT_TSVZ:
                self.keywords["dialect"] = constants.KEYWORD_TSV_DIALECT

        except zipfile.BadZipfile:
            print("StringIO instance was passed by any chance?")
            raise

    def close(self):
        if self.zipfile:
            self.zipfile.close()

    def read_sheet(self, index):
        name = self.content_array[index].name
        content = self.zipfile.read(self.content_array[index].payload)
        encoding_guess = chardet.detect(content)
        sheet = StringIO(content.decode(encoding_guess["encoding"]))

        return CSVinMemoryReader(NamedContent(name, sheet), **self.keywords)


class ContentReader(FileReader):
    def __init__(self, file_content, file_type, **keywords):
        io = BytesIO(file_content)
        super().__init__(io, file_type, **keywords)


def _get_sheet_name(filename):
    len_of_a_dot = 1
    len_of_csv_word = 3
    name_len = len(filename) - len_of_a_dot - len_of_csv_word
    return filename[:name_len]
