import os
import re
import glob

from pyexcel_io import constants
from pyexcel_io.sheet import NamedContent
from pyexcel_io.plugin_api import IReader
from pyexcel_io.readers.csv_sheet import CSVFileReader

DEFAULT_NEWLINE = "\r\n"


class FileReader(IReader):
    def __init__(self, file_name, file_type, **keywords):
        """Load content from a file
        :params str filename: an accessible file path
        :returns: a book
        """
        self.handles = []
        self.keywords = keywords
        if file_type == constants.FILE_FORMAT_TSV:
            self.keywords["dialect"] = constants.KEYWORD_TSV_DIALECT
        self.__line_terminator = keywords.get(
            constants.KEYWORD_LINE_TERMINATOR, DEFAULT_NEWLINE
        )
        names = os.path.splitext(file_name)
        filepattern = "%s%s*%s*%s" % (
            names[0],
            constants.DEFAULT_MULTI_CSV_SEPARATOR,
            constants.DEFAULT_MULTI_CSV_SEPARATOR,
            names[1],
        )
        filelist = glob.glob(filepattern)
        if len(filelist) == 0:
            file_parts = os.path.split(file_name)
            self.content_array = [NamedContent(file_parts[-1], file_name)]

        else:
            matcher = "%s%s(.*)%s(.*)%s" % (
                names[0],
                constants.DEFAULT_MULTI_CSV_SEPARATOR,
                constants.DEFAULT_MULTI_CSV_SEPARATOR,
                names[1],
            )
            tmp_file_list = []
            for filen in filelist:
                result = re.match(matcher, filen)
                tmp_file_list.append((result.group(1), result.group(2), filen))
            ret = []
            for lsheetname, index, filen in sorted(
                tmp_file_list, key=lambda row: row[1]
            ):
                ret.append(NamedContent(lsheetname, filen))
            self.content_array = ret

    def read_sheet(self, index):
        reader = CSVFileReader(self.content_array[index], **self.keywords)
        self.handles.append(reader)
        return reader

    def close(self):
        for reader in self.handles:
            reader.close()
        self.handles = []
