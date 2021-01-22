"""
    pyexcel.plugins.sources.sqlalchemy
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of sqlalchemy sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel_io.constants import DB_SQL
from .db_sources import BookDbSource, SheetDbSource


class SheetSQLAlchemySource(SheetDbSource):
    """
    SQLAlchemy channeled sql database as data source
    """

    def __init__(
        self, session, table, export_columns=None, sheet_name=None, **keywords
    ):
        self.__session = session
        self.__table = table
        SheetDbSource.__init__(
            self,
            DB_SQL,
            export_columns=export_columns,
            sheet_name=sheet_name,
            **keywords
        )

    def get_export_params(self):
        return (self.__session, [self.__table])

    def get_import_params(self):
        return (self.__session, self.__table)


class BookSQLSource(BookDbSource):
    """
    SQLAlchemy bridged multiple table data source
    """

    def __init__(self, session, tables, **keywords):
        self.__session = session
        self.__tables = tables
        BookDbSource.__init__(self, DB_SQL, **keywords)

    def get_params(self):
        return (self.__session, self.__tables)
