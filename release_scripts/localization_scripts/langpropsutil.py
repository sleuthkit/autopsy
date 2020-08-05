"""Functions handling retrieving and storing when a language was last updated.
"""
from typing import Union
from envutil import get_proj_dir
from propsutil import get_entry_dict_from_path, update_entry_dict
from os import path


LANG_FILENAME = 'lastupdated.properties'


def _get_last_update_key(language: str) -> str:
    return "bundles.{lang}.lastupdated".format(lang=language)


def _get_props_path():
    return path.join(get_proj_dir(), LANG_FILENAME)


def get_commit_for_language(language: str) -> Union[str, None]:
    lang_dict = get_entry_dict_from_path(_get_props_path())
    if lang_dict is None:
        return None

    key = _get_last_update_key(language)
    if key not in lang_dict:
        return None

    return lang_dict[key]


def set_commit_for_language(language: str, latest_commit: str):
    key = _get_last_update_key(language)
    update_entry_dict({key: latest_commit}, _get_props_path())
