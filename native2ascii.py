import os
import subprocess
import sys


def main():
    reverse = False

    if len(sys.argv) > 1 and sys.argv[1] == '-reverse':
        reverse = True

    for root, dirs, files in os.walk("."):
        for file in files:
            if file == "Bundle_ja.properties":
                file_path = root + "/" + file
                if reverse:
                    subprocess.run(["native2ascii", file_path, file_path, "-reverse"])
                else:
                    subprocess.run(["native2ascii", file_path, file_path])


if __name__ == '__main__':
    main()
