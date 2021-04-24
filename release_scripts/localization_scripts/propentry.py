from typing import List, Union, Iterator
from outputresult import OutputResult
from tabularutil import WITH_TRANSLATED_COLS, DEFAULT_COLS, create_output_result, DEFAULT_STYLES, WITH_TRANSLATED_STYLE
import re


class PropEntry:
    rel_path: str
    key: str
    value: str
    should_delete: bool

    def __init__(self, rel_path: str, key: str, value: str, should_delete: bool = False):
        """Defines a property file entry to be updated in a property file.

        Args:
            rel_path (str): The relative path for the property file.
            key (str): The key for the entry.
            value (str): The value for the entry.
            should_delete (bool, optional): Whether or not the key should simply be deleted. Defaults to False.
        """
        self.rel_path = rel_path
        self.key = key
        self.value = value
        self.should_delete = should_delete

    def get_row(self) -> List[str]:
        """Returns the list of values to be entered as a row in serialization.

        Returns:
            List[str]:  The list of values to be entered as a row in serialization.
        """
        return [
            self.rel_path,
            self.key,
            self.value]


def convert_to_output(items: Iterator[PropEntry], commit_id: Union[str, None] = None,
                      show_translated_col: bool = True, value_regex: Union[str, None] = None) -> OutputResult:
    """
    Converts PropEntry objects to an output result to be written to a tabular datasource.

    Args:
        items: The PropEntry items.
        commit_id: The commit id to be shown in the header or None.
        show_translated_col: Whether or not to show an empty translated column.
        value_regex: Regex to determine if a value should be omitted.

    Returns: An OutputResult to be written.

    """
    header = WITH_TRANSLATED_COLS if show_translated_col else DEFAULT_COLS
    style = WITH_TRANSLATED_STYLE if show_translated_col else DEFAULT_STYLES

    if commit_id:
        header = header + ['', commit_id]

    results = []
    omitted = []

    for item in items:
        new_entry = item.get_row()
        if value_regex is None or re.match(value_regex, item.value):
            results.append(new_entry)
        else:
            omitted.append(new_entry)

    return create_output_result(header, results, omitted=omitted, style=style)
