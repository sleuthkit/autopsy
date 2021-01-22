"""
    pyexcel.exceptions
    ~~~~~~~~~~~~~~~~~~~

    Exceptions appeared in pyexcel

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""


class UnknownParameters(Exception):
    """Unknown parameter(s) were given to the signature functions"""

    pass


class FileTypeNotSupported(Exception):
    """A file type is not supported"""

    pass


class UpgradePlugin(Exception):
    """Please upgrade your plugin"""

    pass
