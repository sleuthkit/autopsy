from .abstract_sheet import ISheet


class IReader(object):
    """
    content_array should be a list of NamedContent
    where: name is the sheet name,
           payload is the native sheet.
    """

    def read_sheet(self, sheet_index) -> ISheet:
        raise NotImplementedError("Read the sheet by index")

    def sheet_names(self):
        return [content.name for content in self.content_array]

    def __len__(self):
        return len(self.content_array)

    def close(self):
        raise NotImplementedError("Close the file")
