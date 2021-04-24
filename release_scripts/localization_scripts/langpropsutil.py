"""Functions handling retrieving and storing when a language was last updated.
"""
from typing import Union
from envutil import get_proj_dir
from propsutil import get_entry_dict_from_path, update_entry_dict
from os import path


LANG_FILENAME = 'lastupdated.properties'


def _get_last_update_key(language: str) -> str:
    return "bundles.{lang}.lastupdated".format(lang=language)


def _get_props_path(language_updates_file: Union[str, None]):
    if language_updates_file:
        return language_updates_file
    else:
        return path.join(get_proj_dir(), LANG_FILENAME)


def get_commit_for_language(language: str, language_updates_file: Union[str, None] = None) -> Union[str, None]:
    """
    Retrieves the latest commit for a particular language.

    Args:
        language: The language key.
        language_updates_file: The file containing the most recent updates.  If not provided, the default file located
        in the same directory as the running script is used.

    Returns: The most recent commit that the particular language has been updated or None if no key exists.

    """
    lang_dict = get_entry_dict_from_path(_get_props_path(language_updates_file))
    if lang_dict is None:
        return None

    key = _get_last_update_key(language)
    if key not in lang_dict:
        return None

    return lang_dict[key]


def set_commit_for_language(language: str, latest_commit: str, language_updates_file: Union[str, None] = None):
    """
    Sets the most recent update for a language within the language updates file.

    Args:
        language: The language key.
        latest_commit: The commit for how recent the language is.
        language_updates_file: The file containing the most recent updates.  If not provided, the default file located
        in the same directory as the running script is used.

    """
    key = _get_last_update_key(language)
    update_entry_dict({key: latest_commit}, _get_props_path(language_updates_file))
