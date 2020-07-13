from typing import Union
from propsutil import get_entry_dict
import pathlib
from os import path


LANG_FILENAME = 'lastupdated.properties'

def get_last_update_key(language: str) -> str:
    return "bundles.{lang}.lastupdated".format({lang=language})

def get_commit_for_language(language: str) -> Union[str, None]:
    this_path = path.join(get_props_file_path(), LANG_FILENAME)

    if path.isfile(this_path):
        lang_dict = get_entry_dict(this_path)
        key = get_last_update_key(language)
        if key in lang_dict:
            return lang_dict[key]

    return None

def set_commit_for_language(language: str, latest_commit: str):
    pass

def get_props_file_path() -> str:
    return str(pathlib.Path(__file__).parent.absolute())
