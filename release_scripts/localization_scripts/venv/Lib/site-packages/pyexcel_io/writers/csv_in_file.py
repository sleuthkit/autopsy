from pyexcel_io import constants
from pyexcel_io.plugin_api import IWriter
from pyexcel_io.writers.csv_sheet import CSVFileWriter


class CsvFileWriter(IWriter):
    def __init__(self, file_alike_object, file_type, **keywords):
        self._file_alike_object = file_alike_object
        self._keywords = keywords
        if file_type == constants.FILE_FORMAT_TSV:
            self._keywords["dialect"] = constants.KEYWORD_TSV_DIALECT
        self.__index = 0
        self.handlers = []

    def create_sheet(self, name):
        writer = CSVFileWriter(
            self._file_alike_object,
            name,
            sheet_index=self.__index,
            **self._keywords
        )
        self.__index = self.__index + 1
        self.handlers.append(writer)
        return writer

    def close(self):
        for writer in self.handlers:
            writer.close()
        self.handlers = []
