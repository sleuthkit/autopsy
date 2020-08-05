import unittest
from typing import Dict

from itemchange import get_changed, ChangeType


def dict_to_prop_str(this_dict: Dict[str, str]) -> str:
    toret = ''
    for key, val in this_dict.items():
        toret += "{key}={value}\n".format(key=key, value=val)

    return toret


class ItemChangeTest(unittest.TestCase):
    def test_get_changed(self):
        deleted_key = 'deleted.property.key'
        deleted_val = 'will be deleted'

        change_key = 'change.property.key'
        change_val_a = 'original value'
        change_val_b = 'new value'

        change_key2 = 'change2.property.key'
        change_val2_a = 'original value 2'
        change_val2_b = ''

        change_key3 = 'change3.property.key'
        change_val3_a = ''
        change_val3_b = 'cur value 3'

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
            change_key3: change_val3_a,
            same_key: same_value,
            same_key2: same_value2
        }

        b_dict = {
            change_key: change_val_b,
            change_key2: change_val2_b,
            change_key3: change_val3_b,
            addition_key: addition_new_val,
            same_key: same_value,
            same_key2: same_value2
        }

        a_str = dict_to_prop_str(a_dict)
        b_str = dict_to_prop_str(b_dict)

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
        self.assertEqual(addition_item.cur_val, addition_new_val)

        change_item = key_to_change[change_key]
        self.assertEqual(change_item.type, ChangeType.CHANGE)
        self.assertEqual(change_item.prev_val, change_val_a)
        self.assertEqual(change_item.cur_val, change_val_b)

        change_item2 = key_to_change[change_key2]
        self.assertEqual(change_item2.type, ChangeType.CHANGE)
        self.assertEqual(change_item2.prev_val, change_val2_a)
        self.assertEqual(change_item2.cur_val, change_val2_b)

        change_item3 = key_to_change[change_key3]
        self.assertEqual(change_item3.type, ChangeType.CHANGE)
        self.assertEqual(change_item3.prev_val, change_val3_a)
        self.assertEqual(change_item3.cur_val, change_val3_b)

        self.assertTrue(same_key not in key_to_change)
        self.assertTrue(same_key2 not in key_to_change)
