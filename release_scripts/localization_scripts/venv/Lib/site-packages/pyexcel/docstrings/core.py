"""
    pyexcel.docstrings.core
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Reusible docstrings for pyexcel.core

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from . import keywords

__GET_SHEET__ = (
    keywords.EXAMPLE_NOTE_PAGINATION
    + keywords.SOURCE_PARAMS_TABLE
    + """
**Parameters**
    """
    + keywords.SOURCE_PARAMS
)

__GET_BOOK__ = (
    keywords.SOURCE_BOOK_PARAMS_TABLE
    + """
**Parameters**
"""
    + keywords.SOURCE_BOOK_PARAMS
)

I_NOTE = (
    """
When you use this function to work on physical files, this function
will leave its file handle open. When you finish the operation
on its data, you need to call :func:`pyexcel.free_resources` to
close file hande(s).

"""
    + keywords.I_NOTE
)

__SAVE_AS__ = (
    """
It accepts two sets of keywords. Why two sets? one set is
source, the other set is destination. In order to distinguish
the two sets, source set will be exactly the same
as the ones for :meth:`pyexcel.get_sheet`; destination
set are exactly the same as the ones for :class:`pyexcel.Sheet.save_as`
but require a 'dest' prefix.
"""
    + keywords.DEST_PARAMS_TABLE
    + __GET_SHEET__
    + keywords.DEST_PARAMS
    + """

if csv file is destination format, python csv
`fmtparams <https://docs.python.org/release/3.1.5/
library/csv.html#dialects-and-formatting-parameters>`_
are accepted

for example: dest_lineterminator will replace default '\r\n'
to the one you specified

In addition, this function use :class:`pyexcel.Sheet` to
render the data which could have performance penalty. In exchange,
parameters for :class:`pyexcel.Sheet` can be passed on, e.g.
`name_columns_by_row`.

"""
)

__SAVE_BOOK_AS__ = (
    __GET_BOOK__
    + keywords.DEST_BOOK_PARAMS
    + """

Where the dictionary should have text as keys and two dimensional
array as values.

================ ============================================
Saving to source parameters
================ ============================================
file             dest_file_name, dest_sheet_name,
                 keywords with prefix 'dest'
memory           dest_file_type, dest_content,
                 dest_sheet_name, keywords with prefix 'dest'
sql              dest_session, dest_tables,
                 dest_table_init_func, dest_mapdict
django model     dest_models, dest_initializers,
                 dest_mapdict, dest_batch_size
================ ============================================
"""
)

GET_SHEET = __GET_SHEET__

GET_ARRAY = __GET_SHEET__

IGET_ARRAY = __GET_SHEET__ + I_NOTE

GET_DICT = __GET_SHEET__

GET_RECORDS = __GET_SHEET__

IGET_RECORDS = __GET_SHEET__ + I_NOTE

SAVE_AS = __SAVE_AS__

ISAVE_AS = __SAVE_AS__ + I_NOTE

GET_BOOK = __GET_BOOK__

IGET_BOOK = __GET_BOOK__ + I_NOTE

GET_BOOK_DICT = __GET_BOOK__

SAVE_BOOK_AS = __SAVE_BOOK_AS__

ISAVE_BOOK_AS = __SAVE_BOOK_AS__ + I_NOTE
