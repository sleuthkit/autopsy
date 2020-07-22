import os
import unittest
from typing import Union, Tuple


def get_path_pieces(orig_path: str) -> Tuple[str, Union[str, None], Union[str, None]]:
    """Retrieves path pieces.  This is a naive approach as it determines if a file is present based on the
    presence of an extension.
    Args:
        orig_path:  The original path to deconstruct.

    Returns: A tuple of directory, filename and extension.  If no extension is present, filename and extension are None.

    """

    potential_parent_dir, orig_file = os.path.split(orig_path)
    filename, file_extension = os.path.splitext(orig_file)

    if file_extension is None or len(file_extension) < 1:
        return orig_path, None, None
    else:
        return potential_parent_dir, filename, file_extension


def get_new_path(orig_path: str, new_filename: str) -> str:
    """Obtains a new path.  This tries to determine if the provided path is a directory or filename (has an
    extension containing '.') then constructs the new path with the old parent directory and the new filename.

    Args:
        orig_path (str): The original path.
        new_filename (str): The new filename to use.

    Returns:
        str: The new path.
    """

    parent_dir, filename, ext = get_path_pieces(orig_path)
    return os.path.join(parent_dir, new_filename)


# For use with creating csv filenames for entries that have been omitted.
OMITTED_ADDITION = '-omitted'


def get_filename_addition(orig_path: str, filename_addition: str) -> str:
    """Gets filename with addition.  So if item is '/path/name.ext' and the filename_addition is '-add', the new result
    would be '/path/name-add.ext'.

    Args:
        orig_path (str): The original path.
        filename_addition (str): The new addition.

    Returns: The altered path.

    """
    parent_dir, filename, extension = get_path_pieces(orig_path)
    if filename is None:
        return orig_path + filename_addition
    else:
        ext = '' if extension is None else extension
        return os.path.join(parent_dir, '{0}{1}.{2}'.format(filename, filename_addition, ext))


class FileUtilTest(unittest.TestCase):

    @staticmethod
    def _joined_paths(pieces: Tuple[str, str, str]) -> str:
        return os.path.join(pieces[0], pieces[1] + '.' + pieces[2])

    PATH_PIECES1 = ('/test/folder', 'filename', 'ext')
    PATH_PIECES2 = ('/test.test2/folder.test2', 'filename.test', 'ext')
    PATH_PIECES3 = ('/test.test2/folder.test2/folder', None, None)

    def __init__(self):
        self.PATH1 = FileUtilTest._joined_paths(self.PATH_PIECES1)
        self.PATH2 = FileUtilTest._joined_paths(self.PATH_PIECES2)
        self.PATH3 = self.PATH_PIECES3[0]

        self.ALL_ITEMS = [
            (self.PATH_PIECES1, self.PATH1),
            (self.PATH_PIECES2, self.PATH2),
            (self.PATH_PIECES3, self.PATH3)
        ]

    def get_path_pieces_test(self):
        for (expected_path, expected_filename, expected_ext), path in self.ALL_ITEMS:
            path, filename, ext = get_path_pieces(path)
            self.assertEqual(path, expected_path)
            self.assertEqual(filename, expected_filename)
            self.assertEqual(ext, expected_ext)

    def get_new_path_test(self):
        for (expected_path, expected_filename, expected_ext), path in self.ALL_ITEMS:
            new_name = "newname.file"
            new_path = get_new_path(path, new_name)
            self.assertEqual(new_path, os.path.join(expected_path, new_name))

    def get_filename_addition_test(self):
        for (expected_path, expected_filename, expected_ext), path in self.ALL_ITEMS:
            addition = "addition"
            new_path = get_filename_addition(path, addition)
            self.assertEqual(
                new_path, os.path.join(
                    expected_path,
                    "{file_name}{addition}.{extension}".format(
                        file_name=expected_filename, addition=addition, extension=expected_ext)))
