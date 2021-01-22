import zipfile

from pyexcel_io import constants
from pyexcel_io.plugin_api import IWriter
from pyexcel_io.writers.csvz_sheet import CSVZipSheetWriter


class CsvZipWriter(IWriter):
    """
    csvz writer

    It is better to store csv files as a csvz as it saves your disk space.
    Pyexcel-io had the facility to unzip it for you or you could use
    any other unzip software.
    """

    def __init__(self, file_name, file_type, **keywords):
        self._file_type = file_type
        self.zipfile = zipfile.ZipFile(file_name, "w", zipfile.ZIP_DEFLATED)
        self._keywords = keywords
        if file_type == constants.FILE_FORMAT_TSVZ:
            self._keywords["dialect"] = constants.KEYWORD_TSV_DIALECT

    def create_sheet(self, name):
        given_name = name
        if given_name is None:
            given_name = constants.DEFAULT_SHEET_NAME
        writer = CSVZipSheetWriter(
            self.zipfile, given_name, self._file_type[:3], **self._keywords
        )
        return writer

    def close(self):
        if self.zipfile:
            self.zipfile.close()
