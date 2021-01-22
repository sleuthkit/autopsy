"""
    pyexcel_io
    ~~~~~~~~~~~~~~~~~~~

    Uniform interface for reading/writing different excel file formats

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import logging

import pyexcel_io.plugins as plugins

from .io import get_data, iget_data, save_data  # noqa
from ._compact import NullHandler

logging.getLogger(__name__).addHandler(NullHandler())  # noqa


BLACK_LIST = [__name__, "pyexcel_webio", "pyexcel_text"]
WHITE_LIST = [
    "pyexcel_io.readers",
    "pyexcel_io.writers",
    "pyexcel_io.database",
]
PREFIX_PATTERN = "^pyexcel_.*$"

plugins.load_plugins(
    PREFIX_PATTERN, __path__, BLACK_LIST, WHITE_LIST  # noqa: F821
)
