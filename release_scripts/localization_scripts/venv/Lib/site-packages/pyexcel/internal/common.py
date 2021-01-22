"""
    pyexcel.internal.common
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Defintion for the shared objects

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
NO_COLUMN_NAMES = "Only sheet with column names is accepted"


class SheetIterator(object):
    """
    Sheet Iterator
    """

    def __init__(self, bookreader):
        self.book_reader_ref = bookreader
        self.current = 0

    def __iter__(self):
        return self

    def __next__(self):
        return self.next()

    def next(self):
        """get next sheet"""
        if self.current < self.book_reader_ref.number_of_sheets():
            self.current += 1
            return self.book_reader_ref[self.current - 1]
        else:
            raise StopIteration


def get_sheet_headers(sheet):
    from pyexcel.internal.generators import SheetStream

    if isinstance(sheet, SheetStream):
        headers = next(sheet.payload)
    else:
        headers = sheet.colnames
    if len(headers) == 0:
        raise Exception(NO_COLUMN_NAMES)
    return headers


def get_book_headers_in_array(book):
    from pyexcel.internal.generators import BookStream

    if isinstance(book, BookStream):
        colnames_array = [next(sheet.payload) for sheet in book]
    else:
        for sheet in book:
            if len(sheet.colnames) == 0:
                sheet.name_columns_by_row(0)
        colnames_array = [sheet.colnames for sheet in book]
    return colnames_array
