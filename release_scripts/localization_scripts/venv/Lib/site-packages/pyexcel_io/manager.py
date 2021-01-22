"""
    pyexcel_io.manager
    ~~~~~~~~~~~~~~~~~~~

    Control file streams

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from pyexcel_io._compact import BytesIO, StringIO

MIME_TYPES = {}
FILE_TYPES = ()
TEXT_STREAM_TYPES = []
BINARY_STREAM_TYPES = []


def register_stream_type(file_type, stream_type):
    """
    keep track of stream type for different file formats
    """
    if stream_type == "text":
        TEXT_STREAM_TYPES.append(file_type)
    elif stream_type == "binary":
        BINARY_STREAM_TYPES.append(file_type)


def get_io(file_type):
    """A utility function to help you generate a correct io stream

    :param file_type: a supported file type
    :returns: a appropriate io stream, None otherwise
    """
    __file_type = None
    if file_type:
        __file_type = file_type.lower()

    if __file_type in TEXT_STREAM_TYPES:
        return StringIO()

    elif __file_type in BINARY_STREAM_TYPES:
        return BytesIO()

    else:
        return None


def get_io_type(file_type):
    """A utility function to help you generate a correct io stream

    :param file_type: a supported file type
    :returns: a appropriate io stream, None otherwise
    """
    __file_type = None
    if file_type:
        __file_type = file_type.lower()

    if __file_type in TEXT_STREAM_TYPES:
        return "string"

    elif __file_type in BINARY_STREAM_TYPES:
        return "bytes"

    else:
        return None


def register_a_file_type(file_type, stream_type, mime_type):
    """
    keep track of file format supports by this library
    """
    global FILE_TYPES
    FILE_TYPES += (file_type,)
    stream_type = stream_type
    if mime_type is not None:
        MIME_TYPES[file_type] = mime_type
    register_stream_type(file_type, stream_type)
