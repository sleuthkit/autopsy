"""
    pyexcel
    ~~~~~~~~~~~~~~~~~~~

    **pyexcel** is a wrapper library to read, manipulate and
    write data in different excel formats: csv, ods, xls, xlsx
    and xlsm. It does not support formulas, styles and charts.

    :copyright: (c) 2014-2019 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from .book import Book
from .core import (
    save_as,
    get_book,
    get_dict,
    isave_as,
    get_array,
    get_sheet,
    iget_book,
    iget_array,
    get_records,
    iget_records,
    save_book_as,
    get_book_dict,
    isave_book_as,
)
from .sheet import Sheet

# flake8: noqa
from .cookbook import (
    split_a_book,
    merge_all_to_a_book,
    merge_csv_to_a_book,
    extract_a_sheet_from_a_book,
)
from .deprecated import (
    Reader,
    BookReader,
    SeriesReader,
    ColumnSeriesReader,
    load,
    load_book,
    load_from_dict,
    load_from_memory,
    load_from_records,
    load_book_from_memory,
)
from .__version__ import __author__, __version__
from .internal.garbagecollector import free_resources
