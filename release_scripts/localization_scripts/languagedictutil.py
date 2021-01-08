import os
from pathlib import Path
from typing import Dict, Iterator, Tuple, TypeVar
from git import Blob

from foundvalue import FoundValue
from gitutil import get_text
from propsutil import get_entry_dict


def extract_translations(file_iter: Iterator[Tuple[str, Blob]], orig_filename: str, translated_filename: str) \
        -> Dict[str, FoundValue]:
    """
    Creates a translations dictionary based on comparing the values of keys in an original bundles file and a translated
    bundles file in the same directory.  For instance, if /path/to/original.properties and
    /path/to/translated.properties both exist and in both files, a key-value pairing for keyA exists, the dictionary
    will contain an entry mapping the original value for keyA to the translated value and other metadata for that
    key.

    Args:
        file_iter: An iterator of tuples containing the path and the content of the file.
        orig_filename: The original file name (i.e. 'bundle.properties-MERGED').
        translated_filename: The translated file name (i.e. 'Bundle_ja.properties').

    Returns: A dictionary mapping original values to translated values.

    """

    # Create a dictionary mapping parent path to the file content for both original and translated files
    original_files: Dict[str, Tuple[str, Blob]] = dict()
    translated_files: Dict[str, Tuple[str, Blob]] = dict()

    for path, content in file_iter:
        parent_dir, file_name = os.path.split(str(Path(path)))
        if file_name.strip().lower() == orig_filename.strip().lower():
            original_files[parent_dir.strip().lower()] = (path, content)
        elif file_name.strip().lower() == translated_filename.strip().lower():
            translated_files[parent_dir.strip().lower()] = (path, content)

    # determine original and translated files with common parent folders and find common keys
    to_ret: Dict[str, FoundValue] = dict()
    for (common_folder, (original_path, original_blob), (translated_path, translated_blob))\
            in common_entries(original_files, translated_files):

        orig_dict = sanitize_prop_dict_keys(get_entry_dict(get_text(original_blob)))
        translated_dict = sanitize_prop_dict_keys(get_entry_dict(get_text(translated_blob)))

        for common_key, orig_value, translated_value in common_entries(orig_dict, translated_dict):
            to_ret[orig_value] = FoundValue(
                common_path=common_folder,
                original_file=original_path,
                translated_file=translated_path,
                key=common_key,
                orig_val=orig_value,
                translated_val=translated_value)

    return to_ret


def sanitize_prop_dict_keys(dct: Dict[str, str]) -> Dict[str, str]:
    """
    Sanitizes all the keys in a dictionary (i.e. strips white space and makes lower case).

    Args:
        dct: The dictionary.

    Returns: The dictionary with sanitized keys.

    """
    return {k.strip().lower(): v for k, v in dct.items()}


K = TypeVar('K')
V = TypeVar('V')


def common_entries(*dcts: Dict[K, V]) -> Iterator[Tuple[K, Tuple[V, ...]]]:
    """
    Taken from https://stackoverflow.com/questions/16458340/python-equivalent-of-zip-for-dictionaries,
    creates creates an iterator of tuples where the left value is the common key value and the right hand value is
    a tuple of all the matching values in order that the dictionaries were ordered in parameters.

    Args:
        *dcts: The dictionaries in order to provide common key/values.

    Returns:
    An iterator of tuples where the left value is the common key value and the right hand value is
    a tuple of all the matching values in order that the dictionaries were ordered in parameters.
    """
    if not dcts:
        return
    for i in set(dcts[0]).intersection(*dcts[1:]):
        yield (i,) + tuple(d[i] for d in dcts)
