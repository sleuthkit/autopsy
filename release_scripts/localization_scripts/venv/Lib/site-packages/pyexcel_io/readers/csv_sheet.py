"""
    pyexcel_io.readers.csv_sheet
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    csv file reader

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import csv

import pyexcel_io.service as service
import pyexcel_io._compact as compact
import pyexcel_io.constants as constants
from pyexcel_io.plugin_api import ISheet

DEFAULT_SEPARATOR = "__"
DEFAULT_SHEET_SEPARATOR_FORMATTER = "---%s---" % constants.DEFAULT_NAME + "%s"
SEPARATOR_MATCHER = "---%s:(.*)---" % constants.DEFAULT_NAME
DEFAULT_CSV_STREAM_FILE_FORMATTER = (
    "---%s:" % constants.DEFAULT_NAME + "%s---%s"
)
DEFAULT_NEWLINE = "\r\n"
BOM_LITTLE_ENDIAN = b"\xff\xfe"
BOM_BIG_ENDIAN = b"\xfe\ff"
LITTLE_ENDIAN = 0
BIG_ENDIAN = 1


class CSVMemoryMapIterator(object):
    """
    Wrapper class for mmap object

    mmap object does not handle encoding at all. This class
    provide the necessary transcoding for utf-8, utf-16 and utf-32
    """

    def __init__(self, mmap_obj, encoding):
        self.__mmap_obj = mmap_obj
        self.__encoding = encoding
        self.__count = 0
        self.__endian = LITTLE_ENDIAN
        if encoding == "utf-8":
            # ..\r\x00\n
            # \x00\x..
            self.__zeros_left_in_2_row = 0
        elif encoding == "utf-16":
            # ..\r\x00\n
            # \x00\x..
            self.__zeros_left_in_2_row = 1
        elif encoding == "utf-32":
            # \r\x00\x00\x00\n
            # \x00\x00\x00\x..
            self.__zeros_left_in_2_row = 3
        elif encoding in ["utf-32-be", "utf-16-be"]:
            self.__zeros_left_in_2_row = 0
            self.__endian = BIG_ENDIAN
        elif encoding == "utf-32-le":
            self.__zeros_left_in_2_row = 3
            self.__endian = LITTLE_ENDIAN
        elif encoding == "utf-16-le":
            self.__zeros_left_in_2_row = 1
            self.__endian = LITTLE_ENDIAN
        else:
            raise Exception("Encoding %s is not supported" % encoding)

    def __iter__(self):
        return self

    def __next__(self):
        line = self.__mmap_obj.readline()
        if self.__count == 0:
            utf_16_32 = (
                self.__encoding == "utf-16" or self.__encoding == "utf-32"
            )
            if utf_16_32:
                bom_header = line[:2]
                if bom_header == BOM_BIG_ENDIAN:
                    self.__endian = BIG_ENDIAN
        elif self.__endian == LITTLE_ENDIAN:
            line = line[self.__zeros_left_in_2_row :]  # noqa: E203
        if self.__endian == LITTLE_ENDIAN:
            line = line.rstrip()
        line = line.decode(self.__encoding)
        self.__count += 1
        if line == "":
            raise StopIteration

        return line

    def close(self):
        pass


class CSVSheetReader(ISheet):
    """ generic csv file reader"""

    def __init__(
        self,
        sheet,
        encoding="utf-8",
        auto_detect_float=True,
        ignore_infinity=True,
        auto_detect_int=True,
        auto_detect_datetime=True,
        pep_0515_off=True,
        ignore_nan_text=False,
        default_float_nan=None,
        **keywords
    ):
        self._native_sheet = sheet
        self._encoding = encoding
        self.__auto_detect_int = auto_detect_int
        self.__auto_detect_float = auto_detect_float
        self.__ignore_infinity = ignore_infinity
        self.__auto_detect_datetime = auto_detect_datetime
        self.__file_handle = None
        self.__pep_0515_off = pep_0515_off
        self.__ignore_nan_text = ignore_nan_text
        self.__default_float_nan = default_float_nan
        self._keywords = keywords

    def get_file_handle(self):
        """ return me unicde reader for csv """
        raise NotImplementedError("Please implement get_file_handle()")

    def row_iterator(self):
        self.__file_handle = self.get_file_handle()
        return csv.reader(self.__file_handle, **self._keywords)

    def column_iterator(self, row):
        for element in row:
            if element is not None and element != "":
                element = self.__convert_cell(element)
            yield element

    def __convert_cell(self, csv_cell_text):
        ret = None
        if self.__auto_detect_int:
            ret = service.detect_int_value(csv_cell_text, self.__pep_0515_off)
        if ret is None and self.__auto_detect_float:
            ret = service.detect_float_value(
                csv_cell_text,
                self.__pep_0515_off,
                ignore_nan_text=self.__ignore_nan_text,
                default_float_nan=self.__default_float_nan,
            )
            shall_we_ignore_the_conversion = (
                ret in [float("inf"), float("-inf")]
            ) and self.__ignore_infinity
            if shall_we_ignore_the_conversion:
                ret = None
        if ret is None and self.__auto_detect_datetime:
            ret = service.detect_date_value(csv_cell_text)
        if ret is None:
            ret = csv_cell_text
        return ret

    def close(self):
        if self.__file_handle:
            self.__file_handle.close()


# else: means the generator has been run
# yes, no run, no file open.


class CSVFileReader(CSVSheetReader):
    """ read csv from phyical file """

    def get_file_handle(self):
        unicode_reader = open(
            self._native_sheet.payload, "r", encoding=self._encoding
        )
        return unicode_reader


class CSVinMemoryReader(CSVSheetReader):
    """ read csv file from memory """

    def get_file_handle(self):
        if isinstance(self._native_sheet.payload, compact.BytesIO):
            # please note that
            # if the end developer feed us bytesio in python3
            # we will do the conversion to StriongIO but that
            # comes at a cost.
            content = self._native_sheet.payload.read()
            unicode_reader = compact.StringIO(content.decode(self._encoding))
        else:
            unicode_reader = self._native_sheet.payload

        return unicode_reader
