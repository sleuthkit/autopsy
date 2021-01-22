"""
    pyexcel.internal.sheets.row
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Generic table row

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import copy
import types

from pyexcel import _compact as compact

from . import _shared as utils


class Row(utils.CommonPropertyAmongRowNColumn):
    """Represent row of a matrix

    .. table:: "example.csv"

        = = =
        1 2 3
        4 5 6
        7 8 9
        = = =

    Above column manipulation can be performed on rows similarly. This section
    will not repeat the same example but show some advance usages.


        >>> import pyexcel as pe
        >>> data = [[1,2,3], [4,5,6], [7,8,9]]
        >>> m = pe.internal.sheets.Matrix(data)
        >>> m.row[0:2]
        [[1, 2, 3], [4, 5, 6]]
        >>> m.row[0:3] = [0, 0, 0]
        >>> m.row[2]
        [0, 0, 0]
        >>> del m.row[0:2]
        >>> m.row[0]
        [0, 0, 0]

    """

    def select(self, indices):
        """Delete row indices other than specified

        Examples:

            >>> import pyexcel as pe
            >>> data = [[1],[2],[3],[4],[5],[6],[7],[9]]
            >>> sheet = pe.Sheet(data)
            >>> sheet
            pyexcel sheet:
            +---+
            | 1 |
            +---+
            | 2 |
            +---+
            | 3 |
            +---+
            | 4 |
            +---+
            | 5 |
            +---+
            | 6 |
            +---+
            | 7 |
            +---+
            | 9 |
            +---+
            >>> sheet.row.select([1,2,3,5])
            >>> sheet
            pyexcel sheet:
            +---+
            | 2 |
            +---+
            | 3 |
            +---+
            | 4 |
            +---+
            | 6 |
            +---+

        """
        new_indices = []
        if compact.is_array_type(indices, str):
            new_indices = utils.names_to_indices(indices, self._ref.rownames)
        else:
            new_indices = indices
        to_remove = []
        for index in self._ref.row_range():
            if index not in new_indices:
                to_remove.append(index)
        self._ref.filter(row_indices=to_remove)

    def __delitem__(self, locator):
        """Override the operator to delete items

        Examples:

            >>> import pyexcel as pe
            >>> data = [[1],[2],[3],[4],[5],[6],[7],[9]]
            >>> sheet = pe.Sheet(data)
            >>> sheet
            pyexcel sheet:
            +---+
            | 1 |
            +---+
            | 2 |
            +---+
            | 3 |
            +---+
            | 4 |
            +---+
            | 5 |
            +---+
            | 6 |
            +---+
            | 7 |
            +---+
            | 9 |
            +---+
            >>> del sheet.row[1,2,3,5]
            >>> sheet
            pyexcel sheet:
            +---+
            | 1 |
            +---+
            | 5 |
            +---+
            | 7 |
            +---+
            | 9 |
            +---+

        """
        if compact.is_string(type(locator)):
            self._ref.delete_named_row_at(locator)
        elif compact.is_tuple_consists_of_strings(locator):
            indices = utils.names_to_indices(list(locator), self._ref.rownames)
            self._ref.delete_rows(indices)
        elif isinstance(locator, slice):
            my_range = utils.analyse_slice(locator, self._ref.number_of_rows())
            self._ref.delete_rows(my_range)
        elif isinstance(locator, tuple):
            self._ref.filter(row_indices=(list(locator)))
        elif isinstance(locator, list):
            self._ref.filter(row_indices=locator)
        elif isinstance(locator, types.LambdaType):
            self._delete_rows_by_content(locator)
        elif isinstance(locator, types.FunctionType):
            self._delete_rows_by_content(locator)
        else:
            self._ref.delete_rows([locator])

    def __getattr__(self, attr):
        """
        Refer to sheet.row.name
        """
        the_attr = attr
        if attr not in self._ref.rownames:
            the_attr = the_attr.replace("_", " ")
            if the_attr not in self._ref.rownames:
                raise AttributeError("%s is not found" % attr)

        return self._ref.named_row_at(the_attr)

    def _delete_rows_by_content(self, locator):
        to_remove = []
        for index, row in enumerate(self._ref.rows()):
            if locator(index, row):
                to_remove.append(index)
        if len(to_remove) > 0:
            self._ref.delete_rows(to_remove)

    def __setitem__(self, aslice, a_row):
        """Override the operator to set items"""
        if compact.is_string(type(aslice)):
            self._ref.set_named_row_at(aslice, a_row)
        elif isinstance(aslice, slice):
            my_range = utils.analyse_slice(aslice, self._ref.number_of_rows())
            for i in my_range:
                self._ref.set_row_at(i, a_row)
        else:
            self._ref.set_row_at(aslice, a_row)

    def __getitem__(self, aslice):
        """By default, this class recognize from top to bottom
        from left to right"""
        index = aslice
        if compact.is_string(type(aslice)):
            return self._ref.named_row_at(aslice)
        elif isinstance(aslice, slice):
            my_range = utils.analyse_slice(aslice, self._ref.number_of_rows())
            results = []
            for i in my_range:
                results.append(self._ref.row_at(i))
            return results

        if abs(index) in self._ref.row_range():
            return self._ref.row_at(index)
        else:
            raise IndexError

    def __iadd__(self, other):
        """Overload += sign

        :return: self
        """
        if isinstance(other, compact.OrderedDict):
            self._ref.extend_rows(copy.deepcopy(other))
        elif isinstance(other, list):
            self._ref.extend_rows(copy.deepcopy(other))
        elif hasattr(other, "get_internal_array"):
            self._ref.extend_rows(copy.deepcopy(other.get_internal_array()))
        else:
            raise TypeError
        return self

    def __add__(self, other):
        """Overload + sign

        :return: new instance
        """
        new_instance = self._ref.clone()
        if isinstance(other, compact.OrderedDict):
            new_instance.extend_rows(copy.deepcopy(other))
        elif isinstance(other, list):
            new_instance.extend_rows(copy.deepcopy(other))
        elif hasattr(other, "get_internal_array"):
            new_instance.extend_rows(copy.deepcopy(other.get_internal_array()))
        else:
            raise TypeError
        return new_instance

    def format(self, row_index=None, formatter=None, format_specs=None):
        """Format a row"""
        if row_index is not None:
            self._handle_one_formatter(row_index, formatter)
        elif format_specs:
            for spec in format_specs:
                self._handle_one_formatter(spec[0], spec[1])

    def _handle_one_formatter(self, rows, theformatter):
        new_indices = rows
        if len(self._ref.rownames) > 0:
            new_indices = utils.names_to_indices(rows, self._ref.rownames)

        converter = utils.CommonPropertyAmongRowNColumn.get_converter(
            theformatter
        )
        if isinstance(new_indices, list):
            for rindex in self._ref.row_range():
                if rindex in new_indices:
                    for column in self._ref.column_range():
                        value = self._ref.cell_value(rindex, column)
                        value = converter(value)
                        self._ref.cell_value(rindex, column, value)
        else:
            for column in self._ref.column_range():
                value = self._ref.cell_value(new_indices, column)
                value = converter(value)
                self._ref.cell_value(new_indices, column, value)
