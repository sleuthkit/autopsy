"""
    pyexcel_io.exceptions
    ~~~~~~~~~~~~~~~~~~~~~~

    all possible exceptions

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""


class NoSupportingPluginFound(Exception):
    """raised when an known file extension is seen"""

    pass


class SupportingPluginAvailableButNotInstalled(Exception):
    """raised when a known plugin is not installed"""

    pass


class IntegerAccuracyLossError(Exception):
    """
    When an interger is greater than 999999999999999, ods loses its accuracy.

    from pyexcel import Sheet, get_sheet
    s = Sheet()
    s[0,0] = 999999999999999  # 15 '9's
    print(s)
    s.save_as('abc.ods')
    b=get_sheet(file_name='abc.ods')
    b[0,0] == s[0,0]

    s = Sheet()
    s[0,0] = 9999999999999999 # 16 '9's
    print(s)
    s.save_as('abc.ods')
    b=get_sheet(file_name='abc.ods')
    b[0,0] != s[0,0]
    """

    def __init__(self, message):
        custom_message = (
            message
            + "\n"
            + "In order to keep its accuracy, please save as string. Then "
            + "convert to int, long or float after the value will be read back"
        )

        super(IntegerAccuracyLossError, self).__init__(custom_message)
