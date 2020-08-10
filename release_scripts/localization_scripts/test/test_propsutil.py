import os
import unittest

from propsutil import set_entry_dict, get_entry_dict_from_path, update_entry_dict
from test.unittestutil import get_output_path


class PropsUtilTest(unittest.TestCase):
    def test_update_entry_dict(self):
        orig_key = 'orig_key'
        orig_val = 'orig_val 片仮名 '
        to_be_altered_key = 'tobealteredkey'
        first_val = 'not yet altered sábado'
        second_val = 'altered Stöcke'

        orig_props = {
            orig_key: orig_val,
            to_be_altered_key: first_val
        }

        update_props = {
            to_be_altered_key: second_val
        }

        os.makedirs(get_output_path(), exist_ok=True)
        test_path = get_output_path('test.props')
        set_entry_dict(orig_props, test_path)

        orig_read_props = get_entry_dict_from_path(test_path)
        self.assertEqual(orig_read_props[orig_key], orig_val)
        self.assertEqual(orig_read_props[to_be_altered_key], first_val)

        update_entry_dict(update_props, test_path)
        updated_read_props = get_entry_dict_from_path(test_path)
        self.assertEqual(updated_read_props[orig_key], orig_val)
        self.assertEqual(updated_read_props[to_be_altered_key], second_val)
