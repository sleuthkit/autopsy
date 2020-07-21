from typing import Iterator, List, Union
from propsutil import get_entry_dict
from enum import Enum


class ChangeType(Enum):
    """Describes the nature of a change in the properties file."""
    ADDITION = 'ADDITION'
    DELETION = 'DELETION'
    CHANGE = 'CHANGE'


class ItemChange:
    rel_path: str
    key: str
    prev_val: str
    cur_val: str
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
        if ItemChange.has_str_content(cur_val) and not ItemChange.has_str_content(prev_val):
            self.type = ChangeType.ADDITION
        elif not ItemChange.has_str_content(cur_val) and ItemChange.has_str_content(prev_val):
            self.type = ChangeType.DELETION
        else:
            self.type = ChangeType.CHANGE

    @staticmethod
    def has_str_content(content: str):
        """Determines whether or not the content is empty or None.

        Args:
            content (str): The text.

        Returns:
            bool: Whether or not it has content.
        """
        return content is not None and len(content.strip()) > 0

    @staticmethod
    def get_headers() -> List[str]:
        """Returns the csv headers to insert when serializing a list of ItemChange objects to csv.

        Returns:
            List[str]: The column headers
        """
        return ['Relative Path', 'Key', 'Change Type', 'Previous Value', 'Current Value']

    def get_row(self) -> List[str]:
        """Returns the list of values to be entered as a row in csv serialization.

        Returns:
            List[str]:  The list of values to be entered as a row in csv serialization.
        """
        return [
            self.rel_path,
            self.key,
            self.type,
            self.prev_val,
            self.cur_val]


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
    """Given the relative path of the properties file that

    Args:
        rel_path (str): The relative path for the properties file.
        a_str (str): The string representing the original state of the file.
        b_str (str): The string representing the current state of the file.

    Returns:
        List[ItemChange]: The changes determined.
    """
    print('Retrieving changes for {}...'.format(rel_path))
    a_dict = get_entry_dict(a_str)
    b_dict = get_entry_dict(b_str)
    all_keys = set().union(a_dict.keys(), b_dict.keys())
    mapped = map(lambda key: get_item_change(
        rel_path, key, a_dict.get(key), b_dict.get(key)), all_keys)
    return filter(lambda entry: entry is not None, mapped)
