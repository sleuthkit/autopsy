"""
    pyexcel.plugin.parsers.sqlalchemy
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Export data into database datables

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.parser import DbParser

from pyexcel_io import get_data, iget_data
from pyexcel_io.database import common as sql


class SQLAlchemyExporter(DbParser):
    """export data via sqlalchmey"""

    def parse_db(
        self, argument, export_columns_list=None, on_demand=False, **keywords
    ):
        session, tables = argument
        exporter = sql.SQLTableExporter(session)
        if export_columns_list is None:
            export_columns_list = [None] * len(tables)
        for table, export_columns in zip(tables, export_columns_list):
            adapter = sql.SQLTableExportAdapter(table, export_columns)
            exporter.append(adapter)
        if on_demand:
            sheets, _ = iget_data(
                exporter, file_type=self._file_type, **keywords
            )
        else:
            sheets = get_data(exporter, file_type=self._file_type, **keywords)
        return sheets
