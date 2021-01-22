class ISheet(object):
    def row_iterator(self):
        raise NotImplementedError("iterate each row")

    def column_iterator(self, row):
        raise NotImplementedError("iterate each column at a given row")


class ISheetWriter(object):
    def write_row(self, data_row):
        raise NotImplementedError("How does your sheet write a row of data")

    def write_array(self, table):
        """
        For standalone usage, write an array
        """
        for row in table:
            self.write_row(row)

    def close(self):
        raise NotImplementedError("How would you close your file")


class NamedContent(object):
    """
    Helper class for content that does not have a name
    """

    def __init__(self, name, payload):
        self.name = name
        self.payload = payload
