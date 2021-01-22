"""
    pyexcel.plugin.parsers.django
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Export data into database datables

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.parser import DbParser

from pyexcel_io import get_data, iget_data
from pyexcel_io.database import common as django


class DjangoExporter(DbParser):
    """Export data from django model"""

    def parse_db(
        self, argument, export_columns_list=None, on_demand=True, **keywords
    ):
        models = argument
        exporter = django.DjangoModelExporter()
        if export_columns_list is None:
            export_columns_list = [None] * len(models)
        for model, export_columns in zip(models, export_columns_list):
            adapter = django.DjangoModelExportAdapter(model, export_columns)
            exporter.append(adapter)
        if on_demand:
            sheets, _ = iget_data(
                exporter, file_type=self._file_type, **keywords
            )
        else:
            sheets = get_data(exporter, file_type=self._file_type, **keywords)
        return sheets
