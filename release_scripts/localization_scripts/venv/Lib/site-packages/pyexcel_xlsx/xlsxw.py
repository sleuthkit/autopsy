"""
    pyexcel_xlsx.xlsxw
    ~~~~~~~~~~~~~~~~~~~

    Write xlsx file format using openpyxl

    :copyright: (c) 2015-2020 by Onni Software Ltd & its contributors
    :license: New BSD License
"""
import openpyxl
from pyexcel_io import constants
from pyexcel_io.plugin_api import IWriter, ISheetWriter


class XLSXSheetWriter(ISheetWriter):
    """
    Write data into xlsx sheet
    """

    def __init__(self, xlsx_sheet, sheet_name=constants.DEFAULT_SHEET_NAME):
        self._xlsx_sheet = xlsx_sheet
        self._xlsx_sheet.title = sheet_name

    def write_row(self, array):
        """
        write a row into the file
        """
        self._xlsx_sheet.append(array)

    def close(self):
        pass


class XLSXWriter(IWriter):
    """
    Write data in write only mode
    """

    def __init__(self, file_alike_object, _, **keywords):
        self._file_alike_object = file_alike_object
        self._native_book = openpyxl.Workbook(write_only=True)

    def create_sheet(self, name):
        return XLSXSheetWriter(self._native_book.create_sheet(), name)

    def close(self):
        """
        This call actually save the file
        """
        self._native_book.save(filename=self._file_alike_object)
