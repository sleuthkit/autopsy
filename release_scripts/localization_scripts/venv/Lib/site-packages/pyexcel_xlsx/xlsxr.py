"""
    pyexcel_xlsx.xlsxr
    ~~~~~~~~~~~~~~~~~~~

    Read xlsx file format using openpyxl

    :copyright: (c) 2015-2020 by Onni Software Ltd & its contributors
    :license: New BSD License
"""
from io import BytesIO

import openpyxl
from pyexcel_io.plugin_api import ISheet, IReader, NamedContent


class FastSheet(ISheet):
    """
    Iterate through rows
    """

    def __init__(self, sheet, **_):
        self.xlsx_sheet = sheet

    def row_iterator(self):
        """
        openpyxl row iterator

        http://openpyxl.readthedocs.io/en/default/optimized.html
        """
        for row in self.xlsx_sheet.rows:
            yield row

    def column_iterator(self, row):
        """
        a generator for the values in a row
        """
        for cell in row:
            yield cell.value


class MergedCell(object):
    def __init__(self, cell_ranges):
        self.__cl, self.__rl, self.__ch, self.__rh = cell_ranges.bounds
        self.value = None

    def register_cells(self, registry):
        for rowx in range(self.__rl, self.__rh + 1):
            for colx in range(self.__cl, self.__ch + 1):
                key = "%s-%s" % (rowx, colx)
                registry[key] = self

    def bottom_row(self):
        return self.__rh

    def right_column(self):
        return self.__ch


class SlowSheet(FastSheet):
    """
    This sheet will be slower because it does not use readonly sheet
    """

    def __init__(self, sheet, **keywords):
        self.xlsx_sheet = sheet
        self._keywords = keywords
        self.__merged_cells = {}
        self.max_row = 0
        self.max_column = 0
        self.__sheet_max_row = sheet.max_row
        self.__sheet_max_column = sheet.max_column
        for ranges in sheet.merged_cells.ranges[:]:
            merged_cells = MergedCell(ranges)
            merged_cells.register_cells(self.__merged_cells)
            if self.max_row < merged_cells.bottom_row():
                self.max_row = merged_cells.bottom_row()
            if self.max_column < merged_cells.right_column():
                self.max_column = merged_cells.right_column()

    def row_iterator(self):
        """
        skip hidden rows
        """
        for row_index, row in enumerate(self.xlsx_sheet.rows, 1):
            if self.xlsx_sheet.row_dimensions[row_index].hidden is False:
                yield (row, row_index)
        if self.max_row > self.__sheet_max_row:
            for i in range(self.__sheet_max_row, self.max_row):
                data = [None] * self.__sheet_max_column
                yield (data, i + 1)

    def column_iterator(self, row_struct):
        """
        skip hidden columns
        """
        row, row_index = row_struct
        for column_index, cell in enumerate(row, 1):
            letter = openpyxl.utils.get_column_letter(column_index)
            if self.xlsx_sheet.column_dimensions[letter].hidden is False:
                if cell:
                    value = cell.value
                else:
                    value = ""
                if value is None:
                    value = ""
                value = self._merged_cells(row_index, column_index, value)
                yield value
        if self.max_column > self.__sheet_max_column:
            for i in range(self.__sheet_max_column, self.max_column):
                value = self._merged_cells(row_index, i + 1, "")
                yield value

    def _merged_cells(self, row, column, value):
        ret = value
        if self.__merged_cells:
            merged_cell = self.__merged_cells.get("%s-%s" % (row, column))
            if merged_cell:
                if merged_cell.value:
                    ret = merged_cell.value
                else:
                    merged_cell.value = value
        return ret


class XLSXBook(IReader):
    """
    Open xlsx as read only mode
    """

    def __init__(
        self,
        file_alike_object,
        file_type,
        skip_hidden_sheets=True,
        detect_merged_cells=False,
        skip_hidden_row_and_column=True,
        **keywords
    ):
        self.skip_hidden_sheets = skip_hidden_sheets
        self.skip_hidden_row_and_column = skip_hidden_row_and_column
        self.detect_merged_cells = detect_merged_cells
        self.keywords = keywords
        self._load_the_excel_file(file_alike_object)

    def read_sheet(self, sheet_index):
        native_sheet = self.content_array[sheet_index].payload
        if self.skip_hidden_row_and_column or self.detect_merged_cells:
            sheet = SlowSheet(native_sheet, **self.keywords)
        else:
            sheet = FastSheet(native_sheet, **self.keywords)
        return sheet

    def close(self):
        self.xlsx_book.close()
        self.xlsx_book = None

    def _load_the_excel_file(self, file_alike_object):
        read_only_flag = True
        if self.skip_hidden_row_and_column:
            read_only_flag = False
        data_only_flag = True
        if self.detect_merged_cells:
            data_only_flag = False
        self.xlsx_book = openpyxl.load_workbook(
            filename=file_alike_object,
            data_only=data_only_flag,
            read_only=read_only_flag,
        )
        self.content_array = []
        for sheet_name, sheet in zip(
            self.xlsx_book.sheetnames, self.xlsx_book
        ):
            if self.skip_hidden_sheets and sheet.sheet_state == "hidden":
                continue
            self.content_array.append(NamedContent(sheet_name, sheet))


class XLSXBookInContent(XLSXBook):
    """
    Open xlsx as read only mode
    """

    def __init__(self, file_content, file_type, **keywords):
        io = BytesIO(file_content)
        super().__init__(io, file_type, **keywords)
