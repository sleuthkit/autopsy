"""
    pyexcel.plugin.renderers._texttable
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Export data into texttable format. It also serves the default
    presentation of pyexcel sheet and book.

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from types import GeneratorType

from pyexcel import constants as constants
from texttable import Texttable
from pyexcel.renderer import Renderer
from pyexcel.internal.sheets.formatters import to_format


class TextTableRenderer(Renderer):
    """Default texttable presetation"""

    def render_sheet(self, sheet):
        content = render_text_table(sheet, self._write_title)
        self._stream.write(content)


def render_text_table(sheet, write_title):
    """return data in text table presentation"""
    content = ""
    if write_title:
        content += "%s:\n" % sheet.name
    table = Texttable(max_width=0)
    data = sheet.to_array()
    if isinstance(data, GeneratorType):
        data = list(data)
    if len(data) == 0:
        return content
    table.set_cols_dtype(["t"] * len(data[0]))
    if len(sheet.colnames) > 0:
        table.set_chars(["-", "|", "+", "="])
        table.header(list(_cleanse_a_row(data[0])))
    else:
        table.add_row(list(_cleanse_a_row(data[0])))
    for sub_array in data[1:]:
        new_array = _cleanse_a_row(sub_array)
        table.add_row(list(new_array))
    content += table.draw()
    return content


def _cleanse_a_row(row):
    for item in row:
        if item == constants.DEFAULT_NA:
            yield " "
        else:
            yield to_format(str, item)
