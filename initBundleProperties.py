#!/usr/bin/env python3

import os
import sys


def main(lang):
    for root, dirs, files in os.walk("."):
        for file in files:
            if file == "Bundle.properties-MERGED":
                with open(root + "/" + file, "rb") as f:
                    file_text = f.read()
                with open(root + "/Bundle_" + lang + ".properties", "wb") as f:
                    f.write(file_text)


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: " + sys.argv[0] + " [lang]")
        exit(0)
    if len(sys.argv[1]) != 2:
        print("Language code must be two-letter")
        exit(0)
    main(sys.argv[1])
