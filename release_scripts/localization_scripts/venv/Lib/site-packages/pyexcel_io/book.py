"""
    pyexcel_io.book
    ~~~~~~~~~~~~~~~~~~~

    The io interface to file extensions

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import warnings

import pyexcel_io.manager as manager
from pyexcel_io._compact import OrderedDict, isstream

from .constants import MESSAGE_ERROR_03, MESSAGE_WRONG_IO_INSTANCE

DEPRECATED_SINCE_0_6_0 = (
    "Deprecated since v0.6.0! "
    + "Although backward compatibility is preserved, "
    + "it is recommended to upgrade to get new features."
)


class RWInterface(object):
    """
    The common methods for book reader and writer
    """

    stream_type = None

    def __init__(self):
        warnings.warn(DEPRECATED_SINCE_0_6_0)
        self._file_type = None

    def open(self, file_name, **keywords):
        """open a file for read or write"""
        raise NotImplementedError("Please implement this method")

    def open_stream(self, file_stream, **keywords):
        """open a file stream for read or write"""
        raise NotImplementedError("Please implement this method")

    def open_content(self, file_stream, **keywords):
        """open a file content for read or write"""
        raise NotImplementedError("Please implement this method")

    def set_type(self, file_type):
        """
        set the file type for the instance

        file type is needed when a third party library could
        handle more than one file type"""
        self._file_type = file_type

    def close(self):
        """
        close the file handle if necessary
        """
        pass

    # implement context manager

    def __enter__(self):
        return self

    def __exit__(self, a_type, value, traceback):
        self.close()


class BookReader(RWInterface):
    """
    Standard book reader
    """

    def __init__(self):
        super(BookReader, self).__init__()
        self._file_name = None
        self._file_stream = None
        self._keywords = None
        self._native_book = None

    def open(self, file_name, **keywords):
        """
        open a file with unlimited keywords

        keywords are passed on to individual readers
        """
        self._file_name = file_name
        self._keywords = keywords

    def open_stream(self, file_stream, **keywords):
        """
        open a file with unlimited keywords for reading

        keywords are passed on to individual readers
        """
        if isstream(file_stream):
            from io import UnsupportedOperation

            try:
                file_stream.seek(0)
            except UnsupportedOperation:
                # python 3
                file_stream = _convert_content_to_stream(
                    file_stream.read(), self._file_type
                )

            self._file_stream = file_stream
            self._keywords = keywords
        else:
            raise IOError(MESSAGE_WRONG_IO_INSTANCE)

    def open_content(self, file_content, **keywords):
        """
        read file content as if it is a file stream with
        unlimited keywords for reading

        keywords are passed on to individual readers
        """
        file_stream = _convert_content_to_stream(file_content, self._file_type)
        self.open_stream(file_stream, **keywords)

    def read_sheet_by_name(self, sheet_name):
        """
        read a named sheet from a excel data book
        """
        named_contents = [
            content
            for content in self._native_book
            if content.name == sheet_name
        ]
        if len(named_contents) == 1:
            return {named_contents[0].name: self.read_sheet(named_contents[0])}

        else:
            raise ValueError("Cannot find sheet %s" % sheet_name)

    def read_sheet_by_index(self, sheet_index):
        """
        read an indexed sheet from a excel data book
        """
        try:
            sheet = self._native_book[sheet_index]
            return {sheet.name: self.read_sheet(sheet)}

        except IndexError:
            self.close()
            raise

    def read_all(self):
        """
        read everything from a excel data book
        """
        result = OrderedDict()
        for sheet in self._native_book:
            result[sheet.name] = self.read_sheet(sheet)
        return result

    def read_many(self, sheets):
        """
        read everything from a excel data book
        """
        result = OrderedDict()
        for sheet in sheets:
            if isinstance(sheet, int):
                result.update(self.read_sheet_by_index(sheet))
            else:
                result.update(self.read_sheet_by_name(sheet))
        return result

    def read_sheet(self, native_sheet):
        """
        Return a context specific sheet from a native sheet
        """
        raise NotImplementedError("Please implement this method")


class BookWriter(RWInterface):
    """
    Standard book writer
    """

    def __init__(self):
        super(BookWriter, self).__init__()
        self._file_alike_object = None
        self._keywords = None

    def open(self, file_name, **keywords):
        """
        open a file with unlimited keywords for writing

        keywords are passed on to individual writers
        """
        self._file_alike_object = file_name
        self._keywords = keywords

    def open_stream(self, file_stream, **keywords):
        """
        open a file stream with unlimited keywords for writing

        keywords are passed on to individual writers
        """
        if not isstream(file_stream):
            raise IOError(MESSAGE_ERROR_03)

        self.open(file_stream, **keywords)

    def open_content(self, file_stream, **keywords):
        """open a file content for read or write"""
        raise Exception("Normal writer would not need this interface")

    def write(self, incoming_dict):
        """
        write a dictionary into an excel file
        """
        for sheet_name in incoming_dict:
            sheet_writer = self.create_sheet(sheet_name)
            if sheet_writer:
                sheet_writer.write_array(incoming_dict[sheet_name])
                sheet_writer.close()
            else:
                raise Exception("Cannot create a sheet writer!")

    def create_sheet(self, sheet_name):
        """
        implement this method for easy extension
        """
        raise NotImplementedError("Please implement create_sheet()")


def _convert_content_to_stream(file_content, file_type):
    stream = manager.get_io(file_type)
    target_content_type = manager.get_io_type(file_type)
    needs_encode = target_content_type == "bytes" and not isinstance(
        file_content, bytes
    )
    needs_decode = target_content_type == "string" and isinstance(
        file_content, bytes
    )
    if needs_encode:
        file_content = file_content.encode("utf-8")
    elif needs_decode:
        file_content = file_content.decode("utf-8")
    stream.write(file_content)
    stream.seek(0)
    return stream
