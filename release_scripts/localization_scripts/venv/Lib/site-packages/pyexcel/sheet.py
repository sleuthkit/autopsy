"""
    pyexcel.sheet
    ~~~~~~~~~~~~~~~~~~~~~

    Building on top of matrix, adding named columns and rows support

    :copyright: (c) 2014-2019 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from collections import defaultdict

from pyexcel import _compact as compact
from pyexcel import constants as constants
from pyexcel._compact import OrderedDict
from pyexcel.internal.sheets.row import Row as NamedRow
from pyexcel.internal.sheets.column import Column as NamedColumn
from pyexcel.internal.sheets.matrix import Matrix


class Sheet(Matrix):
    """Two dimensional data container for filtering, formatting and iteration

    :class:`~pyexcel.Sheet` is a container for a two dimensional array, where
    individual cell can be any Python types. Other than numbers, value of these
    types: string, date, time and boolean can be mixed in the array. This
    differs from Numpy's matrix where each cell are of the same number type.

    In order to prepare two dimensional data for your computation, formatting
    functions help convert array cells to required types. Formatting can be
    applied not only to the whole sheet but also to selected rows or columns.
    Custom conversion function can be passed to these formatting functions. For
    example, to remove extra spaces surrounding the content of a cell, a custom
    function is required.

    Filtering functions are used to reduce the information contained in the
    array.

    :ivar name: sheet name. use to change sheet name
    :ivar row: access data row by row
    :ivar column: access data column by column

    Example::

        >>> import pyexcel as p
        >>> content = {'A': [[1]]}
        >>> b = p.get_book(bookdict=content)
        >>> b
        A:
        +---+
        | 1 |
        +---+
        >>> b[0].name
        'A'
        >>> b[0].name = 'B'
        >>> b
        B:
        +---+
        | 1 |
        +---+

    """

    def __init__(
        self,
        sheet=None,
        name=constants.DEFAULT_NAME,
        name_columns_by_row=-1,
        name_rows_by_column=-1,
        colnames=None,
        rownames=None,
        transpose_before=False,
        transpose_after=False,
    ):
        """Constructor

        :param sheet: two dimensional array
        :param name: this becomes the sheet name.
        :param name_columns_by_row: use a row to name all columns
        :param name_rows_by_column: use a column to name all rows
        :param colnames: use an external list of strings to name the columns
        :param rownames: use an external list of strings to name the rows
        """
        self.__column_names = []
        self.__row_names = []
        self.__row_index = -1
        self.__column_index = -1
        self.init(
            sheet=sheet,
            name=name,
            name_columns_by_row=name_columns_by_row,
            name_rows_by_column=name_rows_by_column,
            colnames=colnames,
            rownames=rownames,
            transpose_before=transpose_before,
            transpose_after=transpose_after,
        )

    def init(
        self,
        sheet=None,
        name=constants.DEFAULT_NAME,
        name_columns_by_row=-1,
        name_rows_by_column=-1,
        colnames=None,
        rownames=None,
        transpose_before=False,
        transpose_after=False,
    ):
        """custom initialization functions

        examples::

            >>> import pyexcel as pe
            >>> data = [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
            >>> sheet = pe.Sheet(data)
            >>> sheet.row[1]
            [4, 5, 6]
            >>> sheet.row[0:3]
            [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
            >>> sheet.row += [11, 12, 13]
            >>> sheet.row[3]
            [11, 12, 13]
            >>> sheet.row[0:4] = [0, 0, 0] # set all to zero
            >>> sheet.row[3]
            [0, 0, 0]
            >>> sheet.row[0] = ['a', 'b', 'c'] # set one row
            >>> sheet.row[0]
            ['a', 'b', 'c']
            >>> del sheet.row[0] # delete first row
            >>> sheet.row[0] # now, second row becomes the first
            [0, 0, 0]
            >>> del sheet.row[0:]
            >>> sheet.row[0]  # nothing left
            Traceback (most recent call last):
                ...
            IndexError
        """
        # this get rid of phatom data by not specifying sheet
        if sheet is None:
            sheet = []
        Matrix.__init__(self, sheet)
        self.name = name
        self.__column_names = []
        self.__row_names = []
        if transpose_before:
            self.transpose()
        self.row = NamedRow(self)
        self.column = NamedColumn(self)
        if name_columns_by_row != -1:
            if colnames:
                raise NotImplementedError(constants.MESSAGE_NOT_IMPLEMENTED_02)
            self.name_columns_by_row(name_columns_by_row)
        else:
            if colnames:
                self.__column_names = colnames
        if name_rows_by_column != -1:
            if rownames:
                raise NotImplementedError(constants.MESSAGE_NOT_IMPLEMENTED_02)
            self.name_rows_by_column(name_rows_by_column)
        else:
            if rownames:
                self.__row_names = rownames
        if transpose_after:
            self.transpose()

    def clone(self):
        import copy

        new_sheet = Sheet(
            copy.deepcopy(self.get_internal_array()),
            name_columns_by_row=self.__row_index,
            name_rows_by_column=self.__column_index,
        )
        return new_sheet

    def transpose(self):
        self.__column_names, self.__row_names = (
            self.__row_names,
            self.__column_names,
        )
        Matrix.transpose(self)

    def name_columns_by_row(self, row_index):
        """Use the elements of a specified row to represent individual columns

        The specified row will be deleted from the data
        :param row_index: the index of the row that has the column names
        """
        self.__row_index = row_index
        self.__column_names = make_names_unique(self.row_at(row_index))
        del self.row[row_index]

    def name_rows_by_column(self, column_index):
        """Use the elements of a specified column to represent individual rows

        The specified column will be deleted from the data
        :param column_index: the index of the column that has the row names
        """
        self.__column_index = column_index
        self.__row_names = make_names_unique(self.column_at(column_index))
        del self.column[column_index]

    def group_rows_by_column(self, column_index_or_name):
        """Group rows with similiar column into a two dimensional array.

        Example::

            >>> import pyexcel as p
            >>> sample_data = [
            ...     ["22/09/2017", "morning"],
            ...     ["22/09/2017", "afternoon"],
            ...     ["23/09/2017", "morning"],
            ...     ["23/09/2017", "afternoon"]
            ... ]
            >>> sheet = p.Sheet(sample_data)
            >>> sheet.group_rows_by_column(0)
            22/09/2017:
            +------------+-----------+
            | 22/09/2017 | morning   |
            +------------+-----------+
            | 22/09/2017 | afternoon |
            +------------+-----------+
            23/09/2017:
            +------------+-----------+
            | 23/09/2017 | morning   |
            +------------+-----------+
            | 23/09/2017 | afternoon |
            +------------+-----------+

        :returns: an instance of a Book
        """
        from pyexcel import Book

        groups = defaultdict(list)

        if isinstance(column_index_or_name, int):
            for row in self.to_array():
                groups[row[column_index_or_name]].append(row)
        else:
            if len(self.colnames) == 0:
                self.name_columns_by_row(0)
            column_index = self.colnames.index(column_index_or_name)
            for row in self.rows():
                if len(groups[row[column_index]]) == 0:
                    groups[row[column_index]].append(self.colnames)
                groups[row[column_index]].append(row)
        return Book(groups)

    def top(self, lines=5):
        """
        Preview top most 5 rows
        """
        sheet = Sheet(self.row[:lines])
        if len(self.colnames) > 0:
            sheet.colnames = self.__column_names
        return sheet

    def top_left(self, rows=5, columns=5):
        """
        Preview top corner: 5x5
        """
        region = Sheet(self.region((0, 0), (rows, columns)))
        if len(self.__row_names) > 0:
            rownames = self.__row_names[:rows]
            region.rownames = rownames
        if len(self.__column_names) > 0:
            columnnames = self.__column_names[:columns]
            region.colnames = columnnames

        return region

    @property
    def colnames(self):
        """Return column names if any"""
        return self.__column_names

    @colnames.setter
    def colnames(self, value):
        """Set column names"""
        self.__column_names = make_names_unique(value)

    @property
    def rownames(self):
        """Return row names if any"""
        return self.__row_names

    @rownames.setter
    def rownames(self, value):
        """Set row names"""
        self.__row_names = make_names_unique(value)

    def named_column_at(self, name):
        """Get a column by its name"""
        index = name
        if compact.is_string(type(index)):
            index = self.colnames.index(name)
        column_array = self.column_at(index)
        return column_array

    def set_named_column_at(self, name, column_array):
        """
        Take the first row as column names

        Given name to identify the column index, set the column to
        the given array except the column name.
        """
        index = name
        if compact.is_string(type(index)):
            index = self.colnames.index(name)
        self.set_column_at(index, column_array)

    def delete_columns(self, column_indices):
        """Delete one or more columns

        :param list column_indices: a list of column indices
        """
        Matrix.delete_columns(self, column_indices)
        if len(self.__column_names) > 0:
            new_series = [
                self.__column_names[i]
                for i in range(0, len(self.__column_names))
                if i not in column_indices
            ]
            self.__column_names = new_series

    def delete_rows(self, row_indices):
        """Delete one or more rows

        :param list row_indices: a list of row indices
        """
        Matrix.delete_rows(self, row_indices)
        if len(self.__row_names) > 0:
            new_series = [
                self.__row_names[i]
                for i in range(0, len(self.__row_names))
                if i not in row_indices
            ]
            self.__row_names = new_series

    def delete_named_column_at(self, name):
        """Works only after you named columns by a row

        Given name to identify the column index, set the column to
        the given array except the column name.
        :param str name: a column name
        """
        if isinstance(name, int):
            if len(self.rownames) > 0:
                self.rownames.pop(name)
            self.delete_columns([name])
        else:
            index = self.colnames.index(name)
            self.colnames.pop(index)
            Matrix.delete_columns(self, [index])

    def named_row_at(self, name):
        """Get a row by its name """
        index = name
        index = self.rownames.index(name)
        row_array = self.row_at(index)
        return row_array

    def set_named_row_at(self, name, row_array):
        """
        Take the first column as row names

        Given name to identify the row index, set the row to
        the given array except the row name.
        """
        index = name
        if compact.is_string(type(index)):
            index = self.rownames.index(name)
        self.set_row_at(index, row_array)

    def delete_named_row_at(self, name):
        """Take the first column as row names

        Given name to identify the row index, set the row to
        the given array except the row name.
        """
        if isinstance(name, int):
            if len(self.rownames) > 0:
                self.rownames.pop(name)
            self.delete_rows([name])
        else:
            index = self.rownames.index(name)
            self.rownames.pop(index)
            Matrix.delete_rows(self, [index])

    def extend_rows(self, rows):
        """Take ordereddict to extend named rows

        :param ordereddist/list rows: a list of rows.
        """
        incoming_data = []
        if isinstance(rows, compact.OrderedDict):
            keys = rows.keys()
            for k in keys:
                self.rownames.append(k)
                incoming_data.append(rows[k])
            Matrix.extend_rows(self, incoming_data)
        elif len(self.rownames) > 0:
            raise TypeError(
                constants.MESSAGE_DATA_ERROR_ORDEREDDICT_IS_EXPECTED
            )
        else:
            Matrix.extend_rows(self, rows)

    def extend_columns_with_rows(self, rows):
        """Put rows on the right most side of the data"""
        if len(self.colnames) > 0:
            headers = rows.pop(self.__row_index)
            self.__column_names += headers
        Matrix.extend_columns_with_rows(self, rows)

    def extend_columns(self, columns):
        """Take ordereddict to extend named columns

        :param ordereddist/list columns: a list of columns
        """
        incoming_data = []
        if isinstance(columns, compact.OrderedDict):
            keys = columns.keys()
            for k in keys:
                self.colnames.append(k)
                incoming_data.append(columns[k])
            Matrix.extend_columns(self, incoming_data)
        elif len(self.colnames) > 0:
            raise TypeError(
                constants.MESSAGE_DATA_ERROR_ORDEREDDICT_IS_EXPECTED
            )
        else:
            Matrix.extend_columns(self, columns)

    def to_array(self):
        """Returns an array after filtering"""
        ret = []
        ret += list(self.rows())
        if len(self.rownames) > 0:
            ret = [[value[0]] + value[1] for value in zip(self.rownames, ret)]
            if not compact.PY2:
                ret = list(ret)
        if len(self.colnames) > 0:
            if len(self.rownames) > 0:
                ret.insert(0, [constants.DEFAULT_NA] + self.colnames)
            else:
                ret.insert(0, self.colnames)
        return ret

    def to_records(self, custom_headers=None):
        """
        Make an array of dictionaries

        It takes the first row as keys and the rest of
        the rows as values. Then zips keys and row values
        per each row. This is particularly helpful for
        database operations.
        """
        if len(self.colnames) > 0:
            if custom_headers:
                headers = custom_headers
            else:
                headers = self.colnames
            for row in self.rows():
                the_dict = compact.OrderedDict(zip(headers, row))
                yield the_dict

        elif len(self.rownames) > 0:
            if custom_headers:
                headers = custom_headers
            else:
                headers = self.rownames
            for column in self.columns():
                the_dict = compact.OrderedDict(zip(headers, column))
                yield the_dict

        else:
            raise ValueError(constants.MESSAGE_DATA_ERROR_NO_SERIES)

    def project(self, new_ordered_columns, exclusion=False):
        """
        Rearrange the sheet.

        :ivar new_ordered_columns: new columns
        :ivar exclusion: to exlucde named column or not. defaults to False

        Example::

           >>> sheet = Sheet(
           ... [["A", "B", "C"], [1, 2, 3], [11, 22, 33], [111, 222, 333]],
           ... name_columns_by_row=0)
           >>> sheet.project(["B", "A", "C"])
           pyexcel sheet:
           +-----+-----+-----+
           |  B  |  A  |  C  |
           +=====+=====+=====+
           | 2   | 1   | 3   |
           +-----+-----+-----+
           | 22  | 11  | 33  |
           +-----+-----+-----+
           | 222 | 111 | 333 |
           +-----+-----+-----+
           >>> sheet.project(["B", "C"])
           pyexcel sheet:
           +-----+-----+
           |  B  |  C  |
           +=====+=====+
           | 2   | 3   |
           +-----+-----+
           | 22  | 33  |
           +-----+-----+
           | 222 | 333 |
           +-----+-----+
           >>> sheet.project(["B", "C"], exclusion=True)
           pyexcel sheet:
           +-----+
           |  A  |
           +=====+
           | 1   |
           +-----+
           | 11  |
           +-----+
           | 111 |
           +-----+

        """
        from pyexcel import get_array

        the_dict = self.to_dict()
        new_dict = OrderedDict()
        if exclusion:
            for column in the_dict.keys():
                if column not in new_ordered_columns:
                    new_dict[column] = the_dict[column]
        else:
            for column in new_ordered_columns:
                new_dict[column] = the_dict[column]

        array = get_array(adict=new_dict)
        return Sheet(array, name=self.name, name_columns_by_row=0)

    def to_dict(self, row=False):
        """Returns a dictionary"""
        the_dict = compact.OrderedDict()
        if len(self.colnames) > 0 and row is False:
            for column in self.named_columns():
                the_dict.update(column)
        elif len(self.rownames) > 0:
            for row in self.named_rows():
                the_dict.update(row)
        else:
            raise NotImplementedError("Not implemented")
        return the_dict

    def named_rows(self):
        """iterate rows using row names"""
        for row_name in self.__row_names:
            try:
                yield {row_name: self.row[row_name]}
            except IndexError:
                yield {row_name: []}

    def named_columns(self):
        """iterate rows using column names"""
        for column_name in self.__column_names:
            try:
                yield {column_name: self.column[column_name]}
            except IndexError:
                yield {column_name: []}

    @property
    def content(self):
        """
        Plain representation without headers
        """
        content = self.get_texttable(write_title=False)
        return _RepresentedString(content)

    # python magic methods

    def __getitem__(self, aset):
        if isinstance(aset, tuple):
            if isinstance(aset[0], str):
                row = self.rownames.index(aset[0])
            else:
                row = aset[0]

            if isinstance(aset[1], str):
                column = self.colnames.index(aset[1])
            else:
                column = aset[1]
            return self.cell_value(row, column)
        else:
            return Matrix.__getitem__(self, aset)

    def __setitem__(self, aset, c):
        if isinstance(aset, tuple):
            if isinstance(aset[0], str):
                row = self.rownames.index(aset[0])
            else:
                row = aset[0]

            if isinstance(aset[1], str):
                column = self.colnames.index(aset[1])
            else:
                column = aset[1]
            self.cell_value(row, column, c)
        else:
            Matrix.__setitem__(self, aset, c)

    def __len__(self):
        return self.number_of_rows()


class _RepresentedString(object):
    """present in text"""

    def __init__(self, text):
        self.text = text

    def __repr__(self):
        return self.text

    def __str__(self):
        return self.text


def make_names_unique(alist):
    """Append the number of occurences to duplicated names"""
    duplicates = {}
    new_names = []
    for item in alist:
        if not compact.is_string(type(item)):
            item = str(item)
        item = item.strip()
        if item in duplicates:
            duplicates[item] = duplicates[item] + 1
            new_names.append("%s-%d" % (item, duplicates[item]))
        else:
            duplicates[item] = 0
            new_names.append(item)
    return new_names
