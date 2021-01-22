"""
    pyexcel_io.database.sql
    ~~~~~~~~~~~~~~~~~~~~~~~~~

    The lower level handler for database import and export

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import pyexcel_io.constants as constants
from pyexcel_io.utils import is_empty_array, swap_empty_string_for_none
from pyexcel_io.plugin_api import IWriter, ISheetWriter


class PyexcelSQLSkipRowException(Exception):
    """
    Raised this exception to skipping a row
    while data import
    """

    pass


class SQLTableWriter(ISheetWriter):
    """Write to a table"""

    def __init__(
        self, importer, adapter, auto_commit=True, bulk_size=1000, **keywords
    ):
        self.__auto_commit = auto_commit
        self.__count = 0
        self.__bulk_size = bulk_size
        self.adapter = adapter
        self.importer = importer

    def write_row(self, array):
        if is_empty_array(array):
            print(constants.MESSAGE_EMPTY_ARRAY)
        else:
            new_array = swap_empty_string_for_none(array)
            try:
                self._write_row(new_array)
            except PyexcelSQLSkipRowException:
                print(constants.MESSAGE_IGNORE_ROW)
                print(new_array)

    def _write_row(self, array):
        new_array = array
        if self.adapter.column_name_mapping_dict:
            another_new_array = []
            for index, element in enumerate(new_array):
                if index in self.adapter.column_name_mapping_dict:
                    another_new_array.append(element)
            new_array = another_new_array
        row = dict(zip(self.adapter.column_names, new_array))
        obj = None
        if self.adapter.row_initializer:
            # allow initinalizer to return None
            # if skipping is needed
            obj = self.adapter.row_initializer(row)
        if obj is None:
            obj = self.adapter.table()
            for name in self.adapter.column_names:
                setattr(obj, name, row[name])
        self.importer.session.add(obj)
        if self.__auto_commit and self.__bulk_size != float("inf"):
            self.__count += 1
            if self.__count % self.__bulk_size == 0:
                self.importer.session.commit()

    def close(self):
        if self.__auto_commit:
            self.importer.session.commit()


class SQLBookWriter(IWriter):
    """ write data into database tables via sqlalchemy """

    def __init__(self, file_content, _, auto_commit=True, **keywords):
        self.__importer = file_content
        self.__auto_commit = auto_commit

    def create_sheet(self, sheet_name):
        sheet_writer = None
        adapter = self.__importer.get(sheet_name)
        if adapter:
            sheet_writer = SQLTableWriter(
                self.__importer, adapter, auto_commit=self.__auto_commit
            )
        else:
            raise Exception(
                "Sheet: %s does not match any given tables." % sheet_name
                + "Please be aware of case sensitivity."
            )

        return sheet_writer

    def close(self):
        pass
