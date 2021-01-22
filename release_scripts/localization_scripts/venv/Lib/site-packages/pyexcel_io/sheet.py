"""
    pyexcel_io.sheet
    ~~~~~~~~~~~~~~~~~~~

    The io interface to file extensions

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import pyexcel_io.constants as constants
from pyexcel_io.utils import _index_filter
from pyexcel_io._compact import irange
from pyexcel_io.plugin_api import NamedContent  # noqa: F401


class SheetReader(object):
    """
    Generic sheet reader
    """

    def __init__(
        self,
        sheet,
        start_row=0,
        row_limit=-1,
        start_column=0,
        column_limit=-1,
        skip_row_func=None,
        skip_column_func=None,
        skip_empty_rows=False,
        row_renderer=None,
        keep_trailing_empty_cells=False,
        **deprecated_use_of_keywords_here
    ):
        self._native_sheet = sheet
        self._keywords = {}
        self._keywords.update(deprecated_use_of_keywords_here)
        self._start_row = start_row
        self._row_limit = row_limit
        self._start_column = start_column
        self._column_limit = column_limit
        self._skip_row = _index_filter
        self._skip_column = _index_filter
        self._skip_empty_rows = skip_empty_rows
        self._row_renderer = row_renderer
        self.keep_trailing_empty_cells = keep_trailing_empty_cells

        if skip_row_func:
            self._skip_row = skip_row_func
        if skip_column_func:
            self._skip_column = skip_column_func

    def to_array(self):
        """2 dimentional representation of the content"""
        for row_index, row in enumerate(self.row_iterator()):
            row_position = self._skip_row(
                row_index, self._start_row, self._row_limit
            )
            if row_position == constants.SKIP_DATA:
                continue

            elif row_position == constants.STOP_ITERATION:
                break

            return_row = []
            tmp_row = []

            for column_index, cell_value in enumerate(
                self.column_iterator(row)
            ):
                column_position = self._skip_column(
                    column_index, self._start_column, self._column_limit
                )
                if column_position == constants.SKIP_DATA:
                    continue

                elif column_position == constants.STOP_ITERATION:
                    break

                if self.keep_trailing_empty_cells:
                    return_row.append(cell_value)
                else:
                    tmp_row.append(cell_value)
                    if cell_value is not None and cell_value != "":
                        return_row += tmp_row
                        tmp_row = []
            if self._skip_empty_rows and len(return_row) < 1:
                # we by-pass next yeild here
                # because it is an empty row
                continue

            if self._row_renderer:
                return_row = self._row_renderer(return_row)
            yield return_row

    def row_iterator(self):
        """
        iterate each row

        override this function in the sitation where
        number_of_rows() is difficult or costly to implement
        """
        return irange(self.number_of_rows())

    def column_iterator(self, row):
        """
        iterate each column of a given row

        override this function in the sitation where
        number_of_columns() is difficult or costly to implement
        """
        for column in irange(self.number_of_columns()):
            yield self.cell_value(row, column)

    def number_of_rows(self):
        """
        implement this method for easy extension
        """
        raise Exception("Please implement number_of_rows()")

    def number_of_columns(self):
        """
        implement this method for easy extension
        """
        raise Exception("Please implement number_of_columns()")

    def cell_value(self, row, column):
        """
        implement this method for easy extension
        """
        raise Exception("Please implement cell_value()")

    def close(self):
        pass


class SheetWriter(object):
    """
    Generic sheet writer
    """

    def __init__(self, native_book, native_sheet, name, **keywords):
        if name:
            sheet_name = name
        else:
            sheet_name = constants.DEFAULT_SHEET_NAME
        self._native_book = native_book
        self._native_sheet = native_sheet
        self._keywords = keywords
        self.set_sheet_name(sheet_name)

    def set_sheet_name(self, name):
        """
        Set sheet name
        """
        pass

    def write_row(self, array):
        """
        write a row into the file
        """
        raise NotImplementedError("Please implement write_row()")

    def write_array(self, table):
        """
        For standalone usage, write an array
        """
        for row in table:
            self.write_row(row)

    def close(self):
        """
        This call actually save the file
        """
        pass
