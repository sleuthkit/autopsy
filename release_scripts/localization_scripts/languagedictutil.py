import os
from pathlib import Path
from typing import Dict, Iterator, Tuple, TypeVar
from git import Blob
from propsutil import get_entry_dict


class FoundValue:
    """
    A class containing a record of a prop key existing in both an original props file and a translated props file
    """
    common_path: str
    original_file: str
    translated_file: str
    key: str
    orig_val: str
    translated_val: str

    def __init__(self, common_path, original_file, translated_file, key, orig_val, translated_val):
        """
        Constructor.
        Args:
            common_path: The folder common to both files.
            original_file: The original file path.
            translated_file: The translated file path.
            key: The common prop key.
            orig_val: The original (English) value.
            translated_val: The translated value.
        """
        self.common_path = common_path
        self.original_file = original_file
        self.translated_file = translated_file
        self.key = key
        self.orig_val = orig_val
        self.translated_val = translated_val


def extract_translations(file_iter: Iterator[Tuple[str, Blob]], orig_filename: str, translated_filename: str) \
        -> Dict[str, FoundValue]:
    """

    Args:
        file_iter:
        orig_filename:
        translated_filename:

    Returns:

    """
    original_files: Dict[str, Tuple[str, Blob]] = dict()
    translated_files: Dict[str, Tuple[str, Blob]] = dict()

    for path, content in file_iter:
        parent_dir, file_name = os.path.split(str(Path(path)))
        if file_name.strip().lower() == orig_filename.strip().lower():
            original_files[file_name] = (parent_dir, content)
        elif file_name.strip().lower() == translated_filename.strip().lower():
            translated_files[file_name] = (parent_dir, content)

    to_ret: Dict[str, FoundValue] = dict()

    for common_folder, ((original_path, original_blob), (translated_path, translated_blob))\
            in common_entries(original_files, translated_files):
        orig_dict = sanitize_prop_dict_keys(get_entry_dict(original_blob))
        translated_dict = sanitize_prop_dict_keys(get_entry_dict(translated_blob))

        for common_key, (orig_value, translated_value) in common_entries(orig_dict, translated_dict):
            to_ret[orig_value] = FoundValue(
                common_path=common_folder,
                original_file=original_path,
                translated_file=translated_path,
                key=common_key,
                orig_val=orig_value,
                translated_val=translated_value)

    return to_ret


def sanitize_prop_dict_keys(dct: Dict[str, str]) -> Dict[str, str]:
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
