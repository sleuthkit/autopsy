#!/usr/bin/env python3

import os

for root, dirs, files in os.walk("."):
    path = root.split(os.sep)
    for file in files:
        if file == "Bundle.properties-MERGED":
            with open(root + "/" + file, "rb") as f:
                file_text = f.read()
            with open(root + "/Bundle_ja.properties", "wb") as f:
                f.write(file_text)
