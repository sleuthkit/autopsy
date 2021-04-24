import os
from pathlib import Path
from typing import Dict, Iterator, Tuple, TypeVar, List
from git import Blob

from foundvalue import FoundValue
from gitutil import get_text
from propentry import PropEntry
from propsutil import get_entry_dict


def extract_translations(orig_file_iter: Iterator[Tuple[str, Blob]], translated_file_iter: Iterator[Tuple[str, Blob]],
                         orig_filename: str, translated_filename: str) -> Dict[str, FoundValue]:
    """
    Creates a translations dictionary based on comparing the values of keys in an original bundles file and a translated
    bundles file in the same directory.  For instance, if /path/to/original.properties and
    /path/to/translated.properties both exist and in both files, a key-value pairing for keyA exists, the dictionary
    will contain an entry mapping the original value for keyA to the translated value and other metadata for that
    key.

    Args:
        orig_file_iter: An iterator of tuples containing the path and the content of the file for original content.
        translated_file_iter: An iterator of tuples containing the path and the content of the file for translated
        content.
        orig_filename: The original file name (i.e. 'bundle.properties-MERGED').
        translated_filename: The translated file name (i.e. 'Bundle_ja.properties').

    Returns: A dictionary mapping original values to translated values.

    """

    # Create a dictionary mapping parent path to the file content for both original and translated files
    original_files: Dict[str, Tuple[str, Blob]] = _find_file_entries(orig_file_iter, orig_filename)
    translated_files: Dict[str, Tuple[str, Blob]] = _find_file_entries(translated_file_iter, translated_filename)

    # determine original and translated files with common parent folders and find common keys
    to_ret: Dict[str, FoundValue] = dict()
    for (common_folder, (original_path, original_blob), (translated_path, translated_blob)) \
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


def _find_file_entries(file_iter: Iterator[Tuple[str, Blob]], searched_file_name: str) -> Dict[str, Tuple[str, Blob]]:
    """
    Finds file entries that match the file_name indicated.
    Args:
        file_iter: An iterator of a tuple containing the repo relative path of the file and the file contents as a blob.
        searched_file_name: The file name to be found in the relative path.

    Returns: A dictionary mapping the directory to a tuple of the full path (including the file name) and the file
    contents.

    """
    files: Dict[str, Tuple[str, Blob]] = dict()
    for path, content in file_iter:
        parent_dir, file_name = os.path.split(str(Path(path)))
        if searched_file_name.strip().lower() == file_name.strip().lower():
            files[parent_dir.strip().lower()] = (path, content)

    return files


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


def find_unmatched_translations(orig_file_iter: Iterator[Tuple[str, Blob]],
                                translated_file_iter: Iterator[Tuple[str, Blob]],
                                orig_filename: str, translated_filename: str) -> List[PropEntry]:
    """
    Finds all unmatched translation (where English is non-empty value and Japanese does not exist or is empty).

    Args:
        orig_file_iter: An iterator of tuples containing the path and the content of the file for original content.
        translated_file_iter: An iterator of tuples containing the path and the content of the file for translated
        content.
        orig_filename: The original file name (i.e. 'bundle.properties-MERGED').
        translated_filename: The translated file name (i.e. 'Bundle_ja.properties').

    Returns: A list of found unmatched translations sorted by path and then key.

    """

    # Create a dictionary mapping parent path to the file content for both original and translated files
    original_files: Dict[str, Tuple[str, Blob]] = _find_file_entries(orig_file_iter, orig_filename)
    translated_files: Dict[str, Tuple[str, Blob]] = _find_file_entries(translated_file_iter, translated_filename)

    to_ret: List[PropEntry] = []
    for (common_folder, (original_path, original_blob), (translated_path, translated_blob)) \
            in common_entries(original_files, translated_files):

        orig_dict = get_entry_dict(get_text(original_blob))
        translated_dict = get_entry_dict(get_text(translated_blob))

        for key, orig_val in orig_dict.items():
            if len(orig_val.strip()) > 0 and (key not in translated_dict or len(translated_dict[key].strip()) < 1):
                to_ret.append(PropEntry(
                    rel_path=common_folder,
                    key=key,
                    value=orig_val))

    to_ret.sort(key=lambda rec: (rec.rel_path, rec.key))
    return to_ret
