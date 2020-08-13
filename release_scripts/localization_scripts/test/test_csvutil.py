import codecs
import os
import unittest
from typing import TypeVar, List

from csvutil import records_to_csv, csv_to_records
from test.unittestutil import get_output_path


class CsvUtilTest(unittest.TestCase):
    T = TypeVar('T')

    def assert_equal_arr(self, a: List[T], b: List[T]):
        self.assertEqual(len(a), len(b), 'arrays are not equal length')
        for i in range(0, len(a)):
            if isinstance(a[i], list) and isinstance(b[i], list):
                self.assert_equal_arr(a[i], b[i])
            else:
                self.assertEqual(a[i], b[i], "Items: {0} and {1} at index {2} are not equal.".format(a[i], b[i], i))

    def test_read_write(self):
        data = [['header1', 'header2', 'header3', 'additional header'],
                ['data1', 'data2', 'data3'],
                ['', 'data2-1', 'data2-2']]

        os.makedirs(get_output_path(), exist_ok=True)
        test_path = get_output_path('test.csv')
        records_to_csv(test_path, data)

        byte_inf = min(32, os.path.getsize(test_path))
        with open(test_path, 'rb') as bom_test_file:
            raw = bom_test_file.read(byte_inf)
            if not raw.startswith(codecs.BOM_UTF8):
                self.fail("written csv does not have appropriate BOM")

        read_records_no_header, no_header = csv_to_records(test_path, header_row=False)
        self.assert_equal_arr(read_records_no_header, data)

        read_rows, header = csv_to_records(test_path, header_row=True)
        self.assert_equal_arr(header, data[0])
        self.assert_equal_arr(read_rows, [data[1], data[2]])
