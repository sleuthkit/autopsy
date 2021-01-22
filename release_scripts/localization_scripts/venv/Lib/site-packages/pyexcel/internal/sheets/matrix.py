"""
    pyexcel.internal.sheets.matrix
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Matrix, a data model that accepts any types, spread sheet style
    of lookup.

    :copyright: (c) 2014-2019 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import copy
import types
from functools import partial
from itertools import chain

from pyexcel import _compact as compact
from pyexcel import constants as constants
from pyexcel.internal.meta import SheetMeta
from pyexcel.internal.sheets.row import Row
from pyexcel.internal.sheets.column import Column
from pyexcel.internal.sheets.formatters import to_format
from pyexcel.internal.sheets.extended_list import PyexcelList

from . import _shared as utils


class Matrix(SheetMeta):
    """The internal representation of a sheet data. Each element
    can be of any python types
    """

    def __init__(self, array):
        """Constructor

        The reason a deep copy was not made here is because
        the data sheet could be huge. It could be costly to
        copy every cell to a new memory area
        :param list array: a list of arrays
        """
        if isinstance(array, types.GeneratorType):
            self.__width, self.__array = uniform(list(array))
        else:
            try:
                self.__width, self.__array = uniform(array)
            except TypeError:
                raise TypeError("Invalid two dimensional array")
        self.row = Row(self)
        self.column = Column(self)
        self.name = "matrix"

    def get_internal_array(self):
        """present internal array"""
        return self.__array

    def number_of_rows(self):
        """The number of rows"""
        return len(self.__array)

    def number_of_columns(self):
        """The number of columns"""
        if self.number_of_rows() > 0:
            return self.__width
        else:
            return 0

    def row_range(self):
        """
        Utility function to get row range
        """
        return compact.irange(self.number_of_rows())

    def column_range(self):
        """
        Utility function to get column range
        """
        return compact.irange(self.number_of_columns())

    def cell_value(self, row, column, new_value=None):
        """Random access to table cells

        :param int row: row index which starts from 0
        :param int column: column index which starts from 0
        :param any new_value: new value if this is to set the value
        """
        fit = row < self.number_of_rows() and column < self.number_of_columns()
        if new_value is None:
            if fit:
                return self.__array[row][column]
            else:
                raise IndexError("Index out of range")
        else:
            if not fit:
                width, array = uniform(self.__array, row+1, column+1)
                self.__width, self.__array = width, array

            self.__array[row][column] = new_value

    def row_at(self, index):
        """
        Gets the data at the specified row
        """
        if index in self.row_range():
            return PyexcelList(copy.deepcopy(self.__array[index]))

        elif index < 0 and utils.abs(index) in self.row_range():
            return PyexcelList(
                copy.deepcopy(self.__array[index + self.number_of_rows()])
            )

        else:
            raise IndexError(constants.MESSAGE_INDEX_OUT_OF_RANGE)

    def set_row_at(self, row_index, data_array):
        """Update a row data range"""
        nrows = self.number_of_rows()
        if row_index < nrows:
            self.__array[row_index] = data_array
            if len(data_array) != self.number_of_columns():
                self.__width, self.__array = uniform(self.__array)
        else:
            raise IndexError(constants.MESSAGE_INDEX_OUT_OF_RANGE)

    def _set_row_at(self, row_index, data_array, starting=0):
        """Update a row data range

        It works like this if the call is: set_row_at(2, ['N', 'N', 'N'], 1)::

            A B C
            1 3 5
            2 N N <- row_index = 2
              ^starting = 1

        This function will not set element outside the current table range

        :param int row_index: which row to be modified
        :param list data_array: one dimensional array
        :param int starting: from which index, the update happens
        :raises IndexError: if row_index exceeds row range or starting
                            exceeds column range
        """
        nrows = self.number_of_rows()
        ncolumns = self.number_of_columns()
        if row_index < nrows and starting < ncolumns:
            real_len = len(data_array) + starting
            end = min(real_len, ncolumns)
            for i in range(starting, end):
                self.cell_value(row_index, i, data_array[i - starting])
            if real_len > ncolumns:
                left = ncolumns - starting
                self.__array[row_index] = (
                    self.__array[row_index] + data_array[left:]
                )
            self.__width, self.__array = uniform(self.__array)
        else:
            raise IndexError(constants.MESSAGE_INDEX_OUT_OF_RANGE)

    def _extend_row(self, row):
        array = copy.deepcopy(row)
        if compact.is_array_type(array, list):
            self.__array += array
        else:
            self.__array.append(array)

    def extend_rows(self, rows):
        """Inserts two dimensional data after the bottom row"""
        if isinstance(rows, list):
            self._extend_row(rows)
            self.__width, self.__array = uniform(self.__array)
        else:
            raise TypeError("Cannot use %s" % type(rows))

    def delete_rows(self, row_indices):
        """Deletes specified row indices"""
        if isinstance(row_indices, list) is False:
            raise IndexError
        if len(row_indices) > 0:
            unique_list = _unique(row_indices)
            sorted_list = sorted(unique_list, reverse=True)
            for i in sorted_list:
                if i < self.number_of_rows() and i >= 0:
                    del self.__array[i]

    def column_at(self, index):
        """
        Gets the data at the specified column
        """
        cell_array = PyexcelList()
        if index in self.column_range():
            for i in self.row_range():
                cell_array.append(self.cell_value(i, index))
            return cell_array

        elif index < 0 and utils.abs(index) in self.column_range():
            reverse_index = self.number_of_columns() + index
            for i in self.row_range():
                cell_array.append(self.cell_value(i, reverse_index))
            return cell_array

        else:
            raise IndexError(constants.MESSAGE_INDEX_OUT_OF_RANGE)

    def set_column_at(self, column_index, data_array, starting=0):
        """Updates a column data range

        It works like this if the call is:
        set_column_at(2, ['N','N', 'N'], 1)::

                +--> column_index = 2
                |
            A B C
            1 3 N <- starting = 1
            2 4 N

        This function will not set element outside the current table range

        :param int column_index: which column to be modified
        :param list data_array: one dimensional array
        :param int staring: from which index, the update happens
        :raises IndexError: if column_index exceeds column range
                            or starting exceeds row range
        """
        nrows = self.number_of_rows()
        ncolumns = self.number_of_columns()
        if column_index < ncolumns and starting < nrows:
            real_len = len(data_array) + starting
            end = min(real_len, nrows)
            for i in range(starting, end):
                self.cell_value(i, column_index, data_array[i - starting])
            if real_len > nrows:
                for i in range(nrows, real_len):
                    new_row = [""] * column_index + [data_array[i - starting]]
                    self.__array.append(new_row)
            self.__width, self.__array = uniform(self.__array)
        else:
            raise IndexError(constants.MESSAGE_INDEX_OUT_OF_RANGE)

    def extend_columns(self, columns):
        """Inserts two dimensional data after the rightmost column

        This is how it works:

        Given::

            s s s     t t

        Get::

            s s s  +  t t
        """
        if not isinstance(columns, list):
            raise TypeError(constants.MESSAGE_DATA_ERROR_DATA_TYPE_MISMATCH)
        incoming_data = columns
        if not compact.is_array_type(columns, list):
            incoming_data = [columns]
        incoming_data = transpose(incoming_data)
        self._extend_columns_with_rows(incoming_data)

    def _extend_columns_with_rows(self, rows):
        current_nrows = self.number_of_rows()
        current_ncols = self.number_of_columns()
        insert_column_nrows = len(rows)
        array_length = min(current_nrows, insert_column_nrows)
        for i in range(0, array_length):
            array = copy.deepcopy(rows[i])
            self.__array[i] += array
        if current_nrows < insert_column_nrows:
            delta = insert_column_nrows - current_nrows
            base = current_nrows
            for i in range(0, delta):
                new_array = [constants.DEFAULT_NA] * current_ncols
                new_array += rows[base + i]
                self.__array.append(new_array)
        self.__width, self.__array = uniform(self.__array)

    def extend_columns_with_rows(self, rows):
        """Rows were appended to the rightmost side

        example::

            >>> import pyexcel as pe
            >>> data = [
            ...     [1],
            ...     [2],
            ...     [3]
            ... ]
            >>> matrix = pe.Sheet(data)
            >>> matrix
            pyexcel sheet:
            +---+
            | 1 |
            +---+
            | 2 |
            +---+
            | 3 |
            +---+
            >>> rows = [
            ...      [11, 11],
            ...      [22, 22]
            ... ]
            >>> matrix.extend_columns_with_rows(rows)
            >>> matrix
            pyexcel sheet:
            +---+----+----+
            | 1 | 11 | 11 |
            +---+----+----+
            | 2 | 22 | 22 |
            +---+----+----+
            | 3 |    |    |
            +---+----+----+
        """
        self._extend_columns_with_rows(rows)

    def region(self, topleft_corner, bottomright_corner):
        """Get a rectangle shaped data out

        :param slice topleft_corner: the top left corner of the rectangle
        :param slice bottomright_corner: the bottom right
                                         corner of the rectangle
        """
        region = []
        max_row = min(bottomright_corner[0], self.number_of_rows())
        max_col = min(bottomright_corner[1], self.number_of_columns())
        for row in range(topleft_corner[0], max_row):
            tmp_row = []
            for column in range(topleft_corner[1], max_col):
                tmp_row.append(self.cell_value(row, column))
            region.append(tmp_row)
        return region

    def cut(self, topleft_corner, bottomright_corner):
        """Get a rectangle shaped data out and clear them in position

        :param slice topleft_corner: the top left corner of the rectangle
        :param slice bottomright_corner: the bottom right
                                         corner of the rectangle
        """
        region = self.region(topleft_corner, bottomright_corner)
        for row in range(topleft_corner[0], bottomright_corner[0]):
            for column in range(topleft_corner[1], bottomright_corner[1]):
                self.cell_value(row, column, constants.DEFAULT_NA)
        return region

    def paste(self, topleft_corner, rows=None, columns=None):
        """Paste a rectangle shaped data after a position

        :param slice topleft_corner: the top left corner of the rectangle

        example::

            >>> import pyexcel as pe
            >>> data = [
            ...     # 0 1  2  3  4 5   6
            ...     [1, 2, 3, 4, 5, 6, 7], #  0
            ...     [21, 22, 23, 24, 25, 26, 27],
            ...     [31, 32, 33, 34, 35, 36, 37],
            ...     [41, 42, 43, 44, 45, 46, 47],
            ...     [51, 52, 53, 54, 55, 56, 57]  # 4
            ... ]
            >>> s = pe.Sheet(data)
            >>> # cut  1<= row < 4, 1<= column < 5
            >>> data = s.cut([1, 1], [4, 5])
            >>> s.paste([4,6], rows=data)
            >>> s
            pyexcel sheet:
            +----+----+----+----+----+----+----+----+----+----+
            | 1  | 2  | 3  | 4  | 5  | 6  | 7  |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+
            | 21 |    |    |    |    | 26 | 27 |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+
            | 31 |    |    |    |    | 36 | 37 |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+
            | 41 |    |    |    |    | 46 | 47 |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+
            | 51 | 52 | 53 | 54 | 55 | 56 | 22 | 23 | 24 | 25 |
            +----+----+----+----+----+----+----+----+----+----+
            |    |    |    |    |    |    | 32 | 33 | 34 | 35 |
            +----+----+----+----+----+----+----+----+----+----+
            |    |    |    |    |    |    | 42 | 43 | 44 | 45 |
            +----+----+----+----+----+----+----+----+----+----+
            >>> s.paste([6,9], columns=data)
            >>> s
            pyexcel sheet:
            +----+----+----+----+----+----+----+----+----+----+----+----+
            | 1  | 2  | 3  | 4  | 5  | 6  | 7  |    |    |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            | 21 |    |    |    |    | 26 | 27 |    |    |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            | 31 |    |    |    |    | 36 | 37 |    |    |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            | 41 |    |    |    |    | 46 | 47 |    |    |    |    |    |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            | 51 | 52 | 53 | 54 | 55 | 56 | 22 | 23 | 24 | 25 |    |    |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            |    |    |    |    |    |    | 32 | 33 | 34 | 35 |    |    |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            |    |    |    |    |    |    | 42 | 43 | 44 | 22 | 32 | 42 |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            |    |    |    |    |    |    |    |    |    | 23 | 33 | 43 |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            |    |    |    |    |    |    |    |    |    | 24 | 34 | 44 |
            +----+----+----+----+----+----+----+----+----+----+----+----+
            |    |    |    |    |    |    |    |    |    | 25 | 35 | 45 |
            +----+----+----+----+----+----+----+----+----+----+----+----+

        """
        if rows:
            self._paste_rows(topleft_corner, rows)
        elif columns:
            self._paste_columns(topleft_corner, columns)
        else:
            raise ValueError(constants.MESSAGE_DATA_ERROR_EMPTY_CONTENT)

    def _paste_rows(self, topleft_corner, rows):
        starting_row, starting_column = topleft_corner
        number_of_rows = self.number_of_rows()
        number_of_columns = self.number_of_columns()
        delta = starting_row - number_of_rows
        if delta > 0:
            max_columns = max(starting_column, number_of_columns)
            empty_row = [
                [constants.DEFAULT_NA for _ in compact.irange(max_columns)]
                for __ in compact.irange(delta)
            ]
            self._extend_row(empty_row)
        number_of_rows = self.number_of_rows()
        for index, row in enumerate(rows):
            set_index = starting_row + index
            if set_index < number_of_rows:
                self._set_row_at(set_index, row, starting=topleft_corner[1])
            else:
                real_row = [constants.DEFAULT_NA] * topleft_corner[1] + row
                self._extend_row(real_row)
        self.__width, self.__array = uniform(self.__array)

    def _paste_columns(self, topleft_corner, columns):
        starting_column = topleft_corner[1]
        number_of_columns = self.number_of_columns()
        for index, column in enumerate(columns):
            set_index = starting_column + index
            if set_index < number_of_columns:
                self.set_column_at(
                    set_index, column, starting=topleft_corner[0]
                )
            else:
                real_column = [constants.DEFAULT_NA] * topleft_corner[0]
                real_column += column
                self.extend_columns([real_column])
        self.__width, self.__array = uniform(self.__array)

    def delete_columns(self, column_indices):
        """Delete columns by specified list of indices"""
        if isinstance(column_indices, list) is False:
            raise TypeError(constants.MESSAGE_DATA_ERROR_DATA_TYPE_MISMATCH)
        if len(column_indices) > 0:
            unique_list = _unique(column_indices)
            sorted_list = sorted(unique_list, reverse=True)
            for i in self.row_range():
                for j in sorted_list:
                    if j < self.number_of_columns() and j >= 0:
                        del self.__array[i][j]
            self.__width = longest_row_number(self.__array)

    def __setitem__(self, aset, cell_value):
        """Override the operator to set items"""
        if isinstance(aset, tuple):
            return self.cell_value(aset[0], aset[1], cell_value)
        elif isinstance(aset, str):
            row, column = utils.excel_cell_position(aset)
            return self.cell_value(row, column, cell_value)
        else:
            raise IndexError

    def __getitem__(self, aset):
        """By default, this class recognize from top to bottom
        from left to right"""
        if isinstance(aset, tuple):
            return self.cell_value(aset[0], aset[1])
        elif isinstance(aset, str):
            row, column = utils.excel_cell_position(aset)
            return self.cell_value(row, column)
        elif isinstance(aset, int):
            print(constants.MESSAGE_DEPRECATED_ROW_COLUMN)
            return self.row_at(aset)
        else:
            raise IndexError

    def contains(self, predicate):
        """Has something in the table"""
        for row in self.rows():
            if predicate(row):
                return True
        return False

    def transpose(self):
        """Rotate the data table by 90 degrees

        Reference :func:`transpose`
        """
        self.__array = transpose(self.__array)
        self.__width, self.__array = uniform(self.__array)

    def to_array(self):
        """Get an array out"""
        return self.__array

    def __iter__(self):
        """
        Default iterator to go through each cell one by one from top row to
        bottom row and from left to right
        """
        return self.rows()

    def enumerate(self):
        """
        Iterate cell by cell from top to bottom and from left to right

        .. testcode::

            >>> import pyexcel as pe
            >>> data = [
            ...     [1, 2, 3, 4],
            ...     [5, 6, 7, 8],
            ...     [9, 10, 11, 12]
            ... ]
            >>> m = pe.internal.sheets.Matrix(data)
            >>> print(list(m.enumerate()))
            [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

        More details see :class:`HTLBRIterator`
        """
        return chain(*self.__array)

    def reverse(self):
        """Opposite to enumerate

        each cell one by one from
        bottom row to top row and from right to left
        example::

            >>> import pyexcel as pe
            >>> data = [
            ...     [1, 2, 3, 4],
            ...     [5, 6, 7, 8],
            ...     [9, 10, 11, 12]
            ... ]
            >>> m = pe.internal.sheets.Matrix(data)
            >>> print(list(m.reverse()))
            [12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]

        More details see :class:`HBRTLIterator`
        """
        for row in reversed(self.__array):
            for cell in reversed(row):
                yield cell

    def vertical(self):
        """
        Default iterator to go through each cell one by one from
        leftmost column to rightmost row and from top to bottom
        example::

            import pyexcel as pe
            data = [
                [1, 2, 3, 4],
                [5, 6, 7, 8],
                [9, 10, 11, 12]
            ]
            m = pe.internal.sheets.Matrix(data)
            print(list(m.vertical()))

        output::

            [1, 5, 9, 2, 6, 10, 3, 7, 11, 4, 8, 12]

        More details see :class:`VTLBRIterator`
        """
        return chain(*compact.czip(*self.__array))

    def rvertical(self):
        """
        Default iterator to go through each cell one by one from rightmost
        column to leftmost row and from bottom to top
        example::

            import pyexcel as pe
            data = [
                [1, 2, 3, 4],
                [5, 6, 7, 8],
                [9, 10, 11, 12]
            ]
            m = pe.internal.sheets.Matrix(data)
            print(pe.utils.to_array(m.rvertical())

        output::

            [12, 8, 4, 11, 7, 3, 10, 6, 2, 9, 5, 1]

        More details see :class:`VBRTLIterator`
        """
        for column in compact.czip(*(reversed(row) for row in self.__array)):
            for cell in reversed(column):
                yield cell

    def rows(self):
        """
        Returns a top to bottom row iterator

        example::

            import pyexcel as pe
            data = [
                [1, 2, 3, 4],
                [5, 6, 7, 8],
                [9, 10, 11, 12]
            ]
            m = pe.internal.sheets.Matrix(data)
            print(pe.utils.to_array(m.rows()))

        output::

            [[1, 2, 3, 4], [5, 6, 7, 8], [9, 10, 11, 12]]

        More details see :class:`RowIterator`
        """
        for row in self.__array:
            yield row

    def rrows(self):
        """
        Returns a bottom to top row iterator

        .. testcode::

            import pyexcel as pe
            data = [
                [1, 2, 3, 4],
                [5, 6, 7, 8],
                [9, 10, 11, 12]
            ]
            m = pe.internal.sheets.Matrix(data)
            print(pe.utils.to_array(m.rrows()))

        .. testoutput::

            [[9, 10, 11, 12], [5, 6, 7, 8], [1, 2, 3, 4]]

        More details see :class:`RowReverseIterator`
        """
        for row in reversed(self.__array):
            yield row

    def columns(self):
        """
        Returns a left to right column iterator

        .. testcode::

            import pyexcel as pe
            data = [
                [1, 2, 3, 4],
                [5, 6, 7, 8],
                [9, 10, 11, 12]
            ]
            m = pe.internal.sheets.Matrix(data)
            print(list(m.columns()))

        .. testoutput::

            [[1, 5, 9], [2, 6, 10], [3, 7, 11], [4, 8, 12]]

        More details see :class:`ColumnIterator`
        """
        for row in compact.czip(*self.__array):
            yield list(row)

    def rcolumns(self):
        """
        Returns a right to left column iterator

        example::

            import pyexcel as pe
            data = [
                [1, 2, 3, 4],
                [5, 6, 7, 8],
                [9, 10, 11, 12]
            ]
            m = pe.internal.sheets.Matrix(data)
            print(pe.utils.to_array(m.rcolumns()))

        output::

            [[4, 8, 12], [3, 7, 11], [2, 6, 10], [1, 5, 9]]

        More details see :class:`ColumnReverseIterator`
        """
        for column in compact.czip(*(reversed(row) for row in self.__array)):
            yield list(column)

    def filter(self, column_indices=None, row_indices=None):
        """Apply the filter with immediate effect"""
        if row_indices is not None:
            self.delete_rows(row_indices)
        if column_indices is not None:
            self.delete_columns(column_indices)

    def format(self, formatter):
        """Apply a formatting action for the whole sheet

        Example::

            >>> import pyexcel as pe
            >>> # Given a dictinoary as the following
            >>> data = {
            ...     "1": [1, 2, 3, 4, 5, 6, 7, 8],
            ...     "3": [1.25, 2.2, 3.3, 4.4, 5.5, 6.6, 7.7, 8.8],
            ...     "5": [2, 3, 4, 5, 6, 7, 8, 9],
            ...     "7": [1, '',]
            ...     }
            >>> sheet = pe.get_sheet(adict=data)
            >>> sheet.row[1]
            [1, 1.25, 2, 1]
            >>> sheet.format(str)
            >>> sheet.row[1]
            ['1', '1.25', '2', '1']
            >>> sheet.format(int)
            >>> sheet.row[1]
            [1, 1, 2, 1]

        """
        custom_function = partial(to_format, formatter)
        self.map(custom_function)

    def map(self, custom_function):
        """Execute a function across all cells of the sheet

        Example::

            >>> import pyexcel as pe
            >>> # Given a dictinoary as the following
            >>> data = {
            ...     "1": [1, 2, 3, 4, 5, 6, 7, 8],
            ...     "3": [1.25, 2.2, 3.3, 4.4, 5.5, 6.6, 7.7, 8.8],
            ...     "5": [2, 3, 4, 5, 6, 7, 8, 9],
            ...     "7": [1, '',]
            ...     }
            >>> sheet = pe.get_sheet(adict=data)
            >>> sheet.row[1]
            [1, 1.25, 2, 1]
            >>> inc = lambda value: (float(value) if value != '' else 0)+1
            >>> sheet.map(inc)
            >>> sheet.row[1]
            [2.0, 2.25, 3.0, 2.0]

        """
        for row in self.row_range():
            for column in self.column_range():
                value = self.cell_value(row, column)
                value = custom_function(value)
                self.cell_value(row, column, value)

    def __iadd__(self, other):
        return _add(self.name, self.__array, other)

    def __add__(self, other):
        """Overload the + sign

        :returns: a new book
        """
        return _add(self.name, copy.deepcopy(self.__array), other)

    def clone(self):
        return Matrix(copy.deepcopy(self.__array))


def _unique(seq):
    """Return a unique list of the incoming list

    Reference:
    http://stackoverflow.com/questions/480214/
    how-do-you-remove-duplicates-from-a-list-in-python-whilst-preserving-order
    """
    seen = set()
    seen_add = seen.add
    return [x for x in seq if not (x in seen or seen_add(x))]


def longest_row_number(array):
    """Find the length of the longest row in the array

    :param list in_array: a list of arrays
    """
    if len(array) > 0:
        # map runs len() against each member of the array
        return max(map(len, array))
    else:
        return 0


def uniform(array, min_rows=0, min_columns=0):
    """Fill-in empty strings to empty cells to make it MxN

    :param list in_array: a list of arrays
    :param int row_no: desired minimum row count
    :param int column_no: desired minimum column length
    """
    width = max(min_columns, longest_row_number(array))
    array_length = len(array)
    height = max(array_length, min_rows)

    if width == 0:
        return 0, array
    else:
        for row in array:
            row_length = len(row)
            for index in range(0, row_length):
                if row[index] is None:
                    row[index] = constants.DEFAULT_NA
            if row_length < width:
                row += [constants.DEFAULT_NA] * (width - row_length)
        for _ in range(array_length, height):
            row = [constants.DEFAULT_NA] * width
            array.append(row)
        return width, array


def transpose(in_array):
    """Rotate clockwise by 90 degrees and flip horizontally

    First column become first row.
    :param list in_array: a list of arrays

    The transformation is::

        1 2 3       1  4
        4 5 6 7 ->  2  5
                    3  6
                    '' 7
    """
    max_length = longest_row_number(in_array)
    new_array = []
    for i in range(0, max_length):
        row_data = []
        for row in in_array:
            if i < len(row):
                row_data.append(row[i])
            else:
                row_data.append(constants.DEFAULT_NA)
        new_array.append(row_data)
    return new_array


def _add(name, left, right):
    from pyexcel.book import Book, local_uuid

    content = {}
    content[name] = left
    if isinstance(right, Book):
        right_in_dict = copy.deepcopy(right.to_dict())
        for key in right_in_dict.keys():
            new_key = key
            if len(right_in_dict.keys()) == 1:
                new_key = right.filename
            if new_key in content:
                uid = local_uuid()
                new_key = "%s_%s" % (key, uid)
            content[new_key] = right_in_dict[key]
    elif isinstance(right, Matrix):
        new_key = right.name
        if new_key in content:
            uid = local_uuid()
            new_key = "%s_%s" % (right.name, uid)
        content[new_key] = copy.deepcopy(right.get_internal_array())
    else:
        raise TypeError
    new_book = Book()
    new_book.load_from_sheets(content)
    return new_book
