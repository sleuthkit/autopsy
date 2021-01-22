"""
    pyexcel.plugins.sources.django
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of django sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel_io.constants import DB_DJANGO
from .db_sources import BookDbSource, SheetDbSource


class SheetDjangoSource(SheetDbSource):
    """
    Django model as data source
    """

    def __init__(
        self, model=None, export_columns=None, sheet_name=None, **keywords
    ):
        self.__model = model
        SheetDbSource.__init__(
            self,
            DB_DJANGO,
            export_columns=export_columns,
            sheet_name=sheet_name,
            **keywords
        )

    def get_export_params(self):
        return [self.__model]

    def get_import_params(self):
        return self.__model


class BookDjangoSource(BookDbSource):
    """
    multiple Django table as data source
    """

    def __init__(self, models, **keywords):
        self.__models = models
        BookDbSource.__init__(self, DB_DJANGO, **keywords)

    def get_params(self):
        return self.__models
