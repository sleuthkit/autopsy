from typing import Generic, TypeVar, List, Union

from outputresult import OutputResult

T = TypeVar('T')


RELATIVE_PATH_COL = 'Relative path'
KEY_COL = 'Key'
VALUE_COL = 'Value'

ENGLISH_VALUE_COL = 'English Value'
TRANSLATED_VALUE_COL = 'Translated Value'

DEFAULT_COLS = [RELATIVE_PATH_COL, KEY_COL, VALUE_COL]
WITH_TRANSLATED_COLS = [RELATIVE_PATH_COL, KEY_COL, ENGLISH_VALUE_COL, TRANSLATED_VALUE_COL]


def create_output_result(row_header: List[str], results: List[List[str]],
                         omitted: Union[List[List[str]], None] = None,
                         deleted: Union[List[List[str]], None] = None) -> OutputResult:

    """
    Creates OutputResult from components.
    Args:
        row_header: The row header.
        results: The results.
        omitted: The omitted items if any.
        deleted: The deleted items if any.

    Returns: The generated OutputResult.

    """
    omitted_result = [row_header] + omitted if omitted else None
    deleted_result = [row_header] + deleted if deleted else None

    return OutputResult([row_header] + results, omitted_result, deleted_result)