# cython: language_level=3
# cython: infer_types=True

import cython

MAX_COL_INDEX = cython.declare(cython.int, 18278)


def cache_column_letters(col_string_cache: list, string_col_cache: list):
    letters: str
    from string import ascii_uppercase as letters

    letter0 = letter1 = letter2 = letter3 = 0
    ch1 = ch2 = ch3 = prefix = letters[0]
    for i in range(1, MAX_COL_INDEX + 1):
        ch0 = letters[letter0]
        if i > 26*26*26:
            col = ch3 + prefix + ch0
        elif i > 26 * 26:
            col = prefix + ch0
        elif i > 26:
            col = ch1 + ch0
        else:
            col = ch0
        string_col_cache[i] = col
        col_string_cache[i] = i

        # next digit in base-26
        letter0 += 1
        if letter0 >= 26:
            letter0 = 0
            letter1 += 1
            if letter1 >= 26:
                letter1 = 0
                letter2 += 1
                if letter2 >= 26:
                    letter2 = 0
                    letter3 += 1
                    ch3 = letters[letter3]
                ch2 = letters[letter2]
            ch1 = letters[letter1]
            prefix = ch2 + ch1


_COL_STRING_CACHE = cython.declare(list, [0] * MAX_COL_INDEX)
_STRING_COL_CACHE = cython.declare(list, [0] * MAX_COL_INDEX)
#cache_column_letters(_STRING_COL_CACHE, _STRING_COL_CACHE)


def get_column_letter(idx):
    """Convert a column index into a column letter
    (3 -> 'C')
    """
    int_idx = cython.cast(cython.int, idx)  # may also raise ValueError
    if 1 <= int_idx <= MAX_COL_INDEX:
        return _STRING_COL_CACHE[int_idx]
    raise ValueError(f"Invalid column index {idx}")


def column_index_from_string(str_col: str):
    """Convert a column name into a numerical index
    ('A' -> 1)
    """
    # we use a function argument to get indexed name lookup
    col: cython.int = 0
    invalid = MAX_COL_INDEX + 1
    for ch in str_col:
        if 'A' <= ch <= 'Z':
            digit = ord(ch) - ord('A')
        elif 'a' <= ch <= 'z':
            digit = ord(ch) - ord('a')
        else:
            col = invalid
            break
        col = col * 26 + digit
        if col >= invalid:
            break
    if col >= invalid:
        raise ValueError(f"{str_col} is not a valid column name")
    return _COL_STRING_CACHE[col]


def coordinate_to_tuple(coordinate: str):
    """
    Convert an Excel style coordinate to (row, colum) tuple
    """
    length = len(coordinate)
    if not (2 <= length <= 1 + 3 + 1 + 7):  # A1 - $ZZZ$12345
        raise ValueError(f"invalid coordinate: {coordinate}")
    start: cython.int = 1 if coordinate[0] == '$' else 0

    i: cython.int = start
    col: cython.int = 0
    digit: cython.int
    while i < length:
        ch = coordinate[i]
        if 'A' <= ch <= 'Z':
            digit = ord(ch) - ord('A')
        elif 'a' <= ch <= 'z':
            digit = ord(ch) - ord('a')
        else:
            break
        col = col * 26 + digit
        i += 1

    if i >= length or i > start + 3:
        raise ValueError(f"invalid coordinate: {coordinate}")

    if coordinate[i] == '$':
        i += 1

    row: cython.int = 0
    while i < length and '0' <= coordinate[i] <= '9':
        row = row * 10 + (ord(coordinate[i]) - ord('0'))
        i += 1

    if i < length:
        raise ValueError(f"invalid coordinate: {coordinate}")

    return row, col


def cast_number(value: str):
    """
    Convert numbers as string to an int or float
    """
    # optimises for the common case of integers that fit into a C long
    intval: cython.long = 0
    safe_max_long: cython.long = 2 ** (cython.sizeof(cython.long) * 8 - 2)
    too_large = safe_max_long + 10
    for ch in value:
        if ch in '.eE':
            return float(value)
        elif ch in '0123456789':
            if intval >= safe_max_long // 10:
                intval = too_large
            else:
                intval = intval * 10 + (ord(ch) - ord('0'))
        else:
            intval = too_large

    if intval < too_large:
        return intval
    return int(value)
