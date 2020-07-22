"""Provides tools for parsing and writing to a csv file.
"""
import codecs
from typing import List, Iterable, Tuple, TypeVar
import csv
import os
import unittest
from envutil import get_proj_dir
from unittestutil import TEST_OUTPUT_FOLDER


def records_to_csv(output_path: str, rows: Iterable[List[str]]):
    """Writes rows to a csv file at the specified path.

    Args:
        output_path (str): The path where the csv file will be written.
        rows (List[List[str]]): The rows to be written.  Each row of a 
                                list of strings will be written according 
                                to their index (i.e. column 3 will be index 2).
    """

    parent_dir, file = os.path.split(output_path)
    if not os.path.exists(parent_dir):
        os.makedirs(parent_dir)

    with open(output_path, 'w', encoding="utf-8-sig", newline='') as csvfile:
        writer = csv.writer(csvfile)
        for row in rows:
            writer.writerow(row)


def csv_to_records(input_path: str, header_row: bool) -> Tuple[List[List[str]], List[str]]:
    """Writes rows to a csv file at the specified path.

    Args:
        input_path (str): The path where the csv file will be written.
        header_row (bool): Whether or not there is a header row to be skipped.
    """

    with open(input_path, encoding='utf-8-sig') as csv_file:
        csv_reader = csv.reader(csv_file, delimiter=',')

        header = None
        results = []
        try:
            for row in csv_reader:
                if header_row:
                    header = row
                    header_row = False
                else:
                    results.append(row)
        except Exception as e:
            raise Exception("There was an error parsing csv {path}".format(path=input_path), e)

        return results, header


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

        os.makedirs(os.path.join(get_proj_dir(), TEST_OUTPUT_FOLDER))
        test_path = os.path.join(get_proj_dir(), TEST_OUTPUT_FOLDER, 'test.csv')
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



