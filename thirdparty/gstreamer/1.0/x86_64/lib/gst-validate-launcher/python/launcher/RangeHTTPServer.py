#!/usr/bin/env python3

# Portions Copyright (C) 2009,2010  Xyne
# Portions Copyright (C) 2011 Sean Goller
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# (version 2) as published by the Free Software Foundation.
#
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
# 02110-1301, USA.


"""Range HTTP Server.

This module builds on BaseHTTPServer by implementing the standard GET
and HEAD requests in a fairly straightforward manner, and includes support
for the Range header.

"""


__version__ = "0.1"

__all__ = ["RangeHTTPRequestHandler"]

import os
import sys

from socketserver import ThreadingMixIn

import posixpath
import http.server
import urllib.parse
import html
import shutil
import mimetypes
import io
import time


_bandwidth = 0

class ThreadingSimpleServer(ThreadingMixIn, http.server.HTTPServer):
    pass

class RangeHTTPRequestHandler(http.server.BaseHTTPRequestHandler):

    """Simple HTTP request handler with GET and HEAD commands.

    This serves files from the current directory and any of its
    subdirectories.  The MIME type for files is determined by
    calling the .guess_type() method.

    The GET and HEAD requests are identical except that the HEAD
    request omits the actual contents of the file.

    """

    server_version = "RangeHTTP/" + __version__

    def do_GET(self):
        """Serve a GET request."""
        f, start_range, end_range = self.send_head()
        print ("Got values of {} and {}".format(start_range, end_range))
        if f:
            f.seek(start_range, 0)
            chunk = 0x1000
            total = 0
            while chunk > 0:
                if start_range + chunk > end_range:
                    chunk = end_range - start_range

                if _bandwidth != 0:
                    time_to_sleep = float(float(chunk) / float(_bandwidth))
                    time.sleep(time_to_sleep)

                try:
                    self.wfile.write(f.read(chunk))
                except:
                    break
                total += chunk
                start_range += chunk
            f.close()

    def do_HEAD(self):
        """Serve a HEAD request."""
        f, start_range, end_range = self.send_head()
        if f:
            f.close()

    def send_head(self):
        """Common code for GET and HEAD commands.

        This sends the response code and MIME headers.

        Return value is either a file object (which has to be copied
        to the outputfile by the caller unless the command was HEAD,
        and must be closed by the caller under all circumstances), or
        None, in which case the caller has nothing further to do.

        """
        path = self.translate_path(self.path)
        f = None
        if os.path.isdir(path):
            if not self.path.endswith("/"):
                # redirect browser
                self.send_response(301)
                self.send_header("Location", self.path + "/")
                self.end_headers()
                return (None, 0, 0)
            for index in "index.html", "index.html":
                index = os.path.join(path, index)
                if os.path.exists(index):
                    path = index
                    break
            else:
                return self.list_directory(path)
        ctype = self.guess_type(path)

        try:
            # Always read in binary mode. Opening files in text mode may cause
            # newline translations, making the actual size of the content
            # transmitted *less* than the content-length!
            f = open(path, "rb")
        except IOError:
            self.send_error(404, "File not found")
            return (None, 0, 0)

        if "Range" in self.headers:
            self.send_response(206) #partial content response
        else :
            self.send_response(200)

        self.send_header("Content-type", ctype)
        file_size = os.path.getsize(path)

        start_range = 0
        end_range = file_size

        self.send_header("Accept-Ranges", "bytes")
        if "Range" in self.headers:
            s, e = self.headers['range'][6:].split('-', 1) #bytes:%d-%d
            sl = len(s)
            el = len(e)

            if sl:
                start_range = int(s)
                if el:
                    end_range = int(e) + 1
            elif el:
                start_range = file_size - min(file_size, int(e))

        self.send_header("Content-Range", "bytes {}-{}/{}".format(start_range, end_range, file_size))
        self.send_header("Content-Length", end_range - start_range)
        self.end_headers()

        print ("Sending bytes {} to {}...".format(start_range, end_range))
        return (f, start_range, end_range)

    def list_directory(self, path):
        """Helper to produce a directory listing (absent index.html).

                Return value is either a file object, or None (indicating an
                error).  In either case, the headers are sent, making the
                interface the same as for send_head().

                """
        try:
            lst = os.listdir(path)
        except OSError:
            self.send_error(404, "Access Forbidden")
            return None

        lst.sort(key=lambda file_name : file_name.lower())
        html_text = []

        displaypath = html.escape(urllib.parse.unquote(self.path))
        html_text.append('<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">')
        html_text.append("<html>\n<title>Directory listing for {}</title>\n".format(displaypath))
        html_text.append("<body>\n<h2>Directory listing for {}</h2>\n".format(displaypath))
        html_text.append("<hr>\n<ul>\n")

        for name in lst:
            fullname = os.path.join(path, name)
            displayname = linkname = name

            if os.path.isdir(fullname):
                displayname = name + "/"
                linkname = name + "/"

            if os.path.islink(fullname):
                displayname = name + "@"

            html_text.append('<li><a href = "{}">{}</a>\n'.format(urllib.parse.quote(linkname), html.escape(displayname)))

        html_text.append('</ul>\n</hr>\n</body>\n</html>\n')

        byte_encoded_string = "\n".join(html_text).encode("utf-8", "surrogateescape")
        f = io.BytesIO()
        f.write(byte_encoded_string)
        length = len(byte_encoded_string)

        f.seek(0)

        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.send_header("Content-length", str(length))
        self.end_headers()

        return (f, 0, length)

    def translate_path(self, path):
        """Translate a /-separated PATH to the local filename syntax.

        Components that mean special things to the local file system
        (e.g. drive or directory names) are ignored.  (XXX They should
        probably be diagnosed.)

        """
        #abandon query parameters
        path = path.split("?", 1)[0]
        path = path.split("#", 1)[0]
        path = posixpath.normpath(urllib.parse.unquote(path))
        words = path.split("/")
        words = filter(None, words)
        path = os.getcwd()

        for word in words:
            drive, word = os.path.splitdrive(word)
            head, word = os.path.split(word)
            if word in (os.curdir, os.pardir): continue
            path = os.path.join(path, word)
        return path


    def guess_type(self, path):
        """Guess the type of a file.

        Argument is a PATH (a filename).

        Return value is a string of the form type/subtype,
        usable for a MIME Content-type header.

        The default implementation looks the file's extension
        up in the table self.extensions_map, using application/octet-stream
        as a default; however it would be permissible (if
        slow) to look inside the data to make a better guess.

        """

        base, ext = posixpath.splitext(path)
        if ext in self.extension_map:
            return self.extension_map[ext]
        ext = ext.lower()
        if ext in self.extension_map:
            return self.extension_map[ext]
        else:
            return self.extension_map['']

    if not  mimetypes.inited:
        mimetypes.init()
    extension_map = mimetypes.types_map.copy()
    extension_map.update({
            '': 'application/octet-stream', # Default
            '.py': 'text/plain',
            '.c': 'text/plain',
            '.h': 'text/plain',
            '.mp4': 'video/mp4',
            '.ogg': 'video/ogg',
            '.java' : 'text/plain',
        })


def test(handler_class = RangeHTTPRequestHandler,server_class = http.server.HTTPServer):
    http.server.test(handler_class, server_class)

if __name__ == "__main__":
    httpd = ThreadingSimpleServer(("0.0.0.0", int(sys.argv[1])), RangeHTTPRequestHandler)
    httpd.serve_forever()
