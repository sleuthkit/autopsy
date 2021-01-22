from pyexcel_io.plugin_api import IReader
from pyexcel_io.database.querysets import QuerysetsReader


class QueryReader(IReader):
    def __init__(self, query_sets, _, column_names=None, **keywords):
        self.query_sets = query_sets
        self.column_names = column_names
        self.keywords = keywords
        self.content_array = [
            QuerysetsReader(
                self.query_sets, self.column_names, **self.keywords
            )
        ]

    def read_sheet(self, index):
        return self.content_array[index]

    def close(self):
        pass
