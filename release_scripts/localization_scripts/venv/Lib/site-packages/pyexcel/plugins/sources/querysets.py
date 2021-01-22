"""
    pyexcel.plugins.sources.querysets
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of querysets

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel import constants as constants
from pyexcel.source import AbstractSource

from pyexcel_io import get_data
from pyexcel_io.constants import DB_QUERYSET
from . import params


# pylint: disable=W0223
class SheetQuerySetSource(AbstractSource):
    """
    Database query set as data source

    SQLAlchemy and Django query sets are supported
    """

    fields = [params.COLUMN_NAMES, params.QUERY_SETS]
    targets = (constants.SHEET,)
    actions = (constants.READ_ACTION,)
    attributes = []

    def __init__(
        self,
        column_names,
        query_sets,
        sheet_name=None,
        row_renderer=None,
        start_row=0,
        row_limit=-1,
        start_column=None,
        column_limit=None,
        skip_row_func=None,
        skip_column_func=None,
        **keywords
    ):
        self.__sheet_name = sheet_name
        if self.__sheet_name is None:
            self.__sheet_name = constants.DEFAULT_SHEET_NAME
        self.__column_names = column_names
        self.__query_sets = query_sets
        self.__row_renderer = row_renderer
        self.__start_row = start_row
        self.__row_limit = row_limit
        self.__skip_row_func = skip_row_func

        if start_column is None:
            print("start_column is ignored")
        if column_limit is None:
            print("column_limit is ignored")
        if skip_column_func is None:
            print("skip_column_func is ignored")
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        local_params = dict(
            row_renderer=self.__row_renderer,
            start_row=self.__start_row,
            row_limit=self.__row_limit,
        )
        if self.__skip_row_func is not None:
            local_params["skip_row_func"] = self.__skip_row_func

        data = get_data(
            self.__query_sets,
            file_type=DB_QUERYSET,
            column_names=self.__column_names,
            **local_params
        )
        return data
