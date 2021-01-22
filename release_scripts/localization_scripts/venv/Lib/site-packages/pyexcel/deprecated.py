"""
    pyexcel.deprecated
    ~~~~~~~~~~~~~~~~~~~

    List of apis that become deprecated but was kept for backward compatibility

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import warnings
from functools import partial

from pyexcel.core import get_book, get_sheet
from pyexcel._compact import deprecated

DEPRECATED_LOADER = partial(
    deprecated,
    message="Deprecated since v0.1.5! Please use get_sheet instead.",
)
DEPRECATED_BOOK_LOADER = partial(
    deprecated, message="Deprecated since v0.1.5! Please use get_book instead."
)


@DEPRECATED_BOOK_LOADER
def load_book(file_name, **keywords):
    """Load content from physical file

    :param str file_name: the file name
    :param any keywords: additional parameters
    """
    return get_book(file_name=file_name, **keywords)


@DEPRECATED_BOOK_LOADER
def load_book_from_memory(file_type, file_content, **keywords):
    """Load content from memory content

    :param tuple the_tuple: first element should be file extension,
    second element should be file content
    :param any keywords: additional parameters
    """
    return get_book(file_type=file_type, file_content=file_content, **keywords)


@DEPRECATED_LOADER
def load(file_name, sheetname=None, **keywords):
    """Constructs an instance :class:`Sheet` from a sheet of an excel file

    except csv, most excel files has more than one sheet.
    Hence sheetname is required here to indicate from which sheet the instance
    should be constructed. If this parameter is omitted, the first sheet, which
    is indexed at 0, is used. For csv, sheetname is always omitted because csv
    file contains always one sheet.
    :param str sheetname: which sheet to be used for construction
    :param int name_colmns_by_row: which row to give column names
    :param int name_rows_by_column: which column to give row names
    :param dict keywords: other parameters
    """
    if isinstance(file_name, tuple):
        sheet = get_sheet(
            file_type=file_name[0],
            file_content=file_name[1],
            sheet_name=sheetname,
            **keywords
        )
    else:
        sheet = get_sheet(
            file_name=file_name, sheet_name=sheetname, **keywords
        )
    return sheet


@DEPRECATED_LOADER
def load_from_memory(file_type, file_content, sheetname=None, **keywords):
    """Constructs an instance :class:`Sheet` from memory

    :param str file_type: one value of these: 'csv', 'tsv', 'csvz',
    'tsvz', 'xls', 'xlsm', 'xslm', 'ods'
    :param iostream file_content: file content
    :param str sheetname: which sheet to be used for construction
    :param dict keywords: any other parameters
    """
    return get_sheet(
        file_type=file_type,
        file_content=file_content,
        sheet_name=sheetname,
        **keywords
    )


@DEPRECATED_LOADER
def load_from_dict(the_dict, with_keys=True, **keywords):
    """Return a sheet from a dictionary of one dimensional arrays

    :param dict the_dict: its value should be one dimensional array
    :param bool with_keys: indicate if dictionary keys should be
                           appended or not
    """
    return get_sheet(adict=the_dict, with_keys=with_keys, **keywords)


@DEPRECATED_LOADER
def load_from_records(records, **keywords):
    """Return a sheet from a list of records

    Sheet.to_records() would produce a list of dictionaries. All dictionaries
    share the same keys.
    :params list records: records are likely to be produced by
                          Sheet.to_records() method.
    """
    return get_sheet(records=records, **keywords)


@partial(
    deprecated,
    message="Deprecated since v0.0.7! Please use class Sheet instead",
)
def Reader(file_name=None, sheetname=None, **keywords):
    """
    A single sheet excel file reader

    Default is the sheet at index 0. Or you specify one using sheet index
    or sheet name. The short coming of this reader is: column filter is
    applied first then row filter is applied next

    use as class would fail though
    changed since 0.0.7
    """
    if isinstance(file_name, tuple):
        return get_sheet(
            file_type=file_name[0],
            file_content=file_name[1],
            sheet_name=sheetname,
            **keywords
        )
    else:
        return get_sheet(file_name=file_name, sheet_name=sheetname, **keywords)


@partial(
    deprecated,
    message=(
        "Deprecated since v0.0.7! Please use class "
        + "Sheet(..., name_columns_by_row=0,..) instead"
    ),
)
def SeriesReader(file_name=None, sheetname=None, series=0, **keywords):
    """A single sheet excel file reader and it has column headers in a selected row

    use as class would fail
    changed since 0.0.7
    """
    if isinstance(file_name, tuple):
        return get_sheet(
            file_type=file_name[0],
            file_content=file_name[1],
            name_columns_by_row=series,
            **keywords
        )
    else:
        return load(
            file_name,
            sheetname=sheetname,
            name_columns_by_row=series,
            **keywords
        )


@partial(
    deprecated,
    message="Please use class Sheet(..., name_rows_by_column=0..) instead",
)
def ColumnSeriesReader(file_name=None, sheetname=None, series=0, **keywords):
    """A single sheet excel file reader and it has row headers in a selected column

    use as class would fail
    changed since 0.0.7
    """
    if isinstance(file_name, tuple):
        return get_sheet(
            file_type=file_name[0],
            file_content=file_name[1],
            name_rows_by_column=series,
            **keywords
        )
    else:
        return load(
            file_name,
            sheetname=sheetname,
            name_rows_by_column=series,
            **keywords
        )


@partial(
    deprecated,
    message="Deprecated since v0.0.7! Please use class Book instead",
)
def BookReader(file_name, **keywords):
    """For backward compatibility"""
    return load_book(file_name, **keywords)


def deprecated_pyexcel_ext(version, module_name):
    """Warn the deprecated usage"""
    warnings.warn(
        "Deprecated usage since v%s! Explicit import " % version
        + "is no longer required. %s is auto imported." % module_name
    )
