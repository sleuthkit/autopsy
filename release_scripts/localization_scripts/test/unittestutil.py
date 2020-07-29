import os
from typing import Union

from envutil import get_proj_dir

TEST_ARTIFACT_FOLDER = 'artifacts'
TEST_OUTPUT_FOLDER = 'output'


def get_output_path(filename: Union[str, None] = None) -> str:
    if filename is None:
        return os.path.join(get_proj_dir(__file__), TEST_ARTIFACT_FOLDER, TEST_OUTPUT_FOLDER)
    else:
        return os.path.join(get_proj_dir(__file__), TEST_ARTIFACT_FOLDER, TEST_OUTPUT_FOLDER, filename)
