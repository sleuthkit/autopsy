from typing import TypeVar, List, Union

from outputresult import OutputResult, ColumnStyle

T = TypeVar('T')


RELATIVE_PATH_COL = 'Relative path'
KEY_COL = 'Key'
VALUE_COL = 'Value'

ENGLISH_VALUE_COL = 'English Value'
TRANSLATED_VALUE_COL = 'Translated Value'

VALUE_STYLE: ColumnStyle = {
    'width': 100,
    'wrap_text': True
}

DEFAULT_COLS = [RELATIVE_PATH_COL, KEY_COL, VALUE_COL]
DEFAULT_STYLES = [None, None, VALUE_STYLE]

WITH_TRANSLATED_COLS = [RELATIVE_PATH_COL, KEY_COL, ENGLISH_VALUE_COL, TRANSLATED_VALUE_COL]
WITH_TRANSLATED_STYLE = [None, None, VALUE_STYLE, VALUE_STYLE]


def create_output_result(row_header: List[str], results: List[List[str]],
                         omitted: Union[List[List[str]], None] = None,
                         deleted: Union[List[List[str]], None] = None,
                         found_translation: Union[List[List[str]], None] = None,
                         style: Union[List[ColumnStyle], None] = None) -> OutputResult:

    """
    Creates OutputResult from components.

    Args:
        row_header: The row header.
        results: The results.
        omitted: The omitted items if any.
        deleted: The deleted items if any.
        found_translation: Items where a previous translation has already been created.
        style: Style of columns if any.

    Returns: The generated OutputResult.

    """
    omitted_result = [row_header] + omitted if omitted else None
    deleted_result = [row_header] + deleted if deleted else None
    found_result = [row_header] + found_translation if found_translation else None

    return OutputResult(
        results=[row_header] + results,
        omitted=omitted_result,
        deleted=deleted_result,
        found=found_result,
        style=style)
