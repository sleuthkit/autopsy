import smtplib
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders
import xml
from time import localtime, strftime
from xml.dom.minidom import parse, parseString
import subprocess
import sys
import os

def send_email(parsed, errorem, attachl, passFail):
	element = parsed.getElementsByTagName("email")
	if(len(element)<=0):
		return
	element = element[0]
	toval = element.getAttribute("value").encode().decode("utf_8")
	if(toval==None):
		return
	element = parsed.getElementsByTagName("mail_server")[0]
	serverval = element.getAttribute("value").encode().decode("utf_8")
	# Create the container (outer) email message.
	msg = MIMEMultipart()
	element = parsed.getElementsByTagName("subject")[0]
	subval = element.getAttribute("value").encode().decode("utf_8")
	if(passFail):
		msg['Subject'] = '[Test]Autopsy ' + subval + ' test passed.'
	else:
		msg['Subject'] = '[Test]Autopsy ' + subval + ' test failed.'
	# me == the sender's email address
	# family = the list of all recipients' email addresses
	msg['From'] = 'AutopsyTest'
	msg['To'] = toval
	msg.preamble = 'This is a test'
	container = MIMEText(errorem, 'plain')
	msg.attach(container)
	Build_email(msg, attachl)
	s = smtplib.SMTP(serverval)
	try:
		print('Sending Email')
		s.sendmail(msg['From'], msg['To'], msg.as_string())
	except Exception as e:
		print(str(e))
	s.quit()
	
def Build_email(msg, attachl):
	for file in attachl:
		part = MIMEBase('application', "octet-stream")
		atach = open(file, "rb")
		attch = atach.read()
		noml = file.split("\\")
		nom = noml[len(noml)-1]
		part.set_payload(attch)
		encoders.encode_base64(part)
		part.add_header('Content-Disposition', 'attachment; filename="' + nom + '"')
		msg.attach(part)
		
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
	
# Fix a standard os.path by making it Windows format
def path_fix(path):
	return path.replace("/", "\\")
	
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
			return os.path.isfile(file)
	except:
		return False
		
# Verifies a directory's existance
def dir_exists(dir):
	try:
		return os.path.exists(dir)
	except:
		return False

# Compares file a to file b and any differences are returned
# Only works with report html files, as it searches for the first <ul>
def compare_report_files(a_path, b_path):
	a_file = open(a_path)
	b_file = open(b_path)
	a = a_file.read()
	b = b_file.read()
	a = a[a.find("<ul>"):]
	b = b[b.find("<ul>"):]
	
	a_list = split(a, 50)
	b_list = split(b, 50)
	if not len(a_list) == len(b_list):
		ex = (len(a_list), len(b_list))
		return ex
	else: 
		return (0, 0)
  
# Split a string into an array of string of the given size
def split(input, size):
	return [input[start:start+size] for start in range(0, len(input), size)]

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