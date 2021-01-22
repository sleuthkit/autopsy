"""
    pyexcel.internal.attributes
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Book and sheet attributes

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel import constants as constants

ATTRIBUTE_REGISTRY = {
    constants.SHEET: {
        constants.READ_ACTION: set(),
        constants.WRITE_ACTION: set(),
        constants.RW_ACTION: set(),
    },
    constants.BOOK: {
        constants.READ_ACTION: set(),
        constants.WRITE_ACTION: set(),
        constants.RW_ACTION: set(),
    },
}


def register_book_attribute(target, action, attr):
    from .meta import BookMeta

    register_an_attribute(BookMeta, target, action, attr)


def register_sheet_attribute(target, action, attr):
    from .meta import SheetMeta

    register_an_attribute(SheetMeta, target, action, attr)


def register_an_attribute(meta_cls, target, action, attr):
    """Register a file type as an attribute"""

    if attr in ATTRIBUTE_REGISTRY[target][constants.RW_ACTION]:
        # No registration required
        return

    ATTRIBUTE_REGISTRY[target][action].add(attr)

    if action == constants.READ_ACTION:
        meta_cls.register_input(attr)
    else:
        meta_cls.register_presentation(attr)

    intersection = (
        attr in ATTRIBUTE_REGISTRY[target][constants.READ_ACTION]
        and attr in ATTRIBUTE_REGISTRY[target][constants.WRITE_ACTION]
    )
    if intersection:
        ATTRIBUTE_REGISTRY[target][constants.RW_ACTION].add(attr)
        ATTRIBUTE_REGISTRY[target][constants.READ_ACTION].remove(attr)
        ATTRIBUTE_REGISTRY[target][constants.WRITE_ACTION].remove(attr)
        meta_cls.register_io(attr)
