import os
import unittest
from typing import Tuple
from pathlib import Path
from fileutil import get_path_pieces, get_new_path, get_filename_addition


def joined_paths(pieces: Tuple[str, str, str]) -> str:
    return os.path.join(pieces[0], pieces[1] + '.' + pieces[2])


PATH_PIECES1 = ('/test/folder', 'filename', 'ext')
PATH_PIECES2 = ('/test.test2/folder.test2', 'filename.test', 'ext')
PATH_PIECES3 = ('/test.test2/folder.test2/folder', None, None)

PATH1 = joined_paths(PATH_PIECES1)
PATH2 = joined_paths(PATH_PIECES2)
PATH3 = PATH_PIECES3[0]

ALL_ITEMS = [
    (PATH_PIECES1, PATH1),
    (PATH_PIECES2, PATH2),
    (PATH_PIECES3, PATH3)
]


class FileUtilTest(unittest.TestCase):
    def test_get_path_pieces(self):
        for (expected_path, expected_filename, expected_ext), path in ALL_ITEMS:
            path, filename, ext = get_path_pieces(path)
            self.assertEqual(path, str(Path(expected_path)))
            self.assertEqual(filename, expected_filename)
            self.assertEqual(ext, expected_ext)

    def test_get_new_path(self):
        for (expected_path, expected_filename, expected_ext), path in ALL_ITEMS:
            new_name = "newname.file"
            new_path = get_new_path(path, new_name)
            self.assertEqual(new_path, str(Path(expected_path) / Path(new_name)))

    def test_get_filename_addition(self):
        for (expected_path, expected_filename, expected_ext), path in ALL_ITEMS:
            addition = "addition"
            new_path = get_filename_addition(path, addition)
            if expected_filename is None or expected_ext is None:
                expected_file_path = Path(expected_path + addition)
            else:
                expected_file_path = Path(expected_path) / Path("{file_name}{addition}.{extension}".format(
                        file_name=expected_filename, addition=addition, extension=expected_ext))

            self.assertEqual(
                new_path, str(expected_file_path))
