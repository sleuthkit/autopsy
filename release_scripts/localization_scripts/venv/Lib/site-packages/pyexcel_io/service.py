"""
    pyexcel_io.service
    ~~~~~~~~~~~~~~~~~~~

    provide service code to downstream projects

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import re
import math
import datetime

from pyexcel_io import constants, exceptions


def has_no_digits_in_float(value):
    """check if a float value had zero value in digits"""
    return value == math.floor(value)


def detect_date_value(cell_text):
    """
    Read the date formats that were written by csv.writer
    """
    ret = None
    try:
        if len(cell_text) == 10:
            ret = datetime.datetime.strptime(cell_text, "%Y-%m-%d")
            ret = ret.date()
        elif len(cell_text) == 19:
            ret = datetime.datetime.strptime(cell_text, "%Y-%m-%d %H:%M:%S")
        elif len(cell_text) > 19:
            ret = datetime.datetime.strptime(
                cell_text[0:26], "%Y-%m-%d %H:%M:%S.%f"
            )
    except ValueError:
        pass
    return ret


def detect_float_value(
    cell_text, pep_0515_off=True, ignore_nan_text=False, default_float_nan=None
):
    should_we_skip_it = (
        cell_text.startswith("0") and cell_text.startswith("0.") is False
    )
    if should_we_skip_it:
        # do not convert if a number starts with 0
        # e.g. 014325
        return None

    if pep_0515_off:
        pattern = "([0-9]+_)+[0-9]+.[0-9]*$"
        if re.match(pattern, cell_text):
            return None

    try:
        if ignore_nan_text:
            if cell_text.lower() == "nan":
                return None
            else:
                return float(cell_text)
        else:
            if cell_text.lower() == "nan":
                if cell_text == default_float_nan:
                    return float("NaN")
                else:
                    return None
            else:
                return float(cell_text)
    except ValueError:
        return None


def detect_int_value(cell_text, pep_0515_off=True):
    if cell_text.startswith("0") and len(cell_text) > 1:
        return None

    if pep_0515_off:
        pattern = "([0-9]+_)+[0-9]+$"
        if re.match(pattern, cell_text):
            return None

    try:
        return int(cell_text)

    except ValueError:
        pattern = "([0-9]+,)*[0-9]+$"
        if re.match(pattern, cell_text):
            integer_string = cell_text.replace(",", "")
            return int(integer_string)

        else:
            return None


def float_value(value):
    """convert a value to float"""
    ret = float(value)
    return ret


def date_value(value):
    """convert to data value accroding ods specification"""
    ret = "invalid"
    try:
        # catch strptime exceptions only
        if len(value) == 10:
            ret = datetime.datetime.strptime(value, "%Y-%m-%d")
            ret = ret.date()
        elif len(value) == 19:
            ret = datetime.datetime.strptime(value, "%Y-%m-%dT%H:%M:%S")
        elif len(value) > 19:
            ret = datetime.datetime.strptime(
                value[0:26], "%Y-%m-%dT%H:%M:%S.%f"
            )
    except ValueError:
        pass
    if ret == "invalid":
        raise Exception("Bad date value %s" % value)

    return ret


def time_value(value):
    """convert to time value accroding the specification"""
    import re

    results = re.match(r"PT(\d+)H(\d+)M(\d+)S", value)
    if results and len(results.groups()) == 3:
        hour = int(results.group(1))
        minute = int(results.group(2))
        second = int(results.group(3))
        if hour < 24:
            ret = datetime.time(hour, minute, second)
        else:
            ret = datetime.timedelta(
                hours=hour, minutes=minute, seconds=second
            )
    else:
        ret = None
    return ret


def boolean_value(value):
    """get bolean value"""
    if value == "true":
        ret = True
    elif value == "false":
        ret = False
    else:
        # needed for pyexcel-ods3
        ret = value
    return ret


ODS_FORMAT_CONVERSION = {
    "float": float,
    "date": datetime.date,
    "time": datetime.time,
    "timedelta": datetime.timedelta,
    "boolean": bool,
    "percentage": float,
    "currency": float,
}


ODS_WRITE_FORMAT_COVERSION = {
    float: "float",
    int: "float",
    str: "string",
    datetime.date: "date",
    datetime.time: "time",
    datetime.timedelta: "timedelta",
    bool: "boolean",
}


VALUE_CONVERTERS = {
    "float": float_value,
    "date": date_value,
    "time": time_value,
    "timedelta": time_value,
    "boolean": boolean_value,
    "percentage": float_value,
}


def throw_exception(value):
    raise exceptions.IntegerAccuracyLossError("%s is too big" % value)


def ods_float_value(value):
    if value > constants.MAX_INTEGER:
        raise exceptions.IntegerAccuracyLossError("%s is too big" % value)
    return value


def ods_date_value(value):
    return value.strftime("%Y-%m-%d")


def ods_time_value(value):
    return value.strftime("PT%HH%MM%SS")


def ods_bool_value(value):
    """convert a boolean value to text"""
    if value is True:
        return "true"

    else:
        return "false"


def ods_timedelta_value(cell):
    """convert a cell value to time delta"""
    hours = cell.days * 24 + cell.seconds // 3600
    minutes = (cell.seconds // 60) % 60
    seconds = cell.seconds % 60
    return "PT%02dH%02dM%02dS" % (hours, minutes, seconds)


ODS_VALUE_CONVERTERS = {
    "date": ods_date_value,
    "time": ods_time_value,
    "boolean": ods_bool_value,
    "timedelta": ods_timedelta_value,
    "float": ods_float_value,
    "long": ods_float_value,
}


VALUE_TOKEN = {
    "float": "value",
    "date": "date-value",
    "time": "time-value",
    "boolean": "boolean-value",
    "percentage": "value",
    "currency": "value",
    "timedelta": "time-value",
}
