MODULE_NAME = "Android Analyzer Python"

"""
A parent class of the analyzers
"""
class AndroidComponentAnalyzer:
    # The Analyzer should implement this method
    def analyze(self, dataSource, fileManager, context):
        raise NotImplementedError
