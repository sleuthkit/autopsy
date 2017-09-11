import smtplib
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders
import xml
from xml.dom.minidom import parse, parseString

def send_email(to, server, subj, body, attachments):
    """Send an email with the given information.

    Args:
        to: a String, the email address to send the email to
        server: a String, the mail server to send from
        subj: a String, the subject line of the message
        body: a String, the body of the message
        attachments: a listof_pathto_File, the attachements to include
    """
    msg = MIMEMultipart()
    msg['Subject'] = subj
    # me == the sender's email address
    # family = the list of all recipients' email addresses
    msg['From'] = 'AutopsyTest'
    msg['To'] = to
    msg.preamble = 'This is a test'
    container = MIMEText(body, 'plain')
    msg.attach(container)
    Build_email(msg, attachments)
    s = smtplib.SMTP(server)
    try:
        print('Sending Email')
        s.sendmail(msg['From'], msg['To'], msg.as_string())
    except Exception as e:
        print(str(e))
    s.quit()

def Build_email(msg, attachments):
    for file in attachments:
        part = MIMEBase('application', "octet-stream")
        atach = open(file, "rb")
        attch = atach.read()
        noml = file.split("\\")
        nom = noml[len(noml)-1]
        part.set_payload(attch)
        encoders.encode_base64(part)
        part.add_header('Content-Disposition', 'attachment; filename="' + nom + '"')
        msg.attach(part)

