def default_getter(attribute=None):
    """a default method for missing renderer method

    for example, the support to write data in a specific file type
    is missing but the support to read data exists
    """

    def none_presenter(_, **__):
        """docstring is assigned a few lines down the line"""
        raise NotImplementedError("%s getter is not defined." % attribute)

    none_presenter.__doc__ = "%s getter is not defined." % attribute
    return none_presenter


def default_setter(attribute=None):
    """a default method for missing parser method

    for example, the support to read data in a specific file type
    is missing but the support to write data exists
    """

    def none_importer(_x, _y, **_z):
        """docstring is assigned a few lines down the line"""
        raise NotImplementedError("%s setter is not defined." % attribute)

    none_importer.__doc__ = "%s setter is not defined." % attribute
    return none_importer


def make_a_property(
    cls,
    attribute,
    doc_string,
    getter_func=default_getter,
    setter_func=default_setter,
):
    """
    create custom attributes for each class
    """
    getter = getter_func(attribute)
    setter = setter_func(attribute)
    attribute_property = property(
        # note:
        # without fget, fset, pypy 5.4.0 crashes randomly.
        fget=getter,
        fset=setter,
        doc=doc_string,
    )
    if "." in attribute:
        attribute = attribute.replace(".", "_")
    else:
        attribute = attribute
    setattr(cls, attribute, attribute_property)
    setattr(cls, "get_%s" % attribute, getter)
    setattr(cls, "set_%s" % attribute, setter)
