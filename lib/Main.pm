#
# Main.pm
# Autopsy Forensic Browser
#
# This file requires The Sleuth Kit
#    www.sleuthkit.org
#
# Brian Carrier [carrier@sleuthkit.org]
# Copyright (c) 2001-2005 by Brian Carrier.  All rights reserved
#
#
# This file is part of the Autopsy Forensic Browser (Autopsy)
#
# Autopsy is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# Autopsy is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Autopsy; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
#
#
# THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
# WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR ANY PARTICULAR PURPOSE.
# IN NO EVENT SHALL THE AUTHORS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, LOSS OF USE, DATA, OR PROFITS OR
# BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
# OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
# ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

use lib './';
use strict;

use Appsort;
use Appview;
use Args;
use Caseman;
use Data;
use Exec;
use File;
use Filesystem;
use Frame;
use Fs;
use Hash;
use Kwsrch;
use Meta;
use Notes;
use Print;
use Timeline;
use Vs;

require 'conf.pl';
require 'define.pl';

# Get rid of insecure settings
$ENV{PATH} = "";
delete @ENV{'IFS', 'CDPATH', 'ENV', 'BASH_ENV'};

# Formats for regular expressions
# Year.Mon.Day Hr:Min:Sec (TZ)
$::REG_DAY       = '\d\d\d\d\-\d\d\-\d\d';
$::REG_TIME      = '\d\d:\d\d:\d\d';
$::REG_ZONE_ARGS = '[\w\+\-\/\_]+';
$::REG_ZONE2     = '\([\w\+\- ]*\)';
$::REG_DATE  = "$::REG_DAY" . '\s+' . "$::REG_TIME" . '\s+' . "$::REG_ZONE2";
$::REG_FTYPE = '[\w\-]+';
$::REG_SKEW  = '[\+\-]?\d+';
$::REG_MTYPE = '[\?bcdflprsvw-]';    # Type according to meta data

$::REG_FILE     = '[\w\-\_\.]+';
$::REG_CASE     = $::REG_FILE;
$::REG_HOST     = '[\w\-\_\.]+';
$::REG_INVESTIG = '[\w]+';

$::REG_IMG           = "$::REG_FILE" . '/' . "$::REG_FILE";
$::REG_IMG_PATH      = '/[\w\-\_\.\/]+';
$::REG_IMG_PATH_WILD = '/[\w\-\_\.\/]+\*?';
$::REG_IMG_CONFIG    = '[\w\-\_\.\/ ]+';
$::REG_FNAME         = $::REG_FILE;
$::REG_MNT           = '[\w\-\_\.\/\:\\\\]+';
$::REG_SEQ_FILE      = '[\w\s\-\_\.\/\:\\\\]+';
$::REG_HASHDB        = '[\w\-\_\.\,\/]+';
$::REG_IMGTYPE       = '[\w\,]+';
$::REG_INAME         = '[\w]+';
$::REG_VNAME         = '[\w]+';

$::REG_META = '[\d-]+';
$::REG_MD5  = '[0-9a-fA-F]{32,32}';

$::HELP_URL = "help/index.html";

# host_dir and case_dir will end with a '/'
$::host_dir = "";
$::case_dir = "";

################## NEW STUFF ##########################
# MODULES

# If the values of these are changed, or if new modules are added,
# Then the below pseudo-binary sort algorithm must be changed as well
$::MOD_CASEMAN = 0;
$::MOD_FRAME   = 1;
$::MOD_FILE    = 2;
$::MOD_META    = 3;
$::MOD_KWSRCH  = 4;
$::MOD_DATA    = 5;
$::MOD_TL      = 6;
$::MOD_FS      = 7;
$::MOD_APPSORT = 8;
$::MOD_NOTES   = 9;
$::MOD_HASH    = 10;
$::MOD_APPVIEW = 11;

# Main Menu
#
# Display the title page
sub welcome {
    Print::print_html_header_javascript("Autopsy Forensic Browser");

    print "<center>\n";

    # This problem has not been seen with the 1 second delay
    if ((0) && ($] >= 5.008)) {
        print
"<p><font color=\"red\">Warning: You are using Perl v5.8.</font><br>\n"
          . "  Some buffer problems have been reported with Autopsy and Perl 5.8 "
          . "where output is not shown.<br>\n"
          . "Perl 5.6 should be used if available.\n"
          . "If data is missing, reload the page<br><hr>\n";
    }

    print <<EOF;
<table cellspacing=0 cellpadding=2 width=600 height=350 border=0>

<tr>
  <td colspan=\"3\" align=\"center\" valign=\"MIDDLE\">
    <b>Autopsy Forensic Browser  $::VER</b>
  </td>
</tr>
<tr>
  <td colspan=\"3\">&nbsp;</td>
</tr>
<tr>
  <td colspan=\"3\" align=\"center\" valign=\"MIDDLE\">
    <a href=\"./about\">
      <img src=\"pict/logo.jpg\" border=0 alt="Logo">
    </a>
  </td>
</tr>
<tr>
  <td colspan=\"3\">&nbsp;</td>
