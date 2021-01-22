"""
    pyexcel_io.writers.csv_sheet
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    The lower level csv file format writer

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import csv

import pyexcel_io.constants as constants
from pyexcel_io.plugin_api import ISheetWriter


class CSVFileWriter(ISheetWriter):
    """
    csv file writer

    """

    def __init__(
        self,
        filename,
        name,
        encoding="utf-8",
        single_sheet_in_book=False,
        sheet_index=None,
        **keywords
    ):
        self._encoding = encoding
        self._sheet_name = name
        if self._sheet_name is None or single_sheet_in_book:
            self._sheet_name = constants.DEFAULT_SHEET_NAME
        self._single_sheet_in_book = single_sheet_in_book
        self.__line_terminator = constants.DEFAULT_CSV_NEWLINE
        self._keywords = keywords
        if constants.KEYWORD_LINE_TERMINATOR in keywords:
            self.__line_terminator = keywords.get(
                constants.KEYWORD_LINE_TERMINATOR
            )
        self._sheet_index = sheet_index
        self.file_handle = None
        self._native_book = filename

        self.writer = self.get_writer()

    def get_writer(self):
        if self._sheet_name != constants.DEFAULT_SHEET_NAME:
            names = self._native_book.split(".")
            file_name = "%s%s%s%s%s.%s" % (
                names[0],
                constants.DEFAULT_MULTI_CSV_SEPARATOR,
                self._sheet_name,  # sheet name
                constants.DEFAULT_MULTI_CSV_SEPARATOR,
                self._sheet_index,  # sheet index
                names[1],
            )
        else:
            file_name = self._native_book

        self.file_handle = open(
            file_name, "w", newline="", encoding=self._encoding
        )
        return csv.writer(self.file_handle, **self._keywords)

    def write_row(self, array):
        """
        write a row into the file
        """
        self.writer.writerow(array)

    def close(self):
        self.file_handle.close()


class CSVMemoryWriter(CSVFileWriter):
    """ Write csv to a memory stream """

    def get_writer(self):
        self.file_handle = self._native_book
        writer = csv.writer(self.file_handle, **self._keywords)
        if not self._single_sheet_in_book:
            writer.writerow(
                [
                    constants.DEFAULT_CSV_STREAM_FILE_FORMATTER
                    % (self._sheet_name, "")
                ]
            )
        return writer

    def close(self):
        if self._single_sheet_in_book:
            #  on purpose, the this is not done
            #  because the io stream can be used later
            pass
        else:
            self.writer.writerow([constants.SEPARATOR_FORMATTER % ""])
