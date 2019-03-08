#!/usr/bin/env python3
#
# Copyright (c) 2013,Thibault Saunier <thibault.saunier@collabora.com>
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this program; if not, write to the
# Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
# Boston, MA 02110-1301, USA.

import os
import time
from . import loggable
import subprocess
import sys
import urllib.request, urllib.error, urllib.parse

logcat = "httpserver"


class HTTPServer(loggable.Loggable):

    """ Class to run a SimpleHttpServer in a process."""

    def __init__(self, options):
        loggable.Loggable.__init__(self)
        self.options = options
        self._process = None
        self._logsfile = None

    def _check_is_up(self, timeout=60):
        """ Check if the server is up, running a simple test based on wget. """
        start = time.time()
        while True:
            try:
                response = urllib.request.urlopen('http://127.0.0.1:%s' % (
                    self.options.http_server_port))
                return True
            except urllib.error.URLError as e:
                pass

            if time.time() - start > timeout:
                return False

            time.sleep(1)

    def start(self):
        """ Start the server in a subprocess """
        self._logsfile = open(os.path.join(self.options.logsdir,
                                           "httpserver.logs"), 'w+')
        if self.options.http_server_dir is not None:
            if self._check_is_up(timeout=2):
                return True

            print("Starting Server")
            try:
                self.debug("Launching http server")
                cmd = "%s %s %d %s" % (sys.executable, os.path.join(os.path.dirname(__file__),
                                                                    "RangeHTTPServer.py"),
                                       self.options.http_server_port,
                                       self.options.http_bandwith,
                                       )
                curdir = os.path.abspath(os.curdir)
                os.chdir(self.options.http_server_dir)
                # cmd = "twistd -no web --path=%s -p %d" % (
                # self.options.http_server_dir, self.options.http_server_port)
                self.debug(
                    "Launching server: %s (logs in %s)", cmd, self._logsfile)
                self._process = subprocess.Popen(cmd.split(" "),
                                                 stderr=self._logsfile,
                                                 stdout=self._logsfile)
                os.chdir(curdir)
                self.debug("Launched http server")
                # Dirty way to avoid eating to much CPU...
                # good enough for us anyway.
                time.sleep(1)

                if self._check_is_up():
                    print("Started")
                    return True
                else:
                    print("Failed starting server")
                    self._process.terminate()
                    self._process = None
            except OSError as ex:
                print("Failed starting server")
                self.warning(logcat, "Could not launch server %s" % ex)

        return False

    def stop(self):
        """ Stop the server subprocess if running. """
        if self._process:
            self._process.terminate()
            self._process = None
            self.debug("Server stopped")
