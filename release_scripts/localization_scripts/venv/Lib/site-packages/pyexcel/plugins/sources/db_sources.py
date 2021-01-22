"""
    pyexcel.plugins.sources.db_sources
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Generic database sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.source import AbstractSource
from pyexcel._compact import PY2
from pyexcel.internal import PARSER, RENDERER

from . import params

NO_COLUMN_NAMES = "Only sheet with column names is accepted"


class SheetDbSource(AbstractSource):
    """
    SQLAlchemy channeled sql database as data source
    """

    def __init__(
        self,
        db_type,
        export_columns=None,
        sheet_name=None,
        parser_library=None,
        renderer_library=None,
        **keywords
    ):
        self._db_type = db_type
        self.__export_columns = export_columns
        self.__sheet_name = sheet_name
        self.__parser_library = parser_library
        self.__renderer_library = renderer_library
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        aparser = PARSER.get_a_plugin(self._db_type, self.__parser_library)
        export_params = self.get_export_params()
        data = aparser.parse_file_stream(
            export_params,
            export_columns_list=[self.__export_columns],
            **self._keywords
        )
        if self.__sheet_name is not None:
            _set_dictionary_key(data, self.__sheet_name)
        return data

    def get_export_params(self):
        """form the parameters for the db renderer"""
        pass

    def write_data(self, sheet):
        arender = RENDERER.get_a_plugin(self._db_type, self.__renderer_library)
        init_func, map_dict = _transcode_sheet_db_keywords(self._keywords)
        import_params = self.get_import_params()
        arender.render_sheet_to_stream(
            import_params,
            sheet,
            init=init_func,
            mapdict=map_dict,
            **self._keywords
        )

    def get_import_params(self):
        """form the parameters for the db parser"""
        pass


class BookDbSource(AbstractSource):
    """
    multiple Django table as data source
    """

    def __init__(
        self, db_type, parser_library=None, renderer_library=None, **keywords
    ):
        self.__db_type = db_type
        self.__parser_library = parser_library
        self.__renderer_library = renderer_library
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        aparser = PARSER.get_a_plugin(self.__db_type, self.__parser_library)
        export_params = self.get_params()
        data = aparser.parse_file_stream(export_params, **self._keywords)
        return data

    def get_params(self):
        """form the paraneters for the db parser and renderer"""
        pass

    def get_source_info(self):
        return self.__db_type, None

    def write_data(self, book):
        arender = RENDERER.get_a_plugin(
            self.__db_type, self.__renderer_library
        )
        init_funcs, map_dicts = _transcode_book_db_keywords(self._keywords)

        import_params = self.get_params()
        arender.render_book_to_stream(
            import_params,
            book,
            inits=init_funcs,
            mapdicts=map_dicts,
            **self._keywords
        )


def _set_dictionary_key(adict, sheet_name):
    if PY2:
        (old_sheet_name, array) = adict.items()[0]
    else:
        (old_sheet_name, array) = list(adict.items())[0]
    adict[sheet_name] = array
    adict.pop(old_sheet_name)


def _transcode_sheet_db_keywords(keywords):
    if params.INITIALIZER in keywords:
        init_func = keywords.pop(params.INITIALIZER)
    else:
        init_func = None
    if params.MAPDICT in keywords:
        map_dict = keywords.pop(params.MAPDICT)
    else:
        map_dict = None

    return init_func, map_dict


def _transcode_book_db_keywords(keywords):
    if params.INITIALIZERS in keywords:
        init_funcs = keywords.pop(params.INITIALIZERS)
    else:
        init_funcs = None
    if params.MAPDICTS in keywords:
        map_dicts = keywords.pop(params.MAPDICTS)
    else:
        map_dicts = None

    return init_funcs, map_dicts
