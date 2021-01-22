"""
    pyexcel.internal.generators
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Defintion for the sheet and book generators.

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel._compact import OrderedDict
from pyexcel.internal.common import SheetIterator


class SheetStream(object):
    """
    Memory efficient sheet representation

    This class wraps around the data read from pyexcel-io.
    Comparing with :class:`pyexcel.Sheet`, the instance of
    this class does not load all data into memory. Hence
    it performs better when dealing with big data.

    If you would like to do custom rendering for each row
    of the two dimensional data, you would need to
    pass a row formatting/rendering function to the parameter
    "renderer" of pyexcel's signature functions.

    """

    def __init__(self, name, payload):
        self.name = name
        self.payload = payload
        self.colnames = []

    def to_array(self):
        """
        Simply return the generator
        """
        return self.payload

    @property
    def array(self):
        """array attribute"""
        return list(self.payload)

    def get_internal_array(self):
        return self.payload


class BookStream(object):
    """
    Memory efficient book representation

    Comparing with :class:`pyexcel.Book`, the instace of
    this class uses :class:`pyexcel.generators.SheetStream` as
    its internal repesentation of sheet objects. Because `SheetStream`
    does not read data into memory, it is memory efficient.
    """

    def __init__(self, sheets=None, filename="memory", path=None):
        """Book constructor

        Selecting a specific book according to filename extension
        :param OrderedDict/dict sheets: a dictionary of data
        :param str filename: the physical file
        :param str path: the relative path or absolute path
        :param set keywords: additional parameters to be passed on
        """
        self.path = path
        self.filename = filename
        self.name_array = []
        if sheets:
            self.load_from_sheets(sheets)
        else:
            self.sheets = {}

    def load_from_sheets(self, sheets):
        """Load content from existing sheets

        :param dict sheets: a dictionary of sheets. Each sheet is
        a list of lists
        """
        if sheets is None:
            return
        self.sheets = OrderedDict()
        keys = sheets.keys()
        if not isinstance(sheets, OrderedDict):
            # if the end user does not care about the order
            # we put alphatical order
            keys = sorted(keys)
        for name in keys:
            sheet = SheetStream(name, sheets[name])
            # this sheets keep sheet order
            self.sheets.update({name: sheet})
            # this provide the convenience of access the sheet
            self.__dict__[name] = sheet
        self.name_array = list(self.sheets.keys())

    def sheet_names(self):
        return self.name_array

    def to_dict(self):
        """
        Get book data structure as a dictionary
        """
        the_dict = OrderedDict()
        for sheet in self:
            the_dict.update({sheet.name: sheet.payload})
        return the_dict

    def __iter__(self):
        return SheetIterator(self)

    def number_of_sheets(self):
        """Return the number of sheets"""
        return len(self.name_array)

    def __getitem__(self, index):
        if isinstance(index, int):
            if index < len(self.name_array):
                sheet_name = self.name_array[index]
                return self.sheets[sheet_name]
        else:
            sheet_name = index
            return self.sheets[sheet_name]
