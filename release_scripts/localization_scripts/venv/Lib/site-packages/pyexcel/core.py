"""
    pyexcel.core
    ~~~~~~~~~~~~~~~~~~~

    A list of pyexcel signature functions

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import re

from pyexcel import constants as constants
from pyexcel import docstrings as docs
from pyexcel.book import Book, to_book
from pyexcel.sheet import Sheet
from pyexcel._compact import OrderedDict, append_doc, zip_longest
from pyexcel.internal import core as sources

from pyexcel_io import manager as manager

STARTS_WITH_DEST = "^dest_(.*)"
SAVE_AS_EXCEPTION = (
    "This function does not accept parameters for "
    + "pyexce.Sheet. Please use pyexcel.save_as instead."
)


@append_doc(docs.GET_SHEET)
def get_sheet(**keywords):
    """
    Get an instance of :class:`Sheet` from an excel source
    """
    sheet_params = {}
    for field in constants.VALID_SHEET_PARAMETERS:
        if field in keywords:
            sheet_params[field] = keywords.pop(field)
    named_content = sources.get_sheet_stream(**keywords)
    sheet = Sheet(named_content.payload, named_content.name, **sheet_params)
    return sheet


@append_doc(docs.GET_BOOK)
def get_book(**keywords):
    """
    Get an instance of :class:`Book` from an excel source
    """
    book_stream = sources.get_book_stream(**keywords)
    book = Book(
        book_stream.to_dict(),
        filename=book_stream.filename,
        path=book_stream.path,
    )
    return book


@append_doc(docs.IGET_BOOK)
def iget_book(**keywords):
    """
    Get an instance of :class:`BookStream` from an excel source

    First use case is to get all sheet names without extracting
    the sheets into memory.
    """
    return sources.get_book_stream(on_demand=True, **keywords)


@append_doc(docs.SAVE_AS)
def save_as(**keywords):
    """
    Save a sheet from a data source to another one
    """
    dest_keywords, source_keywords = _split_keywords(**keywords)
    sheet_params = {}
    for field in constants.VALID_SHEET_PARAMETERS:
        if field in source_keywords:
            sheet_params[field] = source_keywords.pop(field)
    sheet_stream = sources.get_sheet_stream(**source_keywords)
    output_sheet_name = sheet_stream.name
    if "sheet_name" in dest_keywords:
        output_sheet_name = dest_keywords["sheet_name"]
    sheet = Sheet(sheet_stream.payload, output_sheet_name, **sheet_params)
    return sources.save_sheet(sheet, **dest_keywords)


@append_doc(docs.ISAVE_AS)
def isave_as(**keywords):
    """
    Save a sheet from a data source to another one with less memory

    It is simliar to :meth:`pyexcel.save_as` except that it does
    not accept parameters for :class:`pyexcel.Sheet`. And it read
    when it writes.
    """
    dest_keywords, source_keywords = _split_keywords(**keywords)
    for field in constants.VALID_SHEET_PARAMETERS:
        if field in source_keywords:
            raise Exception(SAVE_AS_EXCEPTION)
    sheet = sources.get_sheet_stream(on_demand=True, **source_keywords)
    if "sheet_name" in dest_keywords:
        sheet.name = dest_keywords["sheet_name"]
    return sources.save_sheet(sheet, **dest_keywords)


@append_doc(docs.SAVE_BOOK_AS)
def save_book_as(**keywords):
    """
    Save a book from a data source to another one
    """
    dest_keywords, source_keywords = _split_keywords(**keywords)
    book = sources.get_book_stream(**source_keywords)
    book = to_book(book)
    return sources.save_book(book, **dest_keywords)


@append_doc(docs.ISAVE_BOOK_AS)
def isave_book_as(**keywords):
    """
    Save a book from a data source to another one

    It is simliar to :meth:`pyexcel.save_book_as` but it read
    when it writes. This function provide some speedup but
    the output data is not made uniform.
    """
    dest_keywords, source_keywords = _split_keywords(**keywords)
    book = sources.get_book_stream(on_demand=True, **source_keywords)
    return sources.save_book(book, **dest_keywords)


@append_doc(docs.GET_ARRAY)
def get_array(**keywords):
    """
    Obtain an array from an excel source

    It accepts the same parameters as :meth:`~pyexcel.get_sheet`
    but return an array instead.
    """
    sheet = get_sheet(**keywords)
    return sheet.to_array()


@append_doc(docs.GET_DICT)
def get_dict(name_columns_by_row=0, **keywords):
    """
    Obtain a dictionary from an excel source

    It accepts the same parameters as :meth:`~pyexcel.get_sheet`
    but return a dictionary instead.

    Specifically:
    name_columns_by_row : specify a row to be a dictionary key.
    It is default to 0 or first row.

    If you would use a column index 0 instead, you should do::

        get_dict(name_columns_by_row=-1, name_rows_by_column=0)

    """
    sheet = get_sheet(name_columns_by_row=name_columns_by_row, **keywords)
    return sheet.to_dict()


@append_doc(docs.GET_RECORDS)
def get_records(name_columns_by_row=0, **keywords):
    """
    Obtain a list of records from an excel source

    It accepts the same parameters as :meth:`~pyexcel.get_sheet`
    but return a list of dictionary(records) instead.

    Specifically:
    name_columns_by_row : specify a row to be a dictionary key.
    It is default to 0 or first row.

    If you would use a column index 0 instead, you should do::

        get_records(name_columns_by_row=-1, name_rows_by_column=0)

    """
    sheet = get_sheet(name_columns_by_row=name_columns_by_row, **keywords)
    return list(sheet.to_records())


@append_doc(docs.IGET_ARRAY)
def iget_array(**keywords):
    """
    Obtain a generator of an two dimensional array from an excel source

    It is similiar to :meth:`pyexcel.get_array` but it has less memory
    footprint.
    """
    sheet_stream = sources.get_sheet_stream(on_demand=True, **keywords)
    return sheet_stream.payload


@append_doc(docs.IGET_RECORDS)
def iget_records(custom_headers=None, **keywords):
    """
    Obtain a generator of a list of records from an excel source

    It is similiar to :meth:`pyexcel.get_records` but it has less memory
    footprint but requires the headers to be in the first row. And the
    data matrix should be of equal length. It should consume less memory
    and should work well with large files.
    """
    sheet_stream = sources.get_sheet_stream(on_demand=True, **keywords)
    headers = None
    for row_index, row in enumerate(sheet_stream.payload):
        if row_index == 0:
            headers = row
        else:
            if custom_headers:
                # custom order
                tmp_dict = dict(
                    zip_longest(headers, row, fillvalue=constants.DEFAULT_NA)
                )
                ordered_dict = OrderedDict()
                for name in custom_headers:
                    ordered_dict[name] = tmp_dict[name]
                yield ordered_dict
            else:
                # default order
                yield OrderedDict(
                    zip_longest(headers, row, fillvalue=constants.DEFAULT_NA)
                )


@append_doc(docs.GET_BOOK_DICT)
def get_book_dict(**keywords):
    """
    Obtain a dictionary of two dimensional arrays

    It accepts the same parameters as :meth:`~pyexcel.get_book`
    but return a dictionary instead.
    """
    book = get_book(**keywords)
    return book.to_dict()


def get_io_type(file_type):
    """
    Return the io stream types, string or bytes
    """
    io_type = manager.get_io_type(file_type)
    if io_type is None:
        io_type = "string"
    return io_type


def _split_keywords(**keywords):
    dest_keywords = {}
    source_keywords = {}
    for key, value in keywords.items():
        result = re.match(STARTS_WITH_DEST, key)
        if result:
            parameter = result.group(1)
            dest_keywords[parameter] = value
        else:
            source_keywords[key] = value
    return dest_keywords, source_keywords
