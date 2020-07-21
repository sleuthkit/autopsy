class PropEntry:
    rel_path: str
    key: str
    value: str
    should_delete: bool

    def __init__(self, rel_path: str, key: str, value: str, should_delete: bool = False):
        """Defines a property file entry to be updated in a property file.

        Args:
            rel_path (str): The relative path for the property file.
            key (str): The key for the entry.
            value (str): The value for the entry.
            should_delete (bool, optional): Whether or not the key should simply be deleted. Defaults to False.
        """
        self.rel_path = rel_path
        self.key = key
        self.value = value
        self.should_delete = should_delete
