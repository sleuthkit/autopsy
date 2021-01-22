"""
    pyexcel.internal.core
    ~~~~~~~~~~~~~~~~~~~~~~

    elementary functions to read and write generic excel content

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel._compact import PY2
from pyexcel.internal import SOURCE
from pyexcel.constants import DEFAULT_NO_DATA
from pyexcel.internal.generators import BookStream, SheetStream


def get_sheet_stream(**keywords):
    """
    Get an instance of SheetStream from an excel source
    """
    a_source = SOURCE.get_source(**keywords)
    filename, path = a_source.get_source_info()
    sheets = a_source.get_data()
    if sheets:
        sheet_name, data = _one_sheet_tuple(sheets.items())
        return SheetStream(sheet_name, data)
    else:
        return SheetStream(DEFAULT_NO_DATA, [[]])


def get_book_stream(**keywords):
    """
    Get an instance of BookStream from an excel source

    Where the dictionary should have text as keys and two dimensional
    array as values.
    """
    a_source = SOURCE.get_book_source(**keywords)
    filename, path = a_source.get_source_info()
    sheets = a_source.get_data()
    return BookStream(sheets, filename=filename, path=path)


def save_sheet(sheet, **keywords):
    """
    Save a sheet instance to any source
    """
    a_source = SOURCE.get_writable_source(**keywords)
    return _save_any(a_source, sheet)


def save_book(book, **keywords):
    """
    Save a book instance to any source
    """
    a_source = SOURCE.get_writable_book_source(**keywords)
    return _save_any(a_source, book)


def _save_any(a_source, instance):
    a_source.write_data(instance)
    try:
        content_stream = a_source.get_content()
        _seek_at_zero(content_stream)
        return content_stream
    except AttributeError:
        return None


def _seek_at_zero(a_stream):
    if PY2:
        try:
            a_stream.seek(0)
        except IOError:
            pass
    else:
        import io

        try:
            a_stream.seek(0)
        except io.UnsupportedOperation:
            pass


def _one_sheet_tuple(items):
    if not PY2:
        items = list(items)
    return items[0][0], items[0][1]
