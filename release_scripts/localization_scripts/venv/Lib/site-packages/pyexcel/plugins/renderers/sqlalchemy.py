"""
    pyexcel.plugin.renderers.sqlalchemy
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Export data into database datables

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel._compact import OrderedDict
from pyexcel.internal import common as common
from pyexcel.renderer import DbRenderer

from pyexcel_io import save_data
from pyexcel_io.database import common as sql


class SQLAlchemyRenderer(DbRenderer):
    """Import data into database"""

    def render_sheet_to_stream(
        self, file_stream, sheet, init=None, mapdict=None, **keywords
    ):
        headers = common.get_sheet_headers(sheet)
        importer = sql.SQLTableImporter(file_stream[0])
        adapter = sql.SQLTableImportAdapter(file_stream[1])
        adapter.column_names = headers
        adapter.row_initializer = init
        adapter.column_name_mapping_dict = mapdict
        importer.append(adapter)
        save_data(
            importer,
            {adapter.get_name(): sheet.get_internal_array()},
            file_type=self._file_type,
            **keywords
        )

    def render_book_to_stream(
        self, file_stream, book, inits=None, mapdicts=None, **keywords
    ):
        session, tables = file_stream
        thebook = book
        initializers = inits
        colnames_array = common.get_book_headers_in_array(book)
        if initializers is None:
            initializers = [None] * len(tables)
        if mapdicts is None:
            mapdicts = [None] * len(tables)
        scattered = zip(tables, colnames_array, mapdicts, initializers)

        importer = sql.SQLTableImporter(session)
        for each_table in scattered:
            adapter = sql.SQLTableImportAdapter(each_table[0])
            adapter.column_names = each_table[1]
            adapter.column_name_mapping_dict = each_table[2]
            adapter.row_initializer = each_table[3]
            importer.append(adapter)
        to_store = OrderedDict()
        for sheet in thebook:
            # due book.to_dict() brings in column_names
            # which corrupts the data
            to_store[sheet.name] = sheet.get_internal_array()
        save_data(importer, to_store, file_type=self._file_type, **keywords)
