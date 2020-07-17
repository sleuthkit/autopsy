from typing import TypeVar, Callable, Tuple


def is_match(content: str, regex: str) -> bool:
    pass

T = TypeVar('T')

def is_match_on_field(obj: T, prop_retriever: Callable[T, str]) -> bool:
    pass

class SeparationResult:

    def __init__(self, ):

def separate(obj: T, prop_retriever: Callable[T, str]) ->
