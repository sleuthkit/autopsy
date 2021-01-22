"""
    pyexcel_io.fileformat.csvz_sheet
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    The lower level csvz file format handler.

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import csv

from pyexcel_io._compact import StringIO
from pyexcel_io.writers.csv_sheet import CSVFileWriter


class CSVZipSheetWriter(CSVFileWriter):
    """ handle the zipfile interface """

    def __init__(self, zipfile, sheetname, file_extension, **keywords):
        self.file_extension = file_extension
        keywords["single_sheet_in_book"] = False
        self.content = StringIO()
        super().__init__(zipfile, sheetname, **keywords)

    def get_writer(self):
        return csv.writer(self.content, **self._keywords)

    def close(self):
        file_name = "%s.%s" % (self._sheet_name, self.file_extension)
        self.content.seek(0)
        self._native_book.writestr(file_name, self.content.read())
        self.content.close()
