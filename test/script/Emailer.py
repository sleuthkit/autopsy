import smtplib
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders
import xml
from xml.dom.minidom import parse, parseString

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

