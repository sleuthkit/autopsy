import os
import sys
import subprocess
from time import localtime, strftime
import traceback

# Returns a Windows style path starting with the cwd and
# ending with the list of directories given
def make_local_path(*dirs):
	path = wgetcwd().decode("utf-8")
	for dir in dirs:
		path += ("\\" + str(dir))
	return path_fix(path)

# Returns a Windows style path based only off the given directories
def make_path(*dirs):
	path = dirs[0]
	for dir in dirs[1:]:
		path += ("\\" + str(dir))
	return path_fix(path)

# Returns a path based on the os.
def make_os_path(platform, *dirs):
    if platform == "cygwin":
        path = ""
        for dir in dirs:
            path += str(dir).replace('\\', '/') + '/'
        return path_fix(path)
    elif platform == "win32":
        return make_path(*dirs)
    else:
        print("Couldn't make path, because we only support Windows and Cygwin at this time.")
        sys.exit(1)


# Fix a standard os.path by making it Windows format
def path_fix(path):
	return os.path.normcase(os.path.normpath(path))

# Gets the true current working directory instead of Cygwin's
def wgetcwd():
	proc = subprocess.Popen(("cygpath", "-m", os.getcwd()), stdout=subprocess.PIPE)
	out,err = proc.communicate()
	tst = out.rstrip()
	if os.getcwd == tst:
		return os.getcwd
	else:
		proc = subprocess.Popen(("cygpath", "-m", os.getcwd()), stdout=subprocess.PIPE)
		out,err = proc.communicate()
		return out.rstrip()
# Verifies a file's existance
def file_exists(file):
	try:
		if os.path.exists(file):
			return os.path.exists(file) and os.path.isfile(file)
	except:
		return False

# Verifies a directory's existance
def dir_exists(dir):
	try:
		return os.path.exists(dir) and os.path.isdir(dir)
	except:
		return False



# Returns the nth word in the given string or "" if n is out of bounds
# n starts at 0 for the first word
def get_word_at(string, n):
	words = string.split(" ")
	if len(words) >= n:
		return words[n]
	else:
		return ""

# Returns true if the given file is one of the required input files
# for ingest testing
def required_input_file(name):
	if ((name == "notablehashes.txt-md5.idx") or
	   (name == "notablekeywords.xml") or
	   (name == "nsrl.txt-md5.idx")):
	   return True
	else:
		return False

def image_type(image_file):
    if (dir_exists(image_file)):
        return IMGTYPE.LOGICAL
    ext_start = image_file.rfind(".")
    if (ext_start == -1):
        return IMGTYPE.UNKNOWN
    ext = image_file[ext_start:].lower()
    if (ext == ".img" or ext == ".dd"):
        return IMGTYPE.RAW
    elif (ext == ".e01"):
        return IMGTYPE.ENCASE
    elif (ext == ".aa" or ext == ".001"):
        return IMGTYPE.SPLIT
    else:
        return IMGTYPE.UNKNOWN

# Returns the type of image file, based off extension
class IMGTYPE:
    RAW, ENCASE, SPLIT, LOGICAL, UNKNOWN = range(5)

def get_image_name(image_file):
    path_end = image_file.rfind("/")
    path_end2 = image_file.rfind("\\")
    ext_start = image_file.rfind(".")
    if (image_type(image_file) == IMGTYPE.LOGICAL):
        name = image_file[path_end2+1:]
        return name
    if(ext_start == -1):
        name = image_file
    if(path_end2 != -1):
        name = image_file[path_end2+1:ext_start]
    elif(ext_start == -1):
        name = image_file[path_end+1:]
    elif(path_end == -1):
        name = image_file[:ext_start]
    elif(path_end!=-1 and ext_start!=-1):
        name = image_file[path_end+1:ext_start]
    else:
        name = image_file[path_end2+1:ext_start]
    return name

def usage():
    """Return the usage description of the test script."""
    return """
Usage:  ./regression.py [-f FILE] [OPTIONS]

        Run RegressionTest.java, and compare the result with a gold standard.
        By default, the script tests every image in ../input
        When the -f flag is set, this script only tests a single given image.
        When the -l flag is set, the script looks for a configuration file,
        which may outsource to a new input directory and to individual images.

        Expected files:
          An NSRL database at:          ../input/nsrl.txt-md5.idx
          A notable hash database at:    ../input/notablehashes.txt-md5.idx
          A notable keyword file at:      ../input/notablekeywords.xml

Options:
  -r            Rebuild the gold standards for the image(s) tested.
  -i            Ignores the ../input directory and all files within it.
  -u            Tells Autopsy not to ingest unallocated space.
  -k            Keeps each image's Solr index instead of deleting it.
  -v            Verbose mode; prints all errors to the screen.
  -e ex      Prints out all errors containing ex.
  -l cfg        Runs from configuration file cfg.
  -c            Runs in a loop over the configuration file until canceled. Must be used in conjunction with -l
  -fr           Will not try download gold standard images
    """

#####
# Enumeration definition (python 3.2 doesn't have enumerations, this is a common solution
# that allows you to access a named enum in a Java-like style, i.e. Numbers.ONE)
#####
def enum(*seq, **named):
    enums = dict(zip(seq, range(len(seq))), **named)
    return type('Enum', (), enums)


def get_files_by_ext(dir_path, ext):
    """Get a list of all the files with a given extenstion in the directory.

    Args:
        dir: a pathto_Dir, the directory to search.
        ext: a String, the extension to search for. i.e. ".html"
    """
    return [ os.path.join(dir_path, file) for file in os.listdir(dir_path) if
    file.endswith(ext) ]

