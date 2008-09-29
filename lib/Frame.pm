#
# Functions to create the tabs and frame of the main browsing mode
#
# Brian Carrier [carrier@sleuthkit.org]
# Copyright (c) 2001-2005 by Brian Carrier.  All rights reserved
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

package Frame;

$Frame::IMG_FRAME = 0;
$Frame::IMG_TABS  = 1;
$Frame::IMG_BLANK = 2;

sub main {
    Args::check_vol('vol');

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $Frame::IMG_FRAME
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    if ($view == $Frame::IMG_FRAME) {
        vol_browse_frame();
    }
    elsif ($view == $Frame::IMG_TABS) {
        vol_browse_tabs();
    }
    elsif ($view == $Frame::IMG_BLANK) {
        vol_browse_blank();
    }
    else {
        Print::print_check_err("Invalid Frame View");
    }

    return 0;
}

# create the frame for the tabs on top and the generic message on the bottom
sub vol_browse_frame {
    Print::print_html_header_frameset(
        "$Args::args{'case'}:$Args::args{'host'}:$Args::args{'vol'}");

    my $submod = $::MOD_FRAME;
    $submod = Args::get_submod() if (exists $Args::args{'submod'});

    # Print the rest of the frames
    my $str  = "";
    my $view = "";

    if ($submod == $::MOD_FILE) {
        $str .= "&meta=$Args::args{'meta'}"   if (exists $Args::args{'meta'});
        $str .= "&dir=$Args::args{'dir'}"     if (exists $Args::args{'dir'});
        $str .= "&sort=$Args::args{'sort'}"   if (exists $Args::args{'sort'});
        $str .= "&dmode=$Args::args{'dmode'}" if (exists $Args::args{'dmode'});
    }
    elsif ($submod == $::MOD_DATA) {
        $str .= "&block=$Args::args{'block'}" if (exists $Args::args{'block'});
        $str .= "&len=$Args::args{'len'}"     if (exists $Args::args{'len'});
    }
    elsif ($submod == $::MOD_META) {
        $str .= "&meta=$Args::args{'meta'}" if (exists $Args::args{'meta'});
    }
    elsif ($submod == $::MOD_FRAME) {
        $view = "&view=$Frame::IMG_BLANK";
    }

    print <<EOF;

<frameset rows=\"40,*\">
  <frame src=\"$::PROGNAME?mod=$::MOD_FRAME&view=$Frame::IMG_TABS&$Args::baseargs&submod=$submod\">

  <frame src=\"$::PROGNAME?mod=$submod$view&$Args::baseargs$str\">
</frameset>

<NOFRAMES>
  <center>
    Autopsy requires a browser that supports frames.
  </center>
</NOFRAMES>

EOF

    Print::print_html_footer_frameset();
    return 0;
}

# Display a message when the image is opened (below the tabs)
sub vol_browse_blank {
    Print::print_html_header("Main Message");

    print <<EOF;

<center>
  <br><br><br><br><br><br><br>
  To start analyzing this volume, choose an analysis mode from the tabs above.
</center>

EOF
    Print::print_html_footer();
    return 0;
}

sub vol_browse_tabs {
    Args::check_submod();
    Print::print_html_header_tabs("Mode Tabs");

    my $submod = Args::get_submod();
    my $vol    = Args::get_vol('vol');

    my $special = 0;
    $special = 1
      unless (($Caseman::vol2cat{$vol} eq "part")
        && ($Fs::is_fs{$Caseman::vol2ftype{$vol}} == 1));

    #      if ( ($Caseman::vol2ftype{$vol} eq "strings")
    #        || ($Caseman::vol2ftype{$vol} eq "blkls")
    #        || ($Caseman::vol2ftype{$vol} eq "swap")
    #        || ($Caseman::vol2ftype{$vol} eq "raw"));

    print "<center><table width=\"800\" border=\"0\" cellspacing=\"0\" "
      . "cellpadding=\"0\"><tr>\n";

    # Files
    print "<td align=\"center\" width=116>";
    if ($special == 0) {

        print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_FILE&$Args::baseargs\""
          . "target=\"_top\">";

        # Current
        if ($submod == $::MOD_FILE) {
            print "<img border=0 "
              . "src=\"pict/main_t_fil_cur.jpg\" "
              . "width=116 height=38 "
              . "alt=\"File Analysis (Current Mode)\"></a>";
        }

        # Link
        else {
            print "<img border=0 "
              . "src=\"pict/main_t_fil_link.jpg\" "
              . "width=116 height=38 "
              . "alt=\"File Analysis\"></a>";
        }
    }

    # non-link
    else {
        print "<img border=0 "
          . "src=\"pict/main_t_fil_org.jpg\" "
          . "width=116 height=38 "
          . "alt=\"File Analysis (not available)\">";
    }

    # Search
    print "</td>\n" . "<td align=\"center\" width=116>";

    print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_KWSRCH&$Args::baseargs\""
      . " target=\"_top\">";

    if ($submod == $::MOD_KWSRCH) {
        print "<img border=0 "
          . "src=\"pict/main_t_srch_cur.jpg\" "
          . "width=116 height=38 "
          . "alt=\"Keyword Search Mode (Current Mode)\"></a>";
    }
    else {
        print "<img border=0 "
          . "src=\"pict/main_t_srch_link.jpg\" "
          . "width=116 height=38 "
          . "alt=\"Keyword Search Mode\"></a>";
    }

    # File Type
    print "</td>\n" . "<td align=\"center\" width=116>";

    if (($special == 0) && ($::LIVE == 0)) {

        print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_APPSORT&$Args::baseargs\""
          . " target=\"_top\">";

        # Current
        if ($submod == $::MOD_APPSORT) {
            print "<img border=0 "
              . "src=\"pict/main_t_ftype_cur.jpg\" "
              . "width=116 height=38 "
              . "alt=\"File Type (Current Mode)\"></a>";
        }
        else {
            print "<img border=0 "
              . "src=\"pict/main_t_ftype_link.jpg\" "
              . "width=116 height=38 "
              . "alt=\"File Type\"></a>";
        }
    }
    else {
        print "<img border=0 "
          . "src=\"pict/main_t_ftype_org.jpg\" "
          . "width=116 height=38 "
          . "alt=\"File Type (not available)\">";
    }

    # Image Details
    print "</td>\n" . "<td align=\"center\" width=116>";

    if (($special == 0) || ($Caseman::vol2cat{$vol} eq "disk")) {

        print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_FS&$Args::baseargs\""
          . " target=\"_top\">";

        if ($submod == $::MOD_FS) {
            print "<img border=0 "
              . "src=\"pict/main_t_img_cur.jpg\" "
              . "width=116 height=38 "
              . "alt=\"Image Details Mode (Current Mode)\"></a>";
        }
        else {
            print "<img border=0 "
              . "src=\"pict/main_t_img_link.jpg\" "
              . "width=116 height=38 "
              . "alt=\"Image Details Mode\"></a>";
        }
    }
    else {
        print "<img border=0 "
          . "src=\"pict/main_t_img_org.jpg\" "
          . "width=116 height=38 "
          . "alt=\"Image Details Mode (not available)\">";
    }

    # Meta Data
    print "</td>\n" . "<td align=\"center\" width=116>";

    if ($special == 0) {
        print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_META&$Args::baseargs\""
          . " target=\"_top\">";

        if ($submod == $::MOD_META) {
            print "<img border=0 "
              . "src=\"pict/main_t_met_cur.jpg\" "
              . "width=116 height=38 "
              . "alt=\"Meta Data Mode (Current Mode)\"></a>";
        }
        else {
            print "<img border=0 "
              . "src=\"pict/main_t_met_link.jpg\" "
              . "width=116 height=38 "
              . "alt=\"Meta Data Mode\"></a>";
        }
    }
    else {
        print "<img border=0 "
          . "src=\"pict/main_t_met_org.jpg\" "
          . "width=116 height=38 "
          . "alt=\"Meta Data Mode (not available)\">";
    }

    # Data Units
    print "</td>\n" . "<td align=\"center\" width=116>";

    print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_DATA&$Args::baseargs\""
      . " target=\"_top\">";

    # Current
    if ($submod == $::MOD_DATA) {
        print "<img border=0 "
          . "src=\"pict/main_t_dat_cur.jpg\" "
          . "width=116 height=38 "
          . "alt=\"Data Units Mode (Current Mode)\"></a>";
    }

    # Link
    else {
        print "<img border=0 "
          . "src=\"pict/main_t_dat_link.jpg\" "
          . "width=116 height=38 "
          . "alt=\"Data Units Mode\"></a>";
    }

    # Help - set to current mode
    print "</td>\n"
      . "<td align=\"center\" width=52>"
      . "<a href=\"$::HELP_URL\""
      . " target=\"_blank\">"
      . "<img border=0 "
      . "src=\"pict/tab_help.jpg\" "
      . "width=52 "
      . "alt=\"Help\">"
      . "</a></td>\n";

    # Close
    print "<td align=\"center\" width=52>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
      . "view=$Caseman::VOL_OPEN&$Args::baseargs_novol\" target=\"_top\">"
      . "<img border=0 src=\"pict/tab_close.jpg\" width=52 "
      . "alt=\"Close Image\"></a></td>\n";

    print "</tr></table>\n";

    Print::print_html_footer_tabs();
    return 0;
}
