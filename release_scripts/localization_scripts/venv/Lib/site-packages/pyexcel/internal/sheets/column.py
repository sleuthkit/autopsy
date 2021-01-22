"""
    pyexcel.internal.sheets.column
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Generic table column

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import copy
import types

from pyexcel import _compact as compact

from . import _shared as utils


class Column(utils.CommonPropertyAmongRowNColumn):
    """Represent columns of a matrix

    .. table:: "example.csv"

        = = =
        1 2 3
        4 5 6
        7 8 9
        = = =

    Let us manipulate the data columns on the above data matrix::

        >>> import pyexcel as pe
        >>> data = [[1,2,3], [4,5,6], [7,8,9]]
        >>> m = pe.internal.sheets.Matrix(data)
        >>> m.column[0]
        [1, 4, 7]
        >>> m.column[2] = [0, 0, 0]
        >>> m.column[2]
        [0, 0, 0]
        >>> del m.column[1]
        >>> m.column[1]
        [0, 0, 0]
        >>> m.column[2]
        Traceback (most recent call last):
            ...
        IndexError

    """

    def select(self, indices):
        """
        Examples:

            >>> import pyexcel as pe
            >>> data = [[1,2,3,4,5,6,7,9]]
            >>> sheet = pe.Sheet(data)
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+---+---+---+---+
            | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 9 |
            +---+---+---+---+---+---+---+---+
            >>> sheet.column.select([1,2,3,5])
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+
            | 2 | 3 | 4 | 6 |
            +---+---+---+---+
            >>> data = [[1,2,3,4,5,6,7,9]]
            >>> sheet = pe.Sheet(data)
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+---+---+---+---+
            | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 9 |
            +---+---+---+---+---+---+---+---+
            >>> sheet.column.select([1,2,3,5])
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+
            | 2 | 3 | 4 | 6 |
            +---+---+---+---+
            >>> data = [
            ...     ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'],
            ...     [1,2,3,4,5,6,7,9],
            ... ]
            >>> sheet = pe.Sheet(data, name_columns_by_row=0)
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+---+---+---+---+
            | a | b | c | d | e | f | g | h |
            +===+===+===+===+===+===+===+===+
            | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 9 |
            +---+---+---+---+---+---+---+---+
            >>> del sheet.column['a', 'b', 'i', 'f'] # doctest:+ELLIPSIS
            Traceback (most recent call last):
                ...
            ValueError: ...
            >>> sheet.column.select(['a', 'c', 'e', 'h'])
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+
            | a | c | e | h |
            +===+===+===+===+
            | 1 | 3 | 5 | 9 |
            +---+---+---+---+
        """
        new_indices = []
        if compact.is_array_type(indices, str):
            new_indices = utils.names_to_indices(indices, self._ref.colnames)
        else:
            new_indices = indices
        to_remove = []
        for index in self._ref.column_range():
            if index not in new_indices:
                to_remove.append(index)
        self._ref.filter(column_indices=to_remove)

    def __delitem__(self, aslice):
        """Override the operator to delete items

        Examples:

            >>> import pyexcel as pe
            >>> data = [[1,2,3,4,5,6,7,9]]
            >>> sheet = pe.Sheet(data)
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+---+---+---+---+
            | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 9 |
            +---+---+---+---+---+---+---+---+
            >>> del sheet.column[1,2,3,5]
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+
            | 1 | 5 | 7 | 9 |
            +---+---+---+---+
            >>> data = [
            ...     ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'],
            ...     [1,2,3,4,5,6,7,9],
            ... ]
            >>> sheet = pe.Sheet(data, name_columns_by_row=0)
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+---+---+---+---+
            | a | b | c | d | e | f | g | h |
            +===+===+===+===+===+===+===+===+
            | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 9 |
            +---+---+---+---+---+---+---+---+
            >>> del sheet.column['a', 'b', 'i', 'f'] # doctest:+ELLIPSIS
            Traceback (most recent call last):
                ...
            ValueError: ...
            >>> del sheet.column['a', 'c', 'e', 'h']
            >>> sheet
            pyexcel sheet:
            +---+---+---+---+
            | b | d | f | g |
            +===+===+===+===+
            | 2 | 4 | 6 | 7 |
            +---+---+---+---+

        """
        is_sheet = compact.is_string(type(aslice)) and hasattr(
            self._ref, "delete_named_column_at"
        )
        if is_sheet:
            self._ref.delete_named_column_at(aslice)
        elif compact.is_tuple_consists_of_strings(aslice):
            indices = utils.names_to_indices(list(aslice), self._ref.colnames)
            self._ref.delete_columns(indices)
        elif isinstance(aslice, slice):
            my_range = utils.analyse_slice(
                aslice, self._ref.number_of_columns()
            )
            self._ref.delete_columns(my_range)
        elif isinstance(aslice, str):
            index = utils.excel_column_index(aslice)
            self._ref.delete_columns([index])
        elif isinstance(aslice, tuple):
            indices = list(aslice)
            self._ref.filter(column_indices=indices)
        elif isinstance(aslice, list):
            indices = aslice
            self._ref.filter(column_indices=indices)
        elif isinstance(aslice, int):
            self._ref.delete_columns([aslice])
        elif isinstance(aslice, types.LambdaType):
            self._delete_columns_by_content(aslice)
        elif isinstance(aslice, types.FunctionType):
            self._delete_columns_by_content(aslice)
        else:
            raise IndexError

    def _delete_columns_by_content(self, locator):
        to_remove = []
        for index, column in enumerate(self._ref.columns()):
            if locator(index, column):
                to_remove.append(index)
        if len(to_remove) > 0:
            self._ref.delete_columns(to_remove)

    def __setitem__(self, aslice, a_column):
        """Override the operator to set items"""
        is_sheet = compact.is_string(type(aslice)) and hasattr(
            self._ref, "set_named_column_at"
        )
        if is_sheet:
            self._ref.set_named_column_at(aslice, a_column)
        elif isinstance(aslice, slice):
            my_range = utils.analyse_slice(
                aslice, self._ref.number_of_columns()
            )
            for i in my_range:
                self._ref.set_column_at(i, a_column)
        elif isinstance(aslice, str):
            index = utils.excel_column_index(aslice)
            self._ref.set_column_at(index, a_column)
        elif isinstance(aslice, int):
            self._ref.set_column_at(aslice, a_column)
        else:
            raise IndexError

    def __getitem__(self, aslice):
        """By default, this class recognize from top to bottom
        from left to right"""
        index = aslice
        is_sheet = compact.is_string(type(aslice)) and hasattr(
            self._ref, "named_column_at"
        )
        if is_sheet:
            return self._ref.named_column_at(aslice)

        elif isinstance(aslice, slice):
            my_range = utils.analyse_slice(
                aslice, self._ref.number_of_columns()
            )
            results = []
            for i in my_range:
                results.append(self._ref.column_at(i))
            return results

        if isinstance(aslice, str):
            index = utils.excel_column_index(aslice)
        if utils.abs(index) in self._ref.column_range():
            return self._ref.column_at(index)

        else:
            raise IndexError

    def __iadd__(self, other):
        """Overload += sign

        :return: self
        """
        if isinstance(other, compact.OrderedDict):
            self._ref.extend_columns(copy.deepcopy(other))
        elif isinstance(other, list):
            self._ref.extend_columns(copy.deepcopy(other))
        elif hasattr(other, "get_internal_array"):
            self._ref.extend_columns_with_rows(
                copy.deepcopy(other.get_internal_array())
            )
        else:
            raise TypeError

        return self

    def __add__(self, other):
        """Overload + sign
        :return: new instance
        """
        new_instance = self._ref.clone()
        if isinstance(other, compact.OrderedDict):
            new_instance.extend_columns(copy.deepcopy(other))
        elif isinstance(other, list):
            new_instance.extend_columns(copy.deepcopy(other))
        elif hasattr(other, "get_internal_array"):
            new_instance.extend_columns_with_rows(
                copy.deepcopy(other.get_internal_array())
            )
        else:
            raise TypeError

        return new_instance

    def __getattr__(self, attr):
        """
        Refer to sheet.column.name
        """
        the_attr = attr
        if attr not in self._ref.colnames:
            the_attr = the_attr.replace("_", " ")
            if the_attr not in self._ref.colnames:
                raise AttributeError("%s is not found" % attr)

        return self._ref.named_column_at(the_attr)

    def format(self, column_index=None, formatter=None, format_specs=None):
        """Format a column"""
        if column_index is not None:
            self._handle_one_formatter(column_index, formatter)
        elif format_specs:
            for spec in format_specs:
                self._handle_one_formatter(spec[0], spec[1])

    def _handle_one_formatter(self, columns, theformatter):
        new_indices = columns
        if len(self._ref.colnames) > 0:
            new_indices = utils.names_to_indices(columns, self._ref.colnames)
        converter = utils.CommonPropertyAmongRowNColumn.get_converter(
            theformatter
        )

        if isinstance(new_indices, list):
            for rcolumn in self._ref.column_range():
                if rcolumn in new_indices:
                    for row in self._ref.row_range():
                        value = self._ref.cell_value(row, rcolumn)
                        value = converter(value)
                        self._ref.cell_value(row, rcolumn, value)
        else:
            for row in self._ref.row_range():
                value = self._ref.cell_value(row, new_indices)
                value = converter(value)
                self._ref.cell_value(row, new_indices, value)
