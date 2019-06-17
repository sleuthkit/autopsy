import os
import sys


def main():
    reverse = False

    if len(sys.argv) > 1 and sys.argv[1] == '--reverse':
        reverse = True

    from_encoding = "utf-8" if reverse else "unicode_escape"
    to_encoding = "unicode_escape" if reverse else "utf-8"

    for root, dirs, files in os.walk("."):
        for file in files:
            if file == "Bundle_ja.properties":
                with open(root + "/" + file, "r", encoding=from_encoding) as f:
                    file_text = f.read()
                with open(root + "/" + file, "w", encoding=to_encoding) as f:
                    f.write(file_text)


if __name__ == '__main__':
    main()
