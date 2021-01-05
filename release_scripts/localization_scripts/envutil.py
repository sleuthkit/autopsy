"""Functions relating to the project environment.
"""

import pathlib
from typing import Union


def get_proj_dir(path: Union[pathlib.PurePath, str] = __file__) -> str:
    """
    Gets parent directory of this file (and subsequently, the project).

    Args:
        path: Can be overridden to provide a different file.  This will return the parent of that file in that instance.

    Returns:
        The project folder or the parent folder of the file provided.
    """
    return str(pathlib.Path(path).parent.absolute())
