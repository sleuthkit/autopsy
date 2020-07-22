import unittest
from typing import Iterator, List, Union, Dict
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


class ItemChangeTest(unittest.TestCase):
    @staticmethod
    def dict_to_prop_str(dict: Dict[str,str]) -> str:
        toret = ''
        for key,val in dict.items:
            toret += "{key}={value}\n".format(key=key,val=val)

        return toret

    def get_changed_test(self):
        deleted_key = 'deleted.property.key'
        deleted_val = 'will be deleted'

        change_key = 'change.property.key'
        change_val_a = 'original value'
        change_val_b = 'new value'

        change_key2 = 'change2.property.key'
        change_val2_a = 'original value 2'
        change_val2_b = ''

        addition_key = 'addition.property.key'
        addition_new_val = 'the added value'

        same_key = 'samevalue.property.key'
        same_value = 'the same value'

        same_key2 = 'samevalue2.property.key'
        same_value2 = ''

        a_dict = {
            deleted_key: deleted_val,
            change_key: change_val_a,
            change_key2: change_val2_a,
            same_key: same_value,
            same_key2: same_value2
        }

        b_dict = {
            change_key: change_val_b,
            change_key2: change_val2_b,
            addition_key: addition_new_val,
            same_key: same_value,
            same_key2: same_value2
        }

        a_str = ItemChangeTest.dict_to_prop_str(a_dict)
        b_str = ItemChangeTest.dict_to_prop_str(b_dict)

        rel_path = 'my/rel/path.properties'

        key_to_change = {}

        for item_change in get_changed(rel_path, a_str, b_str):
            self.assertEqual(item_change.rel_path, rel_path)
            key_to_change[item_change.key] = item_change

        deleted_item = key_to_change[deleted_key]
        self.assertEqual(deleted_item.type, ChangeType.DELETION)
        self.assertEqual(deleted_item.prev_val, deleted_val)
        self.assertEqual(deleted_item.cur_val, None)

        addition_item = key_to_change[addition_key]
        self.assertEqual(addition_item.type, ChangeType.ADDITION)
        self.assertEqual(addition_item.prev_val, None)
        self.assertEqual(deleted_item.cur_val, addition_new_val)

        change_item = key_to_change[change_key]
        self.assertEqual(change_item.type, ChangeType.CHANGE)
        self.assertEqual(change_item.prev_val, change_val_a)
        self.assertEqual(change_item.cur_val, change_val_b)

        change_item2 = key_to_change[change_key2]
        self.assertEqual(change_item2.type, ChangeType.CHANGE)
        self.assertEqual(change_item2.prev_val, change_val2_a)
        self.assertEqual(change_item2.cur_val, change_val2_b)

        self.assertTrue(same_key not in key_to_change)
        self.assertTrue(same_key2 not in key_to_change)