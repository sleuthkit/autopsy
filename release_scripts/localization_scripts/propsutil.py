"""Provides tools for reading from and writing to java properties files.
"""

from typing import Dict, Union, IO
from jproperties import Properties
import os
from os import path

# The default extension for property files in autopsy repo
DEFAULT_PROPS_EXTENSION = 'properties-MERGED'


def get_entry_dict(file_contents: Union[str, IO]) -> Dict[str, str]:
    """Retrieves a dictionary mapping the properties represented in the string.

    Args:
        file_contents: The string of the properties file or the file handle.

    Returns:
        Dict[str,str]: The mapping of keys to values in that properties file.
    """

    props = Properties()
    props.load(file_contents, "utf-8")
    return props.properties


def set_entry_dict(contents: Dict[str, str], file_path: str):
    """Sets the property file to the key-value pairs of the contents dictionary.

    Args:
        contents (Dict[str, str]): The dictionary whose contents will be the key value pairs of the properties file.
        file_path (str): The path to the properties file to create.
    """

    props = Properties()
    for key, val in contents.items():
        props[key] = val

    parent_dir, file = os.path.split(file_path)
    if not os.path.exists(parent_dir):
        os.makedirs(parent_dir)

    with open(file_path, "wb") as f:
        props.store(f)


def update_entry_dict(contents: Dict[str, str], file_path: str):
    """Updates the properties file at the given location with the key-value properties of contents.  
    Creates a new properties file at given path if none exists.

    Args:
        contents (Dict[str, str]): The dictionary whose contents will be the key value pairs of the properties file.
        file_path (str): The path to the properties file to create.
    """
    contents_to_edit = contents.copy()

    if path.isfile(file_path):
        cur_dict = get_entry_dict(file_path)
        for cur_key, cur_val in cur_dict.values():
            # only update contents if contents does not already have key
            if cur_key not in contents_to_edit:
                contents_to_edit[cur_key] = cur_val

    set_entry_dict(contents_to_edit, file_path)
