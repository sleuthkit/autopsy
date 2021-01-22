"""
    pyexcel.book
    ~~~~~~~~~~~~~~~~~~~

    Excel book

    :copyright: (c) 2014-2019 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from pyexcel import _compact as compact
from pyexcel.sheet import Sheet
from pyexcel.internal.meta import BookMeta
from pyexcel.internal.common import SheetIterator

LOCAL_UUID = 0


class Book(BookMeta):
    """
    Read an excel book that has one or more sheets

    For csv file, there will be just one sheet
    """

    def __init__(self, sheets=None, filename="memory", path=None):
        """
        Book constructor

        Selecting a specific book according to filename extension

        :param sheets: a dictionary of data
        :param filename: the physical file
        :param path: the relative path or absolute path
        :param keywords: additional parameters to be passed on
        """
        self.__path = None
        self.__name_array = []
        self.filename = None
        self.__sheets = compact.OrderedDict()
        self.init(sheets=sheets, filename=filename, path=path)

    def init(self, sheets=None, filename="memory", path=None):
        """indpendent function so that it could be called multiple times"""
        self.__path = path
        self.filename = filename
        self.load_from_sheets(sheets)

    def load_from_sheets(self, sheets):
        """
        Load content from existing sheets

        :param dict sheets: a dictionary of sheets. Each sheet is
                            a list of lists
        """
        if sheets is None:
            return
        keys = sheets.keys()
        for name in keys:
            value = sheets[name]
            if isinstance(value, Sheet):
                sheet = value
                sheet.name = name
            else:
                # array
                sheet = Sheet(value, name)
            # this sheets keep sheet order
            self.__sheets.update({name: sheet})
            # this provide the convenience of access the sheet
            self.__dict__[name.replace(" ", "_")] = sheet
        self.__name_array = list(self.__sheets.keys())

    def __iter__(self):
        return SheetIterator(self)

    def __len__(self):
        return len(self.__name_array)

    def sort_sheets(self, key=None, reverse=False):
        self.__name_array = sorted(self.__name_array, key=key, reverse=reverse)

    def number_of_sheets(self):
        """
        Return the number of sheets
        """
        return len(self.__name_array)

    def sheet_names(self):
        """
        Return all sheet names
        """
        return self.__name_array

    def sheet_by_name(self, name):
        """
        Get the sheet with the specified name
        """
        return self.__sheets[name]

    def sheet_by_index(self, index):
        """
        Get the sheet with the specified index
        """
        if index < len(self.__name_array):
            sheet_name = self.__name_array[index]
            return self.sheet_by_name(sheet_name)

    def remove_sheet(self, sheet):
        """
        Remove a sheet
        """
        if isinstance(sheet, int):
            if sheet < len(self.__name_array):
                sheet_name = self.__name_array[sheet]
                del self.__sheets[sheet_name]
                self.__name_array = list(self.__sheets.keys())
            else:
                raise IndexError
        elif isinstance(sheet, str):
            if sheet in self.__name_array:
                del self.__sheets[sheet]
                self.__name_array = list(self.__sheets.keys())
            else:
                raise KeyError
        else:
            raise TypeError

    def __getitem__(self, key):
        """Override operator[]"""
        if isinstance(key, int):
            return self.sheet_by_index(key)
        else:
            return self.sheet_by_name(key)

    def __delitem__(self, other):
        """
        Override del book[index]
        """
        self.remove_sheet(other)
        return self

    def __add__(self, other):
        """
        Override operator +

        example::

            book3 = book1 + book2
            book3 = book1 + book2["Sheet 1"]

        """
        content = {}
        current_dict = self.to_dict()
        for k in current_dict.keys():
            new_key = k
            if len(current_dict.keys()) == 1:
                new_key = "%s_%s" % (self.filename, k)
            content[new_key] = current_dict[k]
        if isinstance(other, Book):
            other_dict = other.to_dict()
            for key in other_dict.keys():
                new_key = key
                if len(other_dict.keys()) == 1:
                    new_key = other.filename
                if new_key in content:
                    uid = local_uuid()
                    new_key = "%s_%s" % (key, uid)
                content[new_key] = other_dict[key]
        elif isinstance(other, Sheet):
            new_key = other.name
            if new_key in content:
                uid = local_uuid()
                new_key = "%s_%s" % (other.name, uid)
            content[new_key] = other.array
        else:
            raise TypeError
        output = Book()
        output.load_from_sheets(content)
        return output

    def __iadd__(self, other):
        """
        Operator overloading +=

        example::

            book += book2
            book += book2["Sheet1"]

        """
        if isinstance(other, Book):
            names = other.sheet_names()
            for name in names:
                new_key = name
                if len(names) == 1:
                    new_key = other.filename
                if new_key in self.__name_array:
                    uid = local_uuid()
                    new_key = "%s_%s" % (name, uid)
                self.__sheets[new_key] = Sheet(other[name].array, new_key)
        elif isinstance(other, Sheet):
            new_key = other.name
            if new_key in self.__name_array:
                uid = local_uuid()
                new_key = "%s_%s" % (other.name, uid)
            self.__sheets[new_key] = Sheet(other.array, new_key)
        else:
            raise TypeError
        self.__name_array = list(self.__sheets.keys())
        return self

    def to_dict(self):
        """Convert the book to a dictionary"""
        the_dict = compact.OrderedDict()
        for sheet in self:
            the_dict.update({sheet.name: sheet.array})
        return the_dict


def to_book(bookstream):
    """Convert a bookstream to Book"""
    if isinstance(bookstream, Book):
        return bookstream
    else:
        return Book(
            bookstream.to_dict(),
            filename=bookstream.filename,
            path=bookstream.path,
        )


def local_uuid():
    """create home made uuid"""
    global LOCAL_UUID
    LOCAL_UUID = LOCAL_UUID + 1
    return LOCAL_UUID
