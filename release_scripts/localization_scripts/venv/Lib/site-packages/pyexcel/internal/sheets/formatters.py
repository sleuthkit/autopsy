"""
    pyexcel.formatters
    ~~~~~~~~~~~~~~~~~~~

    These utilities help format the content

    :copyright: (c) 2014-2019 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import json
import datetime
from decimal import Decimal

from pyexcel import constants as constants
from pyexcel._compact import PY2


def string_to_format(value, target_format):
    """Convert string to specified format"""
    if target_format == float:
        try:
            ret = float(value)
        except ValueError:
            ret = value
    elif target_format == int:
        try:
            ret = float(value)
            ret = int(ret)
        except ValueError:
            ret = value
    else:
        ret = value

    return ret


def float_to_format(value, target_format):
    """Convert float to specified format"""
    if target_format == int:
        ret = int(value)
    elif target_format == str:
        ret = str(value)
    else:
        ret = value

    return ret


def int_to_format(value, target_format):
    """Convert int to specified format"""
    if target_format == float:
        ret = float(value)
    elif target_format == str:
        ret = str(value)
    else:
        ret = value
    return ret


def date_to_format(value, target_format):
    """Convert date to specified format"""
    if target_format == str:
        if isinstance(value, datetime.date):
            ret = value.strftime("%d/%m/%y")
        elif isinstance(value, datetime.datetime):
            ret = value.strftime("%d/%m/%y")
        elif isinstance(value, datetime.time):
            ret = value.strftime("%H:%M:%S")
    else:
        ret = value
    return ret


def boolean_to_format(value, target_format):
    """Convert bool to specified format"""
    if target_format == float:
        ret = float(value)
    elif target_format == str:
        if value == 1:
            ret = "true"
        else:
            ret = "false"
    else:
        ret = value
    return ret


def empty_to_format(_, target_format):
    """Convert empty value to specified format"""
    if target_format == float:
        ret = 0.0
    elif target_format == int:
        ret = 0
    else:
        ret = constants.DEFAULT_NA
    return ret


CONVERSION_FUNCTIONS = {
    str: string_to_format,
    float: float_to_format,
    int: int_to_format,
    datetime.datetime: date_to_format,
    datetime.time: date_to_format,
    datetime.date: date_to_format,
    bool: boolean_to_format,
    None: empty_to_format,
    Decimal: float_to_format,
}

if PY2:
    CONVERSION_FUNCTIONS[unicode] = string_to_format
    CONVERSION_FUNCTIONS[long] = float_to_format


def default_formatter(value, to_type):
    return json.dumps(value)


def to_format(to_type, value):
    """Wrapper utility function for format different formats

    :param type from_type: a python type
    :param type to_type: a python type
    :param value value: a python value
    """
    if value is not None:
        if value == "":
            from_type = None
        else:
            from_type = type(value)
    else:
        from_type = None
    func = CONVERSION_FUNCTIONS.get(from_type, default_formatter)
    return func(value, to_type)
