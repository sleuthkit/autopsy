"""
    pyexcel_io.database.django
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    The lower level handler for django import and export

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import logging

import pyexcel_io.constants as constants
from pyexcel_io.utils import is_empty_array, swap_empty_string_for_none
from pyexcel_io.plugin_api import IWriter, ISheetWriter

log = logging.getLogger(__name__)


class DjangoModelWriter(ISheetWriter):
    """ import data into a django model """

    def __init__(self, importer, adapter, batch_size=None, bulk_save=True):
        self.batch_size = batch_size
        self.model = adapter.model
        self.column_names = adapter.column_names
        self.mapdict = adapter.column_name_mapping_dict
        self.initializer = adapter.row_initializer
        self.objs = []
        self.bulk_save = bulk_save
        self.adapter = adapter

    def write_row(self, array):
        if is_empty_array(array):
            print(constants.MESSAGE_EMPTY_ARRAY)
        else:
            new_array = swap_empty_string_for_none(array)
            if self.mapdict:
                another_new_array = []
                for index, element in enumerate(new_array):
                    if index in self.mapdict:
                        another_new_array.append(element)
                new_array = another_new_array
            model_to_be_created = new_array
            if self.initializer is not None:
                model_to_be_created = self.initializer(new_array)
            if model_to_be_created:
                row = dict(zip(self.column_names, model_to_be_created))
                self.objs.append(self.model(**row))

    # else
    # skip the row

    def close(self):
        if self.bulk_save:
            self.model.objects.bulk_create(
                self.objs, batch_size=self.batch_size
            )
        else:
            for an_object in self.objs:
                an_object.save()


class DjangoBookWriter(IWriter):
    """ write data into django models """

    def __init__(self, exporter, _, **keywords):
        self.importer = exporter
        self._keywords = keywords

    def create_sheet(self, sheet_name):
        sheet_writer = None
        model = self.importer.get(sheet_name)
        if model:
            sheet_writer = DjangoModelWriter(
                self.importer,
                model,
                batch_size=self._keywords.get("batch_size", None),
                bulk_save=self._keywords.get("bulk_save", True),
            )
        else:
            raise Exception(
                "Sheet: %s does not match any given models." % sheet_name
                + "Please be aware of case sensitivity."
            )

        return sheet_writer

    def close(self):
        pass
