"""
    pyexcel.plugins.sources.pydata.bookdictsource
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of book dict source

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.source import AbstractSource, MemorySourceMixin
from pyexcel._compact import PY2, OrderedDict
from pyexcel.plugins.sources import params

from .common import _FakeIO


class BookDictSource(AbstractSource, MemorySourceMixin):
    """
    Multiple sheet data source via a dictionary of two dimensional arrays
    """

    def __init__(self, bookdict, **keywords):
        self.__bookdict = bookdict
        self._content = _FakeIO()
        AbstractSource.__init__(self, **keywords)

    def get_data(self):
        the_dict = self.__bookdict
        if not isinstance(self.__bookdict, OrderedDict):
            the_dict = _convert_dict_to_ordered_dict(self.__bookdict)
        return the_dict

    def get_source_info(self):
        return params.BOOKDICT, None

    def write_data(self, book):
        self._content.setvalue(book.to_dict())


def _convert_dict_to_ordered_dict(the_dict):
    keys = the_dict.keys()
    if not PY2:
        keys = list(keys)
    keys = sorted(keys)
    ret = OrderedDict()
    for key in keys:
        ret[key] = the_dict[key]
    return ret
