"""
    pyexcel.parser
    ~~~~~~~~~~~~~~~~~~~

    Extract tabular data from external file, stream or content

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.internal import garbagecollector as gc


class AbstractParser(object):
    """
    Parsing data from tabular data such as excel file
    """

    def __init__(self, file_type):
        self._file_type = file_type

    def parse_file(self, file_name, **keywords):
        """
        Parse data from a physical file
        """
        raise NotImplementedError("parse_file is not implemented")

    def parse_file_stream(self, file_stream, **keywords):
        """
        Parse data from a file stream
        """
        raise NotImplementedError("parse_file_stream is not implemented")

    def parse_file_content(self, file_content, **keywords):
        """
        Parse data from a given file content
        """
        raise NotImplementedError("parse_file_content is not implemented")

    def _free_me_up_later(self, reader):
        gc.append(reader)


class DbParser(AbstractParser):
    """
    Change interface for db parser
    """

    def parse_file(self, file_name, **keywords):
        raise Exception("parse_file is not supported")

    def parse_file_stream(self, file_stream, **keywords):
        return self.parse_db(file_stream, **keywords)

    def parse_file_content(self, file_content, **keywords):
        raise Exception("parse_file_content is not supported")

    def parse_db(self, argument, **keywords):
        """
        Parse data from database
        """
        raise NotImplementedError("parse_db is not implemented")
