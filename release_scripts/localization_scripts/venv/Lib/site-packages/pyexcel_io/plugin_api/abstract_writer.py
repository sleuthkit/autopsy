from .abstract_sheet import ISheetWriter


class IWriter(object):
    def create_sheet(self, sheet_name) -> ISheetWriter:
        raise NotImplementedError("Please implement a native sheet writer")

    def write(self, incoming_dict):
        for sheet_name in incoming_dict:
            sheet_writer = self.create_sheet(sheet_name)
            if sheet_writer:
                sheet_writer.write_array(incoming_dict[sheet_name])
                sheet_writer.close()
            else:
                raise Exception("Cannot create a sheet writer!")
