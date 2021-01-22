"""
    pyexcel.source
    ~~~~~~~~~~~~~~~~~~~~~~~~

    Generic data source definition

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel import constants as constants


class AbstractSource(object):
    """
    Define a data source for use with the signature functions

    This can be used to extend the function parameters once the custom
    class inherit this and register it with corresponding source registry
    """

    fields = [constants.SOURCE]
    attributes = []
    targets = []
    actions = []
    key = constants.SOURCE

    def __init__(self, **keywords):
        self._keywords = keywords

    def get_source_info(self):
        """return filename and path, otherwise not useful

        see also `:meth:pyexcel.internal.core.get_book_stream`
        """
        return (None, None)

    @classmethod
    def is_my_business(cls, action, **keywords):
        """
        If all required keys are present, this source is activated
        """
        statuses = [_has_field(field, keywords) for field in cls.fields]
        results = [status for status in statuses if status is False]
        return len(results) == 0

    def write_data(self, content):
        """Write data to a data source"""
        raise NotImplementedError("")

    def get_data(self):
        """Get data from a data source"""
        raise NotImplementedError("")


class MemorySourceMixin(object):
    """A memory source should an internal memory stream

    And it is desirable to get its internal stream
    """

    def get_content(self):
        """Get memory repsentation of the formatted data

        e.g. StringIO instance which contains the csv formatted data
        """
        return self._content


def _has_field(field, keywords):
    return field in keywords and keywords[field] is not None