</tr>
<tr>
  <td colspan=\"3\" align=\"center\" valign=\"MIDDLE\">
    <a href="http://www.sleuthkit.org/autopsy/">
      <tt>http://www.sleuthkit.org/autopsy/</tt>
    </a>
  </td>
</tr>
<tr><td colspan=3>&nbsp;</td></tr>
<tr>
  <td align=center width=200 valign=\"MIDDLE\">
    <a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&view=$Caseman::CASE_OPEN\">
      <img src=\"pict/menu_b_copen.jpg\" alt=\"Open Case\" width=176 height=20 border=0>
    </a>
  </td>
  <td align=center width=200 valign=\"MIDDLE\">
    <a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&view=$Caseman::CASE_NEW\">
      <img src=\"pict/menu_b_cnew.jpg\" alt=\"New Case\" width=176 height=20 border=0>
    </a>
  </td>
  <td align=center width=200 valign=\"MIDDLE\">
    <a href=\"$::HELP_URL\" target=\"_blank\">
      <img src=\"pict/menu_b_help.jpg\" alt=\"Help\" width=167 height=20 border=0>
    </a>
  </td>
</tr>
</table>

EOF

    Print::print_html_footer_javascript();
    return 0;
}

sub get_tskver {
    local *OUT;
    Exec::exec_pipe(*OUT, "'$::TSKDIR/fls' -V");

    my $ver = Exec::read_pipe_line(*OUT);
    $ver = $1 if ($ver =~ /^The Sleuth Kit ver (.*)$/);
    close(OUT);

    return $ver;
}

# This function is called by the code in the 'autopy' file.
# This will check for the basic module arguments and then host
# and case accordingly.  The main function of each of the modules
# is called from here.  Each of the modules will have to take care
# of the detailed argument checking.

sub main {

    # Parse arguments
    my $lcl_args = shift;
    Args::parse_args($lcl_args);

    # When autopsy is first run, no mod or arguments are given.
    unless (exists $Args::args{'mod'}) {

        # if we are not in live analysis mode, display the usual screen
        if ($::LIVE == 0) {
            return welcome();
        }
        else {

            # If we are in live analysis mode, open up the window to select
            # and image and supply basic host and case values.
            $Args::args{'mod'}  = $Args::enc_args{'mod'}  = $::MOD_CASEMAN;
            $Args::args{'view'} = $Args::enc_args{'view'} = $Caseman::VOL_OPEN;
            $Args::args{'case'} = $Args::enc_args{'case'} = "live";
            $Args::args{'host'} = $Args::enc_args{'host'} = "local";
            $Args::args{'inv'}  = $Args::enc_args{'inv'}  = "unknown";
        }
    }

    Args::check_mod();
    my $module = Args::get_mod();

    Args::make_baseargs();

    # For live analysis, we need to change the regular expression
    # for images because it can be a full path (to /dev/xxxxxxx)
    $::REG_IMG = '/[\w\-\_\.\/]+'
      if ($::LIVE == 1);

    # The Case Management module is handled seperately because
    # it may not have the host and case values
    if ($module == $::MOD_CASEMAN) {
        return Caseman::main();
    }

    # Check the minimum arguments
    Args::check_case();
    Args::check_host();

    # Set the case and host variables
    if ($::LIVE == 0) {
        $::case_dir = "$::LOCKDIR/" . Args::get_case() . "/";
        $::case_dir =~ s/\/\//\//g;
        $::host_dir = "$::case_dir" . Args::get_host() . "/";
        $::host_dir =~ s/\/\//\//g;
    }
    else {
        $::host_dir = "";
        $::case_dir = "";
    }
    Caseman::read_host_config();

    # This is a partial binary sort method to reduce the number of checks

    # 0 < mod < 6
    if ($module < $::MOD_TL) {

        # 0 < mod < 4
        if ($module < $::MOD_KWSRCH) {

            # mod == 1
            if ($module == $::MOD_FRAME) {
                return Frame::main();
            }

            # mod == 2
            elsif ($module == $::MOD_FILE) {
                return File::main();
            }

            # mod == 3
            elsif ($module == $::MOD_META) {
                return Meta::main();
            }
        }

        # 4 <= mod < 6
        else {

            # mod == 4
            if ($module == $::MOD_KWSRCH) {
                return Kwsrch::main();
            }

            # mod == 5
            elsif ($module == $::MOD_DATA) {
                return Data::main();
            }
        }
    }

    # 6 <= mod
    else {

        # 6 <= mod < 9
        if ($module < $::MOD_NOTES) {

            # mod == 6
            if ($module == $::MOD_TL) {
                return Timeline::main();
            }

            # mod == 7
            elsif ($module == $::MOD_FS) {
                return Filesystem::main();
            }

            # mod == 8
            elsif ($module == $::MOD_APPSORT) {
                return Appsort::main();
            }
        }

        # 9 <= mod
        else {

            # mod == 9
            if ($module == $::MOD_NOTES) {
                return Notes::main();
            }

            # mod == 10
            elsif ($module == $::MOD_HASH) {
                return Hash::main();
            }

            # mod == 11
            elsif ($module == $::MOD_APPVIEW) {
                return Appview::main();
            }

            # New modules can be added here

        }
    }
    Print::print_check_err("Invalid Module");
}

1;
