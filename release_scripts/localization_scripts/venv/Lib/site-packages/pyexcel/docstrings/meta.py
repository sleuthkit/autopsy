"""
    pyexcel.docstrings.meta
    ~~~~~~~~~~~~~~~~~~~~~~~~~

    Reusible docstrings for pyexcel.internal.meta

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from .keywords import CSV_PARAMS

SAVE_AS_OPTIONS = (
    """
Keywords may vary depending on your file type, because the associated
file type employs different library.

**PARAMETERS**

filename: a file path

library:
    choose a specific pyexcel-io plugin for writing

renderer_library:
    choose a pyexcel parser plugin for writing

"""
    + CSV_PARAMS
)
