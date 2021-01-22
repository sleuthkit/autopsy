"""
    pyexcel_io.database.common
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Common classes shared among database importers and exporters

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""


class DjangoModelExportAdapter(object):
    """ django export parameter holder """

    def __init__(self, model, export_columns=None):
        self.model = model
        self.export_columns = export_columns

    @property
    def name(self):
        """ get database table name """
        return self.get_name()

    def get_name(self):
        """ get database table name """
        return self.model._meta.model_name


class DjangoModelImportAdapter(DjangoModelExportAdapter):
    """ parameter holder for django data import """

    class InOutParameter(object):
        """ local class to manipulate variable io """

        def __init__(self):
            self.output = None
            self.input = None

    def __init__(self, model):
        DjangoModelExportAdapter.__init__(self, model)
        self._column_names = self.InOutParameter()
        self._column_name_mapping_dict = self.InOutParameter()
        self._row_initializer = self.InOutParameter()
        self._process_parameters()

    @property
    def row_initializer(self):
        """ contructor for a database table entry """
        return self._row_initializer.output

    @property
    def column_names(self):
        """ the desginated database column names """
        return self._column_names.output

    @property
    def column_name_mapping_dict(self):
        """ if not the same, a mapping dictionary is looked up"""
        return self._column_name_mapping_dict.output

    @row_initializer.setter
    def row_initializer(self, a_function):
        """ set the contructor """
        self._row_initializer.input = a_function
        self._process_parameters()

    @column_names.setter
    def column_names(self, column_names):
        """ set the column names """
        self._column_names.input = column_names
        self._process_parameters()

    @column_name_mapping_dict.setter
    def column_name_mapping_dict(self, mapping_dict):
        """ set the mapping dict """
        self._column_name_mapping_dict.input = mapping_dict
        self._process_parameters()

    def _process_parameters(self):
        if self._row_initializer.input is None:
            self._row_initializer.output = None
        else:
            self._row_initializer.output = self._row_initializer.input
        if isinstance(self._column_name_mapping_dict.input, list):
            self._column_names.output = self._column_name_mapping_dict.input
            self._column_name_mapping_dict.output = None
        elif isinstance(self._column_name_mapping_dict.input, dict):

            if self._column_names.input:
                self._column_names.output = []
                indices = []
                for index, name in enumerate(self._column_names.input):
                    if name in self._column_name_mapping_dict.input:
                        self._column_names.output.append(
                            self._column_name_mapping_dict.input[name]
                        )
                        indices.append(index)
                self._column_name_mapping_dict.output = indices
        if self._column_names.output is None:
            self._column_names.output = self._column_names.input


class DjangoModelExporter(object):
    """ public interface for django model export """

    def __init__(self):
        self.adapters = []

    def append(self, import_adapter):
        """ store model parameter for more than one model """
        self.adapters.append(import_adapter)


class DjangoModelImporter(object):
    """ public interface for django model import """

    def __init__(self):
        self._adapters = {}

    def append(self, import_adapter):
        """ store model parameter for more than one model """
        self._adapters[import_adapter.get_name()] = import_adapter

    def get(self, name):
        """ get a parameter out """
        return self._adapters.get(name, None)


class SQLTableExportAdapter(DjangoModelExportAdapter):
    """ parameter holder for sql table data export """

    def __init__(self, model, export_columns=None):
        DjangoModelExportAdapter.__init__(self, model, export_columns)
        self.table = model

    def get_name(self):
        return getattr(self.table, "__tablename__", None)


class SQLTableImportAdapter(DjangoModelImportAdapter):
    """ parameter holder for sqlalchemy table import """

    def __init__(self, model):
        DjangoModelImportAdapter.__init__(self, model)
        self.table = model

    def get_name(self):
        return getattr(self.table, "__tablename__", None)


class SQLTableExporter(DjangoModelExporter):
    """ public interface for sql table export """

    def __init__(self, session):
        DjangoModelExporter.__init__(self)
        self.session = session


class SQLTableImporter(DjangoModelImporter):
    """ public interface to do data import via sqlalchemy """

    def __init__(self, session):
        DjangoModelImporter.__init__(self)
        self.session = session
