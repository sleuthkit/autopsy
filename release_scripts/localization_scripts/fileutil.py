import os
from typing import Union, Tuple
from pathlib import Path


def get_path_pieces(orig_path: str) -> Tuple[str, Union[str, None], Union[str, None]]:
    """Retrieves path pieces.  This is a naive approach as it determines if a file is present based on the
    presence of an extension.

    Args:
        orig_path:  The original path to deconstruct.

    Returns: A tuple of directory, filename and extension.  If no extension is present, filename and extension are None.

    """

    potential_parent_dir, orig_file = os.path.split(str(Path(orig_path)))
    filename, file_extension = os.path.splitext(orig_file)
    if file_extension.startswith('.'):
        file_extension = file_extension[1:]

    if file_extension is None or len(file_extension) < 1:
        return str(Path(orig_path)), None, None
    else:
        return potential_parent_dir, filename, file_extension


def get_joined_path(folder: str, file_name: str) -> str:
    """
    Gets a joined folder and filename.

    Args:
        folder: The folder.
        file_name: The filename.

    Returns: The joined string path.

    """
    return str(Path(folder) / Path(file_name))


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
    return str(Path(parent_dir) / Path(new_filename))


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
        return str(Path(orig_path + filename_addition))
    else:
        ext = '' if extension is None else extension
        return str(Path(parent_dir) / Path('{0}{1}.{2}'.format(filename, filename_addition, ext)))
