#
# Sort files based on their application type (content)
#
# Brian Carrier [carrier@sleuthkit.org]
# Copyright (c) 2001-2008 by Brian Carrier.  All rights reserved
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

package Appsort;

$Appsort::FRAME = 1;
$Appsort::MENU  = 2;
$Appsort::ENTER = 3;
$Appsort::RUN   = 4;
$Appsort::VIEW  = 5;
$Appsort::BLANK = 6;

sub main {

    if ($::LIVE == 1) {
        Print::print_html_header("Unsupported for Live Analysis");
        print
"<center><h2>This feature is not available during a live analysis</h2></center>";
        Print::print_html_footer();
        return 0;
    }

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $Appsort::FRAME
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    if ($view == $Appsort::BLANK) {
        return blank();
    }

    # Check Basic Args
    Args::check_vol('vol');

    # These windows don't need the meta data address
    if ($view == $Appsort::FRAME) {
        return frame();
    }
    elsif ($view == $Appsort::ENTER) {
        return enter();
    }
    elsif ($view == $Appsort::MENU) {
        return menu();
    }
    elsif ($view == $Appsort::RUN) {
        return run();
    }
    elsif ($view == $Appsort::VIEW) {
        return view();
    }
    else {
        Print::print_check_err("Invalid Application Sorting View");
    }
}

sub get_sorter_dir {
    if ($Args::args{'vol'} =~ /^($::REG_VNAME)$/) {
        return "$::host_dir" . "$::DATADIR/sorter-$1/";
    }
    Print::print_err("Invalid Sorter Directory");
}

sub get_sorter_graphics_dir {
    if ($Args::args{'vol'} =~ /^($::REG_VNAME)$/) {
        return "$::host_dir" . "$::DATADIR/sorter-graphics-$1/";
    }
    Print::print_err("Invalid Sorter Graphics Directory");
}

# sorter frameset
sub frame {
    Print::print_html_header_frameset("Sorter on $Args::args{'vol'}");

    print "<frameset cols=\"20%,80%\">\n";

    # Block List
    print "<frame src=\"$::PROGNAME?mod=$::MOD_APPSORT&view=$Appsort::MENU&"
      . "$Args::baseargs\">\n";

    # Blank
    print "<frame src=\"$::PROGNAME?mod=$::MOD_APPSORT&view=$Appsort::BLANK&"
      . "$Args::baseargs\" name=\"content\">\n"
      . "</frameset>\n";

    Print::print_html_footer_frameset();
    return 0;
}

# The left-hand frame for running sorter
sub menu {
    Print::print_html_header("sorter menu");

    print "<p><a href=\"$::PROGNAME?mod=$::MOD_APPSORT&view=$Appsort::ENTER&"
      . "$Args::baseargs\" "
      . "target=\"content\">Sort Files by Type</a>";

    print "<p><a href=\"$::PROGNAME?mod=$::MOD_APPSORT&view=$Appsort::VIEW&"
      . "$Args::baseargs\" "
      . "target=\"content\">View Sorted Files</a>";

    Print::print_html_footer();
    return 0;
}

