from collections import Counter


class PyexcelList(list):
    def __init__(self, *args):
        list.__init__(self, *args)

    def value_counts(self):
        from pyexcel import get_sheet

        c = Counter(self)
        sheet = get_sheet(adict=c)
        sheet.rownames = ["names", "counts"]
        return sheet
