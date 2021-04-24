class FoundValue:
    """
    A class containing a record of a prop key existing in both an original props file and a translated props file
    """
    common_path: str
    original_file: str
    translated_file: str
    key: str
    orig_val: str
    translated_val: str

    def __init__(self, common_path, original_file, translated_file, key, orig_val, translated_val):
        """
        Constructor.

        Args:
            common_path: The folder common to both files.
            original_file: The original file path.
            translated_file: The translated file path.
            key: The common prop key.
            orig_val: The original (English) value.
            translated_val: The translated value.
        """
        self.common_path = common_path
        self.original_file = original_file
        self.translated_file = translated_file
        self.key = key
        self.orig_val = orig_val
        self.translated_val = translated_val
