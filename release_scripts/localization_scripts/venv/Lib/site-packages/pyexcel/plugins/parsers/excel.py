"""
    pyexcel.plugins.parsers.excel
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Parsing excel sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.parser import AbstractParser

from pyexcel_io import get_data, iget_data


class ExcelParser(AbstractParser):
    """get data from excel files"""

    def parse_file(self, file_name, **keywords):
        return self._parse_any(file_name, **keywords)

    def parse_file_stream(self, file_stream, **keywords):
        return self._parse_any(
            file_stream, file_type=self._file_type, **keywords
        )

    def parse_file_content(self, file_content, **keywords):
        return self._parse_any(
            file_content, file_type=self._file_type, **keywords
        )

    def _parse_any(
        self, anything, on_demand=False, file_type=None, **keywords
    ):
        if on_demand:
            sheets, reader = iget_data(
                anything, file_type=file_type, **keywords
            )
            self._free_me_up_later(reader)
        else:
            sheets = get_data(anything, file_type=file_type, **keywords)
        return sheets