# Get the data and print the form so that sorter can be run
sub enter {
    Print::print_html_header("sorter - enter data to create");

    print "<center>"
      . "<h3>File Type Sortings</h3></center><br>"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_APPSORT\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Appsort::RUN\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$Args::args{'vol'}\">\n"
      . Args::make_hidden();

    print <<EOF1;
<p>The <b>sorter</b> tool will process an image and organize the
files based on their file type.  The files are organized into categories
that are defined in configuration files.  The categories will be saved
in the <tt>$::DATADIR</tt> directory.  
<hr>
EOF1

    my $sort_dir = get_sorter_dir();
    if (-d "$sort_dir") {
        print "WARNING: This will overwrite any existing data in:<br>"
          . "&nbsp;&nbsp;&nbsp;&nbsp;<tt>$sort_dir</tt><br>\n";
    }

    my $tab = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";

    print <<EOF2;

<p>
<input type=\"checkbox\" name=\"sorter_cat\" value=\"1\" CHECKED>
Sort files into categories by type

  <p>$tab
  <input type=\"checkbox\" name=\"sorter_unk\" value=\"1\">
  Do not save data about <tt>unknown</tt> file types

  <p>$tab
  <input type=\"checkbox\" name=\"sorter_save\" value=\"1\">
  Save a copy of files in category directory (may require lots of disk space)

  <p>$tab
  <input type=\"checkbox\" name=\"sorter_img\" value=\"1\">
  Save ONLY graphic images and make thumbnails <br>
  $tab (may require lots of disk space and will save to a different directory than sorting all file types)

<p>
<input type=\"checkbox\" name=\"sorter_ext\" value=\"1\" CHECKED>
Extension and File Type Validation

EOF2

    if (($::NSRLDB ne "") && (-e "$::NSRLDB")) {

        # NSRL
        print
"<p><input type=\"checkbox\" name=\"sorter_nsrl\" value=\"1\" CHECKED>"
          . "Exclude files in the <b>NIST NSRL</b>\n";
    }

    if (($Caseman::alert_db ne "") && (-e "$Caseman::alert_db")) {
        print
"<p><input type=\"checkbox\" name=\"sorter_alert\" value=\"1\" CHECKED>"
          . "Alert files that are found in the <b>Alert Hash Database</b>\n";
    }

    if (($Caseman::exclude_db ne "") && (-e "$Caseman::exclude_db")) {
        print
"<p><input type=\"checkbox\" name=\"sorter_exclude\" value=\"1\" CHECKED>"
          . "Ignore files that are found in the <b>Exclude Hash Database</b>\n";
    }

    print "<p><input type=\"image\" src=\"pict/but_ok.jpg\" "
      . "width=43 height=20 alt=\"Ok\" border=\"0\">\n</form>\n";

    Print::print_html_footer();
    return;
}

