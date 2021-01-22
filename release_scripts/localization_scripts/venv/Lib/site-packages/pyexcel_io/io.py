"""
    pyexcel_io.io
    ~~~~~~~~~~~~~~~~~~~

    The io interface to file extensions

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import os
import warnings
from types import GeneratorType

from pyexcel_io import constants
from pyexcel_io.reader import Reader
from pyexcel_io.writer import Writer
from pyexcel_io.plugins import OLD_READERS, OLD_WRITERS
from pyexcel_io._compact import isstream
from pyexcel_io.exceptions import (
    NoSupportingPluginFound,
    SupportingPluginAvailableButNotInstalled,
)


def iget_data(afile, file_type=None, **keywords):
    """Get data from an excel file source

    The data has not gone into memory yet. If you use dedicated partial read
    plugins, such as pyexcel-xlsxr, pyexcel-odsr, you will notice
    the memory consumption drop when you work with big files.

    :param afile: a file name, a file stream or actual content
    :param sheet_name: the name of the sheet to be loaded
    :param sheet_index: the index of the sheet to be loaded
    :param sheets: a list of sheet to be loaded
    :param file_type: used only when filename is not a physical file name
    :param force_file_type: used only when filename refers to a physical file
                            and it is intended to open it as forced file type.
    :param library: explicitly name a library for use.
                    e.g. library='pyexcel-ods'
    :param auto_detect_float: defaults to True
    :param auto_detect_int: defaults to True
    :param auto_detect_datetime: defaults to True
    :param ignore_infinity: defaults to True
    :param ignore_nan_text: various forms of 'NaN', 'nan' are ignored
    :param default_float_nan: choose one form of 'NaN', 'nan'
    :param pep_0515_off: turn off pep 0515. default to True.
    :param keep_trailing_empty_cells: keep trailing columns. default to False
    :param keywords: any other library specific parameters
    :returns: an ordered dictionary
    """
    data, reader = _get_data(
        afile, file_type=file_type, streaming=True, **keywords
    )
    return data, reader


def get_data(afile, file_type=None, streaming=None, **keywords):
    """Get data from an excel file source

    :param afile: a file name, a file stream or actual content
    :param sheet_name: the name of the sheet to be loaded
    :param sheet_index: the index of the sheet to be loaded
    :param sheets: a list of sheet to be loaded
    :param file_type: used only when filename is not a physial file name
    :param force_file_type: used only when filename refers to a physical file
                            and it is intended to open it as forced file type.
    :param streaming: toggles the type of returned data. The values of the
                      returned dictionary remain as generator if it is set
                      to True. Default is False.
    :param library: explicitly name a library for use.
                    e.g. library='pyexcel-ods'
    :param auto_detect_float: defaults to True
    :param auto_detect_int: defaults to True
    :param auto_detect_datetime: defaults to True
    :param ignore_infinity: defaults to True
    :param ignore_nan_text: various forms of 'NaN', 'nan' are ignored
    :param default_float_nan: choose one form of 'NaN', 'nan'
    :param pep_0515_off: turn off pep 0515. default to True.
    :param keep_trailing_empty_cells: keep trailing columns. default to False
    :param keywords: any other library specific parameters
    :returns: an ordered dictionary
    """
    if streaming is not None and streaming is True:
        warnings.warn("Please use iget_data instead")
    data, _ = _get_data(
        afile, file_type=file_type, streaming=False, **keywords
    )
    return data


def _get_data(afile, file_type=None, **keywords):
    if isstream(afile):
        keywords.update(
            dict(
                file_stream=afile,
                file_type=file_type or constants.FILE_FORMAT_CSV,
            )
        )
    else:
        if afile is None or file_type is None:
            keywords.update(dict(file_name=afile, file_type=file_type))
        else:
            keywords.update(dict(file_content=afile, file_type=file_type))
    return load_data(**keywords)


def save_data(afile, data, file_type=None, **keywords):
    """Save data to an excel file source

    Your data must be a dictionary

    :param filename: actual file name, a file stream or actual content
    :param data: a dictionary but an ordered dictionary is preferred
    :param file_type: used only when filename is not a physial file name
    :param force_file_type: used only when filename refers to a physical file
                            and it is intended to open it as forced file type.
    :param library: explicitly name a library for use.
                    e.g. library='pyexcel-ods'
    :param keywords: any other parameters that python csv module's
                     `fmtparams <https://docs.python.org/release/3.1.5/library/csv.html#dialects-and-formatting-parameters>`_
    """  # noqa
    to_store = data

    is_list = isinstance(data, (list, GeneratorType))
    if is_list:
        single_sheet_in_book = True
        to_store = {constants.DEFAULT_SHEET_NAME: data}
    else:
        keys = list(data.keys())
        single_sheet_in_book = len(keys) == 1

    no_file_type = isstream(afile) and file_type is None
    if no_file_type:
        file_type = constants.FILE_FORMAT_CSV

    if isstream(afile):
        keywords.update(dict(file_stream=afile, file_type=file_type))
    else:
        keywords.update(dict(file_name=afile, file_type=file_type))
    keywords["single_sheet_in_book"] = single_sheet_in_book
    with get_writer(**keywords) as writer:
        writer.write(to_store)


def load_data(
    file_name=None,
    file_content=None,
    file_stream=None,
    file_type=None,
    force_file_type=None,
    sheet_name=None,
    sheet_index=None,
    sheets=None,
    library=None,
    streaming=False,
    **keywords
):
    """Load data from any supported excel formats

    :param filename: actual file name, a file stream or actual content
    :param file_type: used only when filename is not a physial file name
    :param force_file_type: used only when filename refers to a physical file
                            and it is intended to open it as forced file type.
    :param sheet_name: the name of the sheet to be loaded
    :param sheet_index: the index of the sheet to be loaded
    :param keywords: any other parameters
    """
    result = {}
    inputs = [file_name, file_content, file_stream]
    number_of_none_inputs = [x for x in inputs if x is not None]
    if len(number_of_none_inputs) != 1:
        raise IOError(constants.MESSAGE_ERROR_02)

    if file_type is None:
        if force_file_type:
            file_type = force_file_type
        else:
            try:
                file_type = file_name.split(".")[-1]
            except AttributeError:
                raise Exception(constants.MESSAGE_FILE_NAME_SHOULD_BE_STRING)

    try:
        reader = OLD_READERS.get_a_plugin(file_type, library)
    except (NoSupportingPluginFound, SupportingPluginAvailableButNotInstalled):
        reader = Reader(file_type, library)

    try:
        if file_name:
            reader.open(file_name, **keywords)
        elif file_content:
            reader.open_content(file_content, **keywords)
        elif file_stream:
            reader.open_stream(file_stream, **keywords)
        else:
            raise IOError("Unrecognized options")
        if sheet_name:
            result = reader.read_sheet_by_name(sheet_name)
        elif sheet_index is not None:
            result = reader.read_sheet_by_index(sheet_index)
        elif sheets is not None:
            result = reader.read_many(sheets)
        else:
            result = reader.read_all()
        if streaming is False:
            for key in result.keys():
                result[key] = list(result[key])
            reader.close()
            reader = None

        return result, reader
    except NoSupportingPluginFound:
        if file_name:
            if os.path.exists(file_name):
                if os.path.isfile(file_name):
                    raise
                else:
                    raise IOError(
                        constants.MESSAGE_NOT_FILE_FORMATTER % file_name
                    )
            else:
                raise IOError(
                    constants.MESSAGE_FILE_DOES_NOT_EXIST % file_name
                )
        else:
            raise


def get_writer(
    file_name=None,
    file_stream=None,
    file_type=None,
    library=None,
    force_file_type=None,
    **keywords
):
    """find a suitable writer"""
    inputs = [file_name, file_stream]
    number_of_none_inputs = [x for x in inputs if x is not None]

    if len(number_of_none_inputs) != 1:
        raise IOError(constants.MESSAGE_ERROR_02)

    file_type_given = True

    if file_type is None and file_name:
        if force_file_type:
            file_type = force_file_type
        else:
            try:
                file_type = file_name.split(".")[-1]
            except AttributeError:
                raise Exception(constants.MESSAGE_FILE_NAME_SHOULD_BE_STRING)

        file_type_given = False

    try:
        writer = OLD_WRITERS.get_a_plugin(file_type, library)
    except (NoSupportingPluginFound, SupportingPluginAvailableButNotInstalled):
        writer = Writer(file_type, library)

    if file_name:
        if file_type_given:
            writer.open_content(file_name, **keywords)
        else:
            writer.open(file_name, **keywords)
    elif file_stream:
        writer.open_stream(file_stream, **keywords)
    # else: is resolved by earlier raise statement
    return writer


# backward compactibility
store_data = save_data
