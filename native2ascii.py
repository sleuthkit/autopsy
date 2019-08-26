#!/usr/bin/env python3

import os
import re
import subprocess
import sys


def main(reverse):
    if len(sys.argv) > 1 and sys.argv[1] == '-reverse':
        reverse = True

    for root, dirs, files in os.walk("."):
        for file in files:
            if re.match(r"Bundle_[a-z]{2}\.properties", file):
                file_path = root + "/" + file
                if reverse:
                    subprocess.run(["native2ascii", file_path, file_path, "-reverse"])
                else:
                    subprocess.run(["native2ascii", file_path, file_path])


def err(msg):
    print(msg)
    exit(1)


if __name__ == '__main__':
    usage_str = "Usage: " + sys.argv[0] + " [[-reverse]]"

    if len(sys.argv) > 2:
        err(usage_str)

    if len(sys.argv) == 1:
        main(False)
    else:
        if sys.argv[1] == "-reverse":
            main(True)
        else:
            err("Argument must be empty or '-reverse'")
