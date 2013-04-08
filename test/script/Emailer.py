import smtplib
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.MIMEBase import MIMEBase
from email import Encoders
import urllib2
import xml
from time import localtime, strftime
from xml.dom.minidom import parse, parseString
import subprocess
import sys
import os

def send_email(parsed, errorem, attachl, passFail):
	if(not args.list):
		sys.exit()
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
	if(passFail):
		msg['Subject'] = 'Autopsy Nightly test passed.'
	else:
		msg['Subject'] = 'Autopsy Nightly test failed.'
	# me == the sender's email address
	# family = the list of all recipients' email addresses
	msg['From'] = 'AutopsyContinuousTest'
	msg['To'] = toval
	msg.preamble = 'This is a test'
	container = MIMEText(errorem, 'plain')
	msg.attach(container)
	Build_email(msg, attachl)
	s = smtplib.SMTP(serverval)
	s.sendmail(msg['From'], msg['To'], msg.as_string())
	s.quit()
	
def Build_email(msg, attachl):
	for file in attachl:
		part = MIMEBase('application', "octet-stream")
		atach = open(file, "rb")
		attch = atach.read()
		noml = file.split("\\")
		nom = noml[len(noml)-1]
		part.set_payload(attch)
		Encoders.encode_base64(part)
		part.add_header('Content-Disposition', 'attachment; filename="' + nom + '"')
		msg.attach(part)
		
# Returns a Windows style path starting with the cwd and
# ending with the list of directories given
def make_local_path(*dirs):
	path = wgetcwd()
	for dir in dirs:
		path += ("\\" + dir)
	return path_fix(path)

# Returns a Windows style path based only off the given directories
def make_path(*dirs):
	path = dirs[0]
	for dir in dirs[1:]:
		path += ("\\" + dir)
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