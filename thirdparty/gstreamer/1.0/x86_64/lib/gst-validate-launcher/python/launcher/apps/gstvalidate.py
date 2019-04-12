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
import argparse
import os
import copy
import sys
import time
import urllib.parse
import shlex
import socket
import subprocess
import configparser
from launcher.loggable import Loggable

from launcher.baseclasses import GstValidateTest, Test, \
    ScenarioManager, NamedDic, GstValidateTestsGenerator, \
    GstValidateMediaDescriptor, GstValidateEncodingTestInterface, \
    GstValidateBaseTestManager, MediaDescriptor, MediaFormatCombination

from launcher.utils import path2url, url2path, DEFAULT_TIMEOUT, which, \
    GST_SECOND, Result, Protocols, mkdir, printc, Colors, get_data_file, \
    kill_subprocess

#
# Private global variables     #
#

# definitions of commands to use
parser = argparse.ArgumentParser(add_help=False)
parser.add_argument("--validate-tools-path", dest="validate_tools_path",
                    default="",
                    help="defines the paths to look for GstValidate tools.")
options, args = parser.parse_known_args()

GST_VALIDATE_COMMAND = which("gst-validate-1.0", options.validate_tools_path)
GST_VALIDATE_TRANSCODING_COMMAND = which("gst-validate-transcoding-1.0",
                                         options.validate_tools_path)
G_V_DISCOVERER_COMMAND = which("gst-validate-media-check-1.0",
                               options.validate_tools_path)
GST_VALIDATE_RTSP_SERVER_COMMAND = which("gst-validate-rtsp-server-1.0",
                                         options.validate_tools_path)

ScenarioManager.GST_VALIDATE_COMMAND = GST_VALIDATE_COMMAND
AUDIO_ONLY_FILE_TRANSCODING_RATIO = 5

#
# API to be used to create testsuites     #
#

"""
Some info about protocols and how to handle them
"""
GST_VALIDATE_CAPS_TO_PROTOCOL = [("application/x-hls", Protocols.HLS),
                                 ("application/dash+xml", Protocols.DASH)]


class GstValidateMediaCheckTestsGenerator(GstValidateTestsGenerator):

    def __init__(self, test_manager):
        GstValidateTestsGenerator.__init__(self, "media_check", test_manager)

    def populate_tests(self, uri_minfo_special_scenarios, scenarios):
        for uri, mediainfo, special_scenarios in uri_minfo_special_scenarios:
            protocol = mediainfo.media_descriptor.get_protocol()
            timeout = DEFAULT_TIMEOUT

            classname = "%s.media_check.%s" % (protocol,
                                               os.path.basename(url2path(uri)).replace(".", "_"))
            self.add_test(GstValidateMediaCheckTest(classname,
                                                    self.test_manager.options,
                                                    self.test_manager.reporter,
                                                    mediainfo.media_descriptor,
                                                    uri,
                                                    mediainfo.path,
                                                    timeout=timeout))


class GstValidateTranscodingTestsGenerator(GstValidateTestsGenerator):

    def __init__(self, test_manager):
        GstValidateTestsGenerator.__init__(self, "transcode", test_manager)

    def populate_tests(self, uri_minfo_special_scenarios, scenarios):
        for uri, mediainfo, special_scenarios in uri_minfo_special_scenarios:
            if mediainfo.media_descriptor.is_image():
                continue

            protocol = mediainfo.media_descriptor.get_protocol()
            if protocol == Protocols.RTSP:
                continue

            for comb in self.test_manager.get_encoding_formats():
                classname = "%s.transcode.to_%s.%s" % (mediainfo.media_descriptor.get_protocol(),
                                                       str(comb).replace(
                                                       ' ', '_'),
                                                       mediainfo.media_descriptor.get_clean_name())
                self.add_test(GstValidateTranscodingTest(classname,
                                                         self.test_manager.options,
                                                         self.test_manager.reporter,
                                                         comb,
                                                         uri,
                                                         mediainfo.media_descriptor))


class FakeMediaDescriptor(MediaDescriptor):

    def __init__(self, infos, pipeline_desc):
        MediaDescriptor.__init__(self)
        self._infos = infos
        self._pipeline_desc = pipeline_desc

    def get_path(self):
        return self._infos.get('path', None)

    def get_media_filepath(self):
        return self._infos.get('media-filepath', None)

    def get_caps(self):
        return self._infos.get('caps', None)

    def get_uri(self):
        return self._infos.get('uri', None)

    def get_duration(self):
        return int(self._infos.get('duration', 0)) * GST_SECOND

    def get_protocol(self):
        return self._infos.get('protocol', "launch_pipeline")

    def is_seekable(self):
        return self._infos.get('is-seekable', True)

    def is_image(self):
        return self._infos.get('is-image', False)

    def is_live(self):
        return self._infos.get('is-live', False)

    def get_num_tracks(self, track_type):
        return self._infos.get('num-%s-tracks' % track_type,
                               self._pipeline_desc.count(track_type + "sink"))

    def can_play_reverse(self):
        return self._infos.get('plays-reverse', False)


class GstValidatePipelineTestsGenerator(GstValidateTestsGenerator):

    def __init__(self, name, test_manager, pipeline_template=None,
                 pipelines_descriptions=None, valid_scenarios=[]):
        """
        @name: The name of the generator
        @pipeline_template: A template pipeline to be used to generate actual pipelines
        @pipelines_descriptions: A list of tuple of the form:
                                 (test_name, pipeline_description, extra_data)
                                 extra_data being a dictionnary with the follwing keys:
                                    'scenarios': ["the", "valide", "scenarios", "names"]
                                    'duration': the_duration # in seconds
                                    'timeout': a_timeout # in seconds
                                    'hard_timeout': a_hard_timeout # in seconds

        @valid_scenarios: A list of scenario name that can be used with that generator
        """
        GstValidateTestsGenerator.__init__(self, name, test_manager)
        self._pipeline_template = pipeline_template
        self._pipelines_descriptions = pipelines_descriptions
        self._valid_scenarios = valid_scenarios

    def get_fname(self, scenario, protocol=None, name=None):
        if name is None:
            name = self.name

        if protocol is not None:
            protocol_str = "%s." % protocol
        else:
            protocol_str = ""

        if scenario is not None and scenario.name.lower() != "none":
            return "%s%s.%s" % (protocol_str, name, scenario.name)

        return ("%s.%s.%s" % (protocol_str, self.name, name)).replace("..", ".")

    def generate_tests(self, uri_minfo_special_scenarios, scenarios):
        if self._valid_scenarios is None:
            scenarios = [None]
        elif self._valid_scenarios:
            scenarios = [scenario for scenario in scenarios if
                         scenario is not None and scenario.name in self._valid_scenarios]

        return super(GstValidatePipelineTestsGenerator, self).generate_tests(
            uri_minfo_special_scenarios, scenarios)

    def populate_tests(self, uri_minfo_special_scenarios, scenarios):
        for description in self._pipelines_descriptions:
            name = description[0]
            pipeline = description[1]
            if len(description) == 3:
                extra_datas = description[2]
            else:
                extra_datas = {}

            for scenario in extra_datas.get('scenarios', scenarios):
                if isinstance(scenario, str):
                    scenario = self.test_manager.scenarios_manager.get_scenario(
                        scenario)

                mediainfo = FakeMediaDescriptor(extra_datas, pipeline)
                if not mediainfo.is_compatible(scenario):
                    continue

                if self.test_manager.options.mute:
                    if scenario and scenario.needs_clock_sync():
                        audiosink = "fakesink sync=true"
                        videosink = "fakesink sync=true qos=true max-lateness=20000000"
                    else:
                        audiosink = videosink = "fakesink"
                else:
                    audiosink = 'autoaudiosink'
                    videosink = 'autovideosink'

                pipeline_desc = pipeline % {'videosink': videosink,
                                            'audiosink': audiosink}

                fname = self.get_fname(
                    scenario, protocol=mediainfo.get_protocol(), name=name)

                expected_failures = extra_datas.get("expected-failures")
                extra_env_vars = extra_datas.get("extra_env_vars")
                self.add_test(GstValidateLaunchTest(fname,
                                                    self.test_manager.options,
                                                    self.test_manager.reporter,
                                                    pipeline_desc,
                                                    scenario=scenario,
                                                    media_descriptor=mediainfo,
                                                    expected_failures=expected_failures,
                                                    extra_env_variables=extra_env_vars)
                              )


class GstValidatePlaybinTestsGenerator(GstValidatePipelineTestsGenerator):

    def __init__(self, test_manager):
        if os.getenv("USE_PLAYBIN3") is None:
            GstValidatePipelineTestsGenerator.__init__(
                self, "playback", test_manager, "playbin")
        else:
            GstValidatePipelineTestsGenerator.__init__(
                self, "playback", test_manager, "playbin3")

    def _set_sinks(self, minfo, pipe_str, scenario):
        if self.test_manager.options.mute:
            if scenario.needs_clock_sync() or \
                    minfo.media_descriptor.need_clock_sync():
                afakesink = "'fakesink sync=true'"
                vfakesink = "'fakesink sync=true qos=true max-lateness=20000000'"
            else:
                vfakesink = afakesink = "'fakesink'"

            pipe_str += " audio-sink=%s video-sink=%s" % (
                afakesink, vfakesink)

        return pipe_str

    def _get_name(self, scenario, protocol, minfo):
        return "%s.%s" % (self.get_fname(scenario,
                                         protocol),
                          os.path.basename(minfo.media_descriptor.get_clean_name()))

    def populate_tests(self, uri_minfo_special_scenarios, scenarios):
        test_rtsp = GST_VALIDATE_RTSP_SERVER_COMMAND
        if not test_rtsp:
            printc("\n\nRTSP server not available, you should make sure"
                   " that %s is available in your $PATH." % GST_VALIDATE_RTSP_SERVER_COMMAND,
                   Colors.FAIL)
        elif self.test_manager.options.disable_rtsp:
            printc("\n\nRTSP tests are disabled")
            test_rtsp = False

        for uri, minfo, special_scenarios in uri_minfo_special_scenarios:
            pipe = self._pipeline_template
            protocol = minfo.media_descriptor.get_protocol()

            if protocol == Protocols.RTSP:
                self.debug("SKIPPING %s as it is a RTSP stream" % uri)
                continue

            pipe += " uri=%s" % uri

            for scenario in special_scenarios + scenarios:
                cpipe = pipe
                if not minfo.media_descriptor.is_compatible(scenario):
                    continue

                cpipe = self._set_sinks(minfo, cpipe, scenario)
                fname = self._get_name(scenario, protocol, minfo)

                self.debug("Adding: %s", fname)

                if scenario.does_reverse_playback() and protocol == Protocols.HTTP:
                    # 10MB so we can reverse playback
                    cpipe += " ring-buffer-max-size=10485760"

                self.add_test(GstValidateLaunchTest(fname,
                                                    self.test_manager.options,
                                                    self.test_manager.reporter,
                                                    cpipe,
                                                    scenario=scenario,
                                                    media_descriptor=minfo.media_descriptor)
                              )

                if test_rtsp and protocol == Protocols.FILE and not minfo.media_descriptor.is_image():
                    rtspminfo = NamedDic({"path": minfo.media_descriptor.get_path(),
                                          "media_descriptor": GstValidateRTSPMediaDesciptor(minfo.media_descriptor.get_path())})
                    if not rtspminfo.media_descriptor.is_compatible(scenario):
                        continue

                    cpipe = self._set_sinks(rtspminfo, "%s uri=rtsp://127.0.0.1:<RTSPPORTNUMBER>/test"
                                            % self._pipeline_template, scenario)
                    fname = self._get_name(scenario, Protocols.RTSP, rtspminfo)

                    self.add_test(GstValidateRTSPTest(
                        fname, self.test_manager.options, self.test_manager.reporter,
                        cpipe, uri, scenario=scenario,
                        media_descriptor=rtspminfo.media_descriptor))

                    fname = self._get_name(scenario, Protocols.RTSP + '2', rtspminfo)
                    self.add_test(GstValidateRTSPTest(
                        fname, self.test_manager.options, self.test_manager.reporter,
                        cpipe, uri, scenario=scenario,
                        media_descriptor=rtspminfo.media_descriptor,
                        rtsp2=True))


class GstValidateMixerTestsGenerator(GstValidatePipelineTestsGenerator):

    def __init__(self, name, test_manager, mixer, media_type, converter="",
                 num_sources=3, mixed_srcs={}, valid_scenarios=[]):
        pipe_template = "%(mixer)s name=_mixer !  " + \
            converter + " ! %(sink)s "
        self.converter = converter
        self.mixer = mixer
        self.media_type = media_type
        self.num_sources = num_sources
        self.mixed_srcs = mixed_srcs
        super(
            GstValidateMixerTestsGenerator, self).__init__(name, test_manager, pipe_template,
                                                           valid_scenarios=valid_scenarios)

    def populate_tests(self, uri_minfo_special_scenarios, scenarios):
        if self.test_manager.options.validate_uris:
            return

        wanted_ressources = []
        for uri, minfo, special_scenarios in uri_minfo_special_scenarios:
            protocol = minfo.media_descriptor.get_protocol()
            if protocol == Protocols.FILE and \
                    minfo.media_descriptor.get_num_tracks(self.media_type) > 0:
                wanted_ressources.append((uri, minfo))

        if not self.mixed_srcs:
            if not wanted_ressources:
                return

            for i in range(len(uri_minfo_special_scenarios) / self.num_sources):
                srcs = []
                name = ""
                for nsource in range(self.num_sources):
                    uri, minfo = wanted_ressources[i + nsource]
                    if os.getenv("USE_PLAYBIN3") is None:
                        srcs.append(
                            "uridecodebin uri=%s ! %s" % (uri, self.converter))
                    else:
                        srcs.append(
                            "uridecodebin3 uri=%s ! %s" % (uri, self.converter))
                    fname = os.path.basename(uri).replace(".", "_")
                    if not name:
                        name = fname
                    else:
                        name += "+%s" % fname

                self.mixed_srcs[name] = tuple(srcs)

        for name, srcs in self.mixed_srcs.items():
            if isinstance(srcs, dict):
                pipe_arguments = {
                    "mixer": self.mixer + " %s" % srcs["mixer_props"]}
                srcs = srcs["sources"]
            else:
                pipe_arguments = {"mixer": self.mixer}

            for scenario in scenarios:
                fname = self.get_fname(scenario, Protocols.FILE) + "."
                fname += name

                self.debug("Adding: %s", fname)

                if self.test_manager.options.mute:
                    if scenario.needs_clock_sync():
                        pipe_arguments["sink"] = "fakesink sync=true"
                    else:
                        pipe_arguments["sink"] = "'fakesink'"
                else:
                    pipe_arguments["sink"] = "auto%ssink" % self.media_type

                pipe = self._pipeline_template % pipe_arguments

                for src in srcs:
                    pipe += "%s ! _mixer. " % src

                self.add_test(GstValidateLaunchTest(fname,
                                                    self.test_manager.options,
                                                    self.test_manager.reporter,
                                                    pipe,
                                                    scenario=scenario)
                              )


class GstValidateLaunchTest(GstValidateTest):

    def __init__(self, classname, options, reporter, pipeline_desc,
                 timeout=DEFAULT_TIMEOUT, scenario=None,
                 media_descriptor=None, duration=0, hard_timeout=None,
                 extra_env_variables=None, expected_failures=None):

        extra_env_variables = extra_env_variables or {}

        if scenario:
            duration = scenario.get_duration()
        elif media_descriptor:
            duration = media_descriptor.get_duration() / GST_SECOND

        super(
            GstValidateLaunchTest, self).__init__(GST_VALIDATE_COMMAND, classname,
                                                  options, reporter,
                                                  duration=duration,
                                                  scenario=scenario,
                                                  timeout=timeout,
                                                  hard_timeout=hard_timeout,
                                                  media_descriptor=media_descriptor,
                                                  extra_env_variables=extra_env_variables,
                                                  expected_failures=expected_failures)

        self.pipeline_desc = pipeline_desc
        self.media_descriptor = media_descriptor

    def build_arguments(self):
        GstValidateTest.build_arguments(self)
        self.add_arguments(*shlex.split(self.pipeline_desc))
        if self.media_descriptor is not None and self.media_descriptor.get_path():
            self.add_arguments(
                "--set-media-info", self.media_descriptor.get_path())


class GstValidateMediaCheckTest(GstValidateTest):

    def __init__(self, classname, options, reporter, media_descriptor,
                 uri, minfo_path, timeout=DEFAULT_TIMEOUT,
                 extra_env_variables=None,
                 expected_failures=None):
        extra_env_variables = extra_env_variables or {}

        super(
            GstValidateMediaCheckTest, self).__init__(G_V_DISCOVERER_COMMAND, classname,
                                                      options, reporter,
                                                      timeout=timeout,
                                                      media_descriptor=media_descriptor,
                                                      extra_env_variables=extra_env_variables,
                                                      expected_failures=expected_failures)
        self._uri = uri
        self._media_info_path = minfo_path

    def build_arguments(self):
        Test.build_arguments(self)
        self.add_arguments(self._uri, "--expected-results",
                           self._media_info_path)


class GstValidateTranscodingTest(GstValidateTest, GstValidateEncodingTestInterface):
    scenarios_manager = ScenarioManager()

    def __init__(self, classname, options, reporter,
                 combination, uri, media_descriptor,
                 timeout=DEFAULT_TIMEOUT,
                 scenario=None,
                 extra_env_variables=None,
                 expected_failures=None):
        Loggable.__init__(self)

        extra_env_variables = extra_env_variables or {}

        file_dur = int(media_descriptor.get_duration()) / GST_SECOND
        if not media_descriptor.get_num_tracks("video"):
            self.debug("%s audio only file applying transcoding ratio."
                       "File 'duration' : %s" % (classname, file_dur))
            duration = file_dur / AUDIO_ONLY_FILE_TRANSCODING_RATIO
        else:
            duration = file_dur

        super(
            GstValidateTranscodingTest, self).__init__(GST_VALIDATE_TRANSCODING_COMMAND,
                                                       classname,
                                                       options,
                                                       reporter,
                                                       duration=duration,
                                                       timeout=timeout,
                                                       scenario=scenario,
                                                       media_descriptor=media_descriptor,
                                                       extra_env_variables=None,
                                                       expected_failures=expected_failures)
        extra_env_variables = extra_env_variables or {}

        GstValidateEncodingTestInterface.__init__(
            self, combination, media_descriptor)

        self.uri = uri

    def set_rendering_info(self):
        self.dest_file = os.path.join(self.options.dest,
                                      self.classname.replace(".transcode.", os.sep).
                                      replace(".", os.sep))
        mkdir(os.path.dirname(urllib.parse.urlsplit(self.dest_file).path))
        if urllib.parse.urlparse(self.dest_file).scheme == "":
            self.dest_file = path2url(self.dest_file)

        profile = self.get_profile()
        self.add_arguments("-o", profile)

    def build_arguments(self):
        GstValidateTest.build_arguments(self)
        self.set_rendering_info()
        self.add_arguments(self.uri, self.dest_file)

    def get_current_value(self):
        if self.scenario:
            sent_eos = self.sent_eos_position()
            if sent_eos is not None:
                t = time.time()
                if ((t - sent_eos)) > 30:
                    if self.media_descriptor.get_protocol() == Protocols.HLS:
                        self.set_result(Result.PASSED,
                                        """Got no EOS 30 seconds after sending EOS,
                                        in HLS known and tolerated issue:
                                        https://bugzilla.gnome.org/show_bug.cgi?id=723868""")
                        return Result.KNOWN_ERROR

                    self.set_result(
                        Result.FAILED, "Pipeline did not stop 30 Seconds after sending EOS")

                    return Result.FAILED

        size = self.get_current_size()
        if size is None:
            return self.get_current_position()

        return size

    def check_results(self):
        if self.result in [Result.FAILED, Result.TIMEOUT] or \
                self.process.returncode != 0:
            GstValidateTest.check_results(self)
            return

        res, msg = self.check_encoded_file()
        self.set_result(res, msg)


class GstValidateBaseRTSPTest:
    """ Interface for RTSP tests, requires implementing Test"""
    __used_ports = set()

    def __init__(self, local_uri):
        self._local_uri = local_uri
        self.rtsp_server = None
        self._unsetport_pipeline_desc = None
        self.optional = True

    @classmethod
    def __get_open_port(cls):
        while True:
            # hackish trick from
            # http://stackoverflow.com/questions/2838244/get-open-tcp-port-in-python?answertab=votes#tab-top
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.bind(("", 0))
            port = s.getsockname()[1]
            if port not in cls.__used_ports:
                cls.__used_ports.add(port)
                s.close()
                return port

            s.close()

    def launch_server(self):
        if self.options.redirect_logs == 'stdout':
            self.rtspserver_logs = sys.stdout
        elif self.options.redirect_logs == 'stderr':
            self.rtspserver_logs = sys.stderr

        self.server_port = self.__get_open_port()
        command = [GST_VALIDATE_RTSP_SERVER_COMMAND, self._local_uri, '--port', str(self.server_port)]

        if self.options.validate_gdb_server:
            command = self.use_gdb(command)
            self.rtspserver_logs = sys.stdout
        elif self.options.redirect_logs:
            self.rtspserver_logs = sys.stdout
        else:
            self.rtspserver_logs = open(self.logfile + '_rtspserver.log', 'w+')
            self.extra_logfiles.append(self.rtspserver_logs.name)

        server_env = os.environ.copy()

        self.rtsp_server = subprocess.Popen(command,
                                            stderr=self.rtspserver_logs,
                                            stdout=self.rtspserver_logs,
                                            env=server_env)
        while True:
            s = socket.socket()
            try:
                s.connect((("127.0.0.1", self.server_port)))
                break
            except ConnectionRefusedError:
                time.sleep(0.1)
                continue
            finally:
                s.close()

        if not self._unsetport_pipeline_desc:
            self._unsetport_pipeline_desc = self.pipeline_desc

        self.pipeline_desc = self._unsetport_pipeline_desc.replace(
            "<RTSPPORTNUMBER>", str(self.server_port))

        return ' '.join(command)

    def close_logfile(self):
        super().close_logfile()
        if not self.options.redirect_logs:
            self.rtspserver_logs.close()

    def process_update(self):
        res = super().process_update()
        if res:
            kill_subprocess(self, self.rtsp_server, DEFAULT_TIMEOUT)
            self.__used_ports.remove(self.server_port)

        return res


class GstValidateRTSPTest(GstValidateBaseRTSPTest, GstValidateLaunchTest):

    def __init__(self, classname, options, reporter, pipeline_desc,
                 local_uri, timeout=DEFAULT_TIMEOUT, scenario=None,
                 media_descriptor=None, rtsp2=False):
        GstValidateLaunchTest.__init__(self, classname, options, reporter,
                                       pipeline_desc, timeout, scenario,
                                       media_descriptor)
        GstValidateBaseRTSPTest.__init__(self, local_uri)
        self.rtsp2 = rtsp2

    def get_subproc_env(self):
        env = super().get_subproc_env()
        if self.rtsp2:
            env['GST_VALIDATE_SCENARIO'] = env.get('GST_VALIDATE_SCENARIO', '') + ':' + 'force_rtsp2'

        return env


class GstValidateRTSPMediaDesciptor(GstValidateMediaDescriptor):

    def __init__(self, xml_path):
        GstValidateMediaDescriptor.__init__(self, xml_path)

    def get_uri(self):
        return "rtsp://127.0.0.1:8554/test"

    def get_protocol(self):
        return Protocols.RTSP

    def prerrols(self):
        return False


class GstValidateTestManager(GstValidateBaseTestManager):

    name = "validate"

    # List of all classes to create testsuites
    GstValidateMediaCheckTestsGenerator = GstValidateMediaCheckTestsGenerator
    GstValidateTranscodingTestsGenerator = GstValidateTranscodingTestsGenerator
    GstValidatePipelineTestsGenerator = GstValidatePipelineTestsGenerator
    GstValidatePlaybinTestsGenerator = GstValidatePlaybinTestsGenerator
    GstValidateMixerTestsGenerator = GstValidateMixerTestsGenerator
    GstValidateLaunchTest = GstValidateLaunchTest
    GstValidateMediaCheckTest = GstValidateMediaCheckTest
    GstValidateTranscodingTest = GstValidateTranscodingTest

    def __init__(self):
        super(GstValidateTestManager, self).__init__()
        self._uris = []
        self._run_defaults = True
        self._is_populated = False
        self._default_generators_registered = False

    def init(self):
        for command, name in [
                (GST_VALIDATE_TRANSCODING_COMMAND, "gst-validate-1.0"),
                (GST_VALIDATE_COMMAND, "gst-validate-transcoding-1.0"),
                (G_V_DISCOVERER_COMMAND, "gst-validate-media-check-1.0")]:
            if not command:
                self.error("%s not found" % command)
                return False

        return True

    def add_options(self, parser):
        group = parser.add_argument_group("GstValidate tools specific options"
                                          " and behaviours",
                                          description="""When using --wanted-tests, all the scenarios can be used, even those which have
not been tested and explicitely activated if you set use --wanted-tests ALL""")
        group.add_argument("--validate-check-uri", dest="validate_uris",
                           action="append", help="defines the uris to run default tests on")
        group.add_argument("--validate-tools-path", dest="validate_tools_path",
                           action="append", help="defines the paths to look for GstValidate tools.")
        group.add_argument("--validate-gdb-server", dest="validate_gdb_server",
                           help="Run the server in GDB.")
        group.add_argument("--validate-disable-rtsp", dest="disable_rtsp",
                           help="Disable RTSP tests.")

    def print_valgrind_bugs(self):
        # Look for all the 'pending' bugs in our supp file
        bugs = []
        p = get_data_file('data', 'gstvalidate.supp')
        with open(p) as f:
            for l in f.readlines():
                l = l.strip()
                if l.startswith('# PENDING:'):
                    tmp = l.split(' ')
                    bugs.append(tmp[2])

        if bugs:
            msg = "Ignored valgrind bugs:\n"
            for b in bugs:
                msg += "  + %s\n" % b
            printc(msg, Colors.FAIL, True)

    def populate_testsuite(self):

        if self._is_populated is True:
            return

        if not self.options.config and not self.options.testsuites:
            if self._run_defaults:
                self.register_defaults()
            else:
                self.register_all()

        self._is_populated = True

    def list_tests(self):
        if self.tests:
            return self.tests

        if self._run_defaults:
            scenarios = [self.scenarios_manager.get_scenario(scenario_name)
                         for scenario_name in self.get_scenarios()]
        else:
            scenarios = self.scenarios_manager.get_scenario(None)
        uris = self._list_uris()

        for generator in self.get_generators():
            for test in generator.generate_tests(uris, scenarios):
                self.add_test(test)

        if not self.tests and not uris:
            printc(
                "No valid uris present in the path. Check if media files and info files exist", Colors.FAIL)

        return self.tests

    def _add_media(self, media_info, uri=None):
        self.debug("Checking %s", media_info)
        if isinstance(media_info, GstValidateMediaDescriptor):
            media_descriptor = media_info
            media_info = media_descriptor.get_path()
        else:
            media_descriptor = GstValidateMediaDescriptor(media_info)

        try:
            # Just testing that the vairous mandatory infos are present
            caps = media_descriptor.get_caps()
            if uri is None:
                uri = media_descriptor.get_uri()

            # Adjust local http uri
            if self.options.http_server_port != 8079 and \
               uri.startswith("http://127.0.0.1:8079/"):
                uri = uri.replace("http://127.0.0.1:8079/",
                                  "http://127.0.0.1:%r/" % self.options.http_server_port, 1)
            media_descriptor.set_protocol(urllib.parse.urlparse(uri).scheme)
            for caps2, prot in GST_VALIDATE_CAPS_TO_PROTOCOL:
                if caps2 == caps:
                    media_descriptor.set_protocol(prot)
                    break

            scenario_bname = media_descriptor.get_media_filepath()
            special_scenarios = self.scenarios_manager.find_special_scenarios(
                scenario_bname)
            self._uris.append((uri,
                               NamedDic({"path": media_info,
                                         "media_descriptor": media_descriptor}),
                               special_scenarios))
        except configparser.NoOptionError as e:
            self.debug("Exception: %s for %s", e, media_info)

    def _discover_file(self, uri, fpath):
        try:
            media_info = "%s.%s" % (
                fpath, GstValidateMediaDescriptor.MEDIA_INFO_EXT)
            args = G_V_DISCOVERER_COMMAND.split(" ")
            args.append(uri)
            if os.path.isfile(media_info) and not self.options.update_media_info:
                self._add_media(media_info, uri)
                return True
            elif fpath.endswith(GstValidateMediaDescriptor.STREAM_INFO_EXT):
                self._add_media(fpath)
                return True
            elif not self.options.generate_info and not self.options.update_media_info and not self.options.validate_uris:
                self.info(
                    "%s not present. Use --generate-media-info", media_info)
                return True
            elif self.options.update_media_info and not os.path.isfile(media_info):
                self.info(
                    "%s not present. Use --generate-media-info", media_info)
                return True

            include_frames = 0
            if self.options.update_media_info:
                include_frames = 2
            elif self.options.generate_info_full:
                include_frames = 1

            media_descriptor = GstValidateMediaDescriptor.new_from_uri(
                uri, True,
                include_frames)
            if media_descriptor:
                self._add_media(media_descriptor, uri)
            else:
                self.warning("Could not get any descriptor for %s" % uri)

            return True

        except subprocess.CalledProcessError as e:
            if self.options.generate_info:
                printc("Result: Failed", Colors.FAIL)
            else:
                self.error("Exception: %s", e)
            return False

    def _list_uris(self):
        if self._uris:
            return self._uris

        if self.options.validate_uris:
            for uri in self.options.validate_uris:
                self._discover_file(uri, uri)
            return self._uris

        if not self.args:
            if isinstance(self.options.paths, str):
                self.options.paths = [os.path.join(self.options.paths)]

            for path in self.options.paths:
                if os.path.isfile(path):
                    path = os.path.abspath(path)
                    self._discover_file(path2url(path), path)
                else:
                    for root, dirs, files in os.walk(path):
                        for f in files:
                            fpath = os.path.abspath(os.path.join(root, f))
                            if os.path.isdir(fpath) or \
                                    fpath.endswith(GstValidateMediaDescriptor.MEDIA_INFO_EXT) or\
                                    fpath.endswith(ScenarioManager.FILE_EXTENSION):
                                continue
                            else:
                                self._discover_file(path2url(fpath), fpath)

        self.debug("Uris found: %s", self._uris)

        return self._uris

    def needs_http_server(self):
        for test in self.list_tests():
            if self._is_test_wanted(test) and test.media_descriptor is not None:
                protocol = test.media_descriptor.get_protocol()
                uri = test.media_descriptor.get_uri()

                if protocol in [Protocols.HTTP, Protocols.HLS, Protocols.DASH] and \
                        ("127.0.0.1:%s" % (self.options.http_server_port) in uri or
                         "127.0.0.1:8079" in uri):
                    return True
        return False

    def set_settings(self, options, args, reporter):
        if options.wanted_tests:
            for i in range(len(options.wanted_tests)):
                if "ALL" in options.wanted_tests[i]:
                    self._run_defaults = False
                    options.wanted_tests[
                        i] = options.wanted_tests[i].replace("ALL", "")
        try:
            options.wanted_tests.remove("")
        except ValueError:
            pass

        if options.validate_uris:
            self.check_testslist = False

        super(GstValidateTestManager, self).set_settings(
            options, args, reporter)

    def register_defaults(self):
        """
        Registers the defaults:
            * Scenarios to be used
            * Encoding formats to be used
            * Blacklisted tests
            * Test generators
        """
        self.register_default_scenarios()
        self.register_default_encoding_formats()
        self.register_default_blacklist()
        self.register_default_test_generators()

    def register_default_scenarios(self):
        """
        Registers default test scenarios
        """
        if self.options.long_limit != 0:
            self.add_scenarios([
                "play_15s",
                "reverse_playback",
                "fast_forward",
                "seek_forward",
                "seek_backward",
                "seek_with_stop",
                "switch_audio_track",
                "switch_audio_track_while_paused",
                "switch_subtitle_track",
                "switch_subtitle_track_while_paused",
                "disable_subtitle_track_while_paused",
                "change_state_intensive",
                "scrub_forward_seeking"])
        else:
            self.add_scenarios([
                "play_15s",
                "reverse_playback",
                "fast_forward",
                "seek_forward",
                "seek_backward",
                "seek_with_stop",
                "switch_audio_track",
                "switch_audio_track_while_paused",
                "switch_subtitle_track",
                "switch_subtitle_track_while_paused",
                "disable_subtitle_track_while_paused",
                "change_state_intensive",
                "scrub_forward_seeking"])

    def register_default_encoding_formats(self):
        """
        Registers default encoding formats
        """
        self.add_encoding_formats([
            MediaFormatCombination("ogg", "vorbis", "theora"),
            MediaFormatCombination("webm", "vorbis", "vp8"),
            MediaFormatCombination("mp4", "mp3", "h264"),
            MediaFormatCombination("mkv", "vorbis", "h264"),
        ])

    def register_default_blacklist(self):
        self.set_default_blacklist([
            # hls known issues
            # ("hls.playback.seek_with_stop.*",
            #  "https://bugzilla.gnome.org/show_bug.cgi?id=753689"),

            # dash known issues
            ("dash.media_check.*",
             "Caps are different depending on selected bitrates, etc"),

            # Matroska/WEBM known issues:
            ("*.reverse_playback.*webm$",
             "https://bugzilla.gnome.org/show_bug.cgi?id=679250"),
            ("*.reverse_playback.*mkv$",
             "https://bugzilla.gnome.org/show_bug.cgi?id=679250"),
            ("http.playback.seek_with_stop.*webm",
             "matroskademux.gst_matroska_demux_handle_seek_push: Seek end-time not supported in streaming mode"),
            ("http.playback.seek_with_stop.*mkv",
             "matroskademux.gst_matroska_demux_handle_seek_push: Seek end-time not supported in streaming mode"),

            # MPEG TS known issues:
            ('(?i)*playback.reverse_playback.*(?:_|.)(?:|m)ts$',
             "https://bugzilla.gnome.org/show_bug.cgi?id=702595"),

            # Fragmented MP4 disabled tests:
            ('*.playback..*seek.*.fragmented_nonseekable_sink_mp4',
             "Seeking on fragmented files without indexes isn't implemented"),
            ('*.playback.reverse_playback.fragmented_nonseekable_sink_mp4',
             "Seeking on fragmented files without indexes isn't implemented"),

            # HTTP known issues:
            ("http.*scrub_forward_seeking.*",
             "This is not stable enough for now."),
            ("http.playback.change_state_intensive.raw_video_mov",
             "This is not stable enough for now. (flow return from pad push doesn't match expected value)"),

            # MXF known issues"
            ("*reverse_playback.*mxf",
             "Reverse playback is not handled in MXF"),
            ("file\.transcode.*mxf",
             "FIXME: Transcoding and mixing tests need to be tested"),

            # WMV known issues"
            ("*reverse_playback.*wmv",
             "Reverse playback is not handled in wmv"),
            (".*reverse_playback.*asf",
             "Reverse playback is not handled in asf"),

            # ogg known issues
            ("http.playback.seek.*vorbis_theora_1_ogg",
             "https://bugzilla.gnome.org/show_bug.cgi?id=769545"),
            # RTSP known issues
            ('rtsp.*playback.reverse.*',
             'https://bugzilla.gnome.org/show_bug.cgi?id=626811'),
            ('rtsp.*playback.seek_with_stop.*',
             'https://bugzilla.gnome.org/show_bug.cgi?id=784298'),
            ('rtsp.*playback.fast_*',
             'https://bugzilla.gnome.org/show_bug.cgi?id=754575'),
        ])

    def register_default_test_generators(self):
        """
        Registers default test generators
        """
        if self._default_generators_registered:
            return

        self.add_generators([GstValidatePlaybinTestsGenerator(self),
                             GstValidateMediaCheckTestsGenerator(self),
                             GstValidateTranscodingTestsGenerator(self)])
        self._default_generators_registered = True
