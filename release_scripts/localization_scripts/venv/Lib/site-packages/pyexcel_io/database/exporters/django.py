"""
    pyexcel_io.database.django
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    The lower level handler for django import and export

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from pyexcel_io.plugin_api import IReader
from pyexcel_io.database.querysets import QuerysetsReader


class DjangoModelReader(QuerysetsReader):
    """Read from django model"""

    def __init__(self, model, export_columns=None, **keywords):
        self.__model = model
        if export_columns:
            column_names = export_columns
        else:
            column_names = sorted(
                [field.attname for field in self.__model._meta.concrete_fields]
            )
        QuerysetsReader.__init__(
            self, self.__model.objects.all(), column_names, **keywords
        )


class DjangoBookReader(IReader):
    """ read django models """

    def __init__(self, exporter, _, **keywords):
        self.exporter = exporter
        self.keywords = keywords
        self.content_array = self.exporter.adapters

    def read_sheet(self, native_sheet_index):
        native_sheet = self.content_array[native_sheet_index]
        reader = DjangoModelReader(
            native_sheet.model, export_columns=native_sheet.export_columns
        )
        return reader

    def close(self):
        pass