# Run sorter on the image
sub run {
    Print::print_html_header("sorter - create");

    my $sort_args = "";
    my $ext       = 0;
    my $cat       = 0;

    my $vol = Args::get_vol('vol');
    my $mnt = $Caseman::vol2mnt{$vol};

    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    Print::log_host_inv("Running 'sorter' on ($Caseman::vol2sname{$vol}");

    $ext = 1
      if ( (exists $Args::args{'sorter_ext'})
        && ($Args::args{'sorter_ext'} == 1));
    $cat = 1
      if ( (exists $Args::args{'sorter_cat'})
        && ($Args::args{'sorter_cat'} == 1));

    if (($cat == 0) && ($ext == 0)) {
        print "At least one action must be selected\n"
          . "<p><a href=\"$::PROGNAME?mod=$::MOD_APPSORT&"
          . "view=$Appsort::ENTER&$Args::baseargs\">"
          . "<img border=0 src=\"pict/but_ok.jpg\" alt=\"Ok\" "
          . "width=43 height=20></a>\n";

        return;
    }

    # If both actions are wanted then no flags are needed
    $sort_args .= "-e " if (($ext == 1) && ($cat == 0));
    $sort_args .= "-E " if (($ext == 0) && ($cat == 1));

    my $sort_dir = get_sorter_dir();

    if ($cat == 1) {
        if (   (exists $Args::args{'sorter_img'})
            && ($Args::args{'sorter_img'} == 1))
        {
            my $config = "$::TSKDIR/../share/tsk3/sorter/images.sort";

            Print::print_err("images configuration file not found ($config)")
              unless (-e "$config");

            $sort_args .= "-C \'$config\' -s -U ";

            $sort_dir = get_sorter_graphics_dir();

        }
        else {
            $sort_args .= "-s "
              if ( (exists $Args::args{'sorter_save'})
                && ($Args::args{'sorter_save'} == 1));

            $sort_args .= "-U "
              if ( (exists $Args::args{'sorter_unk'})
                && ($Args::args{'sorter_unk'} == 1));
        }
    }

    if ($::NSRLDB ne "") {
        $sort_args .= "-n \'$::NSRLDB\' "
          if ( (exists $Args::args{'sorter_nsrl'})
            && ($Args::args{'sorter_nsrl'} == 1));
    }

    if ($Caseman::alert_db ne "") {
        $sort_args .= "-a \'$Caseman::alert_db\' "
          if ( (exists $Args::args{'sorter_alert'})
            && ($Args::args{'sorter_alert'} == 1));
    }

    if ($Caseman::exclude_db ne "") {
        $sort_args .= "-x \'$Caseman::exclude_db\' "
          if ( (exists $Args::args{'sorter_exclude'})
            && ($Args::args{'sorter_exclude'} == 1));
    }

    unless (-d "$sort_dir") {
        unless (mkdir "$sort_dir", $::MKDIR_MASK) {
            Print::print_err("Error making $sort_dir");
        }
    }
    if (-e "$sort_dir/index.html") {
        unlink("$sort_dir/index.html");
    }

    my $exec =
"-h -m '$mnt' -d '$sort_dir' -o $offset -i $imgtype -f $ftype $sort_args $img";

    # print "Executing: <tt>sorter $exec</tt><p>\n";

    # Execute Sorter
    my $hit_cnt = 0;
    $SIG{ALRM} = sub {
        if (($hit_cnt++ % 5) == 0) {
            print "+";
        }
        else {
            print "-";
        }
        alarm(5);
    };
    alarm(5);

    local *OUT;
    Exec::exec_pipe(*OUT, "LANG=C LC_ALL=C '$::TSKDIR/sorter' $exec");
    alarm(0);
    $SIG{ALRM} = 'DEFAULT';

    while ($_ = Exec::read_pipe_line(*OUT)) {
        print "$_<br>\n";
        $hit_cnt = 0;
    }

    close(OUT);

    if (-e "$sort_dir/index.html") {
        print "<p>Output can be found by viewing:<br>"
          . "&nbsp;&nbsp;<tt>$sort_dir/index.html</tt><p>\n";

        # Print the index.html file from the output
        print "<hr><center><h3>Results Summary</h3></center>\n";
        open INDEX, "<$sort_dir/index.html"
          or die "Can't open sorter index file ($sort_dir/index.html)";

        while (<INDEX>) {
            next if ((/^<HTML><HEAD><TITLE>/i)
                || (/^<BODY><center><H2>/i));

            # Extract out the symlinks to the categories
            if (/^\s*<li><a href="\.\/[\w\.]+">([\w\s]+)<\/a> \((\d+)\)\s*$/i) {
                print "<LI>$1 ($2)\n";
            }

            # Skip the link on the thumbnails link
            elsif (/^\s*\(<a href=[\"\.\/\w]+>thumbnails<\/A>\)\s*$/) {
                print "(thumbnails)\n";
            }
            else {
                print "$_";
            }
        }
        close(INDEX);
    }

    Print::print_html_footer();
    return;
}

# View Page
sub view {
    Print::print_html_header("");
    print "<center><h3>File Type Sorting</h3>\n"
      . "Autopsy does not currently support viewing the sorted files.<br>\n"
      . "After sorting, you can view the results by opening the following file:<p>\n";
    print "<tt>" . get_sorter_dir() . "index.html</tt>";

    Print::print_html_footer();
    return 0;
}

# Blank Page
sub blank {
    Print::print_html_header("");
    print "<center><h3>File Type Sorting</h3>\n"
      . "In this mode, Autopsy will examine allocated and unallocated files<br> and "
      . "sort them into categories and verify the extension.<p>This allows you to find a file based on"
      . "its type and find \"hidden\" files.<p>\n"
      .

      "WARNING: This can be a time intensive process.<br>\n";

    Print::print_html_footer();
    return 0;
}

