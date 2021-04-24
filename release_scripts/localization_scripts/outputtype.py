from enum import Enum


class OutputType(Enum):
    """Describes the output file type."""
    xlsx = 'xlsx'
    csv = 'csv'

    def __str__(self):
        return str(self.value)