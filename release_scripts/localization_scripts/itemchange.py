from typing import Iterator, List, Union, Dict

from foundvalue import FoundValue
from outputresult import OutputResult
from propsutil import get_entry_dict
from enum import Enum

from tabularutil import WITH_TRANSLATED_COLS, RELATIVE_PATH_COL, KEY_COL, create_output_result, WITH_TRANSLATED_STYLE, \
    VALUE_STYLE
import re


class ChangeType(Enum):
    """Describes the nature of a change in the properties file."""
    ADDITION = 'ADDITION'
    DELETION = 'DELETION'
    CHANGE = 'CHANGE'

    def __str__(self):
        return str(self.value)


class ItemChange:
    rel_path: str
    key: str
    prev_val: Union[str, None]
    cur_val: Union[str, None]
    type: ChangeType

    def __init__(self, rel_path: str, key: str, prev_val: str, cur_val: str):
        """Describes the change that occurred for a particular key of a properties file.

        Args:
            rel_path (str): The relative path of the properties file.
            key (str): The key in the properties file.
            prev_val (str): The previous value for the key.
            cur_val (str): The current value for the key.
        """
        self.rel_path = rel_path
        self.key = key
        self.prev_val = prev_val
        self.cur_val = cur_val
        if cur_val is not None and prev_val is None:
            self.type = ChangeType.ADDITION
        elif cur_val is None and prev_val is not None:
            self.type = ChangeType.DELETION
        else:
            self.type = ChangeType.CHANGE

    @staticmethod
    def get_headers() -> List[str]:
        """Returns the csv headers to insert when serializing a list of ItemChange objects to csv.

        Returns:
            List[str]: The column headers
        """
        return ['Relative Path', 'Key', 'Change Type', 'Previous Value', 'Current Value']

    def get_row(self, show_translated_col: bool) -> List[str]:
        """Returns the list of values to be entered as a row in csv serialization.

        Args:
            show_translated_col (bool): Whether or not the translated columns are showing; otherwise use default.

        Returns:
            List[str]:  The list of values to be entered as a row in csv serialization.
        """

        if show_translated_col:
            return [
                self.rel_path,
                self.key,
                self.cur_val
            ]
        else:
            return [
                self.rel_path,
                self.key,
                str(self.type) if self.type else None,
                self.prev_val,
                self.cur_val]


ITEMCHANGE_DEFAULT_COLS = [RELATIVE_PATH_COL, KEY_COL, 'Change Type', 'Previous Value', 'Current Value']


def convert_to_output(items: Iterator[ItemChange],
                      commit1_id: Union[str, None] = None,
                      commit2_id: Union[str, None] = None,
                      translation_dict: Union[Dict[str, FoundValue], None] = None,
                      show_translated_col: bool = True,
                      value_regex: Union[str, None] = None,
                      separate_deleted: bool = True) -> OutputResult:
    """
    Converts PropEntry objects to an output result to be written to a tabular datasource.

    Args:
        items: The PropEntry items.
        commit1_id: The first commit id to be shown in the header or None.
        commit2_id: The second commit id to be shown in the header or None.
        translation_dict: A dictionary of English values mapped to the translated value.
        show_translated_col: Whether or not to show an empty translated column.
        value_regex: Regex to determine if a value should be omitted.
        separate_deleted: Deleted items should not be included in regular results.

    Returns: An OutputResult to be written.

    """
    header = WITH_TRANSLATED_COLS if show_translated_col else ITEMCHANGE_DEFAULT_COLS
    style = WITH_TRANSLATED_STYLE if show_translated_col else [None, None, None, VALUE_STYLE, VALUE_STYLE]

    if commit1_id:
        header = header + [commit1_id]

    if commit2_id:
        header = header + [commit2_id]

    results = []
    omitted = []
    found_translation = []
    deleted = []

    for item in items:
        item_row = item.get_row(show_translated_col)
        if separate_deleted and item.type == ChangeType.DELETION:
            deleted.append(item_row)
        elif value_regex is not None and re.match(value_regex, item.cur_val):
            omitted.append(item_row)
        elif translation_dict is not None and item.cur_val.strip() in translation_dict:
            found_translation.append(item_row + [translation_dict[item.cur_val.strip()].translated_val])
        else:
            results.append(item_row)

    return create_output_result(header, results, omitted=omitted, deleted=deleted, found_translation=found_translation,
                                style=style)


def get_item_change(rel_path: str, key: str, prev_val: str, cur_val: str) -> Union[ItemChange, None]:
    """Returns an ItemChange object if the previous value is not equal to the current value.

    Args:
        rel_path (str): The relative path for the properties file.
        key (str): The key within the properties file for this potential change.
        prev_val (str): The previous value.
        cur_val (str): The current value.

    Returns:
        ItemChange: The ItemChange object or None if values are the same.
    """
    if prev_val == cur_val:
        return None
    else:
        return ItemChange(rel_path, key, prev_val, cur_val)


def get_changed(rel_path: str, a_str: str, b_str: str) -> Iterator[ItemChange]:
    """Given the relative path of the properties file that has been provided,
    determines the property items that have changed between the two property
    file strings.

    Args:
        rel_path (str): The relative path for the properties file.
        a_str (str): The string representing the original state of the file.
        b_str (str): The string representing the current state of the file.

    Returns:
        List[ItemChange]: The changes determined.
    """
    print('Retrieving changes for {0}...'.format(rel_path))
    a_dict = get_entry_dict(a_str)
    b_dict = get_entry_dict(b_str)
    all_keys = set().union(a_dict.keys(), b_dict.keys())
    mapped = map(lambda key: get_item_change(
        rel_path, key, a_dict.get(key), b_dict.get(key)), all_keys)
    return filter(lambda entry: entry is not None, mapped)
