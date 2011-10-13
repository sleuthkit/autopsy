#
# Metadata mode
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

# Updated 1/13

package Meta;

$Meta::FRAME  = 0;
$Meta::ENTER  = 1;
$Meta::STATS  = 2;
$Meta::EXPORT = 3;
$Meta::FFIND  = 4;
$Meta::LIST   = 5;
$Meta::REPORT = 6;
$Meta::BLANK  = 7;

sub main {

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $Meta::FRAME
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    # Check Basic Args
    Args::check_vol('vol');

    # These windows don't need the meta data address
    if ($view == $Meta::FRAME) {
        return frame();
    }
    elsif ($view == $Meta::ENTER) {
        return enter();
    }
    elsif ($view == $Meta::LIST) {
        return list();
    }
    elsif ($view == $Meta::BLANK) {
        return blank();
    }

    # These windows do need the meta data address
    Args::check_meta('meta');
    if ($view == $Meta::STATS) {
        return stats();
    }
    elsif ($view == $Meta::FFIND) {
        return findfile();
    }

    Args::check_recmode();
    if ($view == $Meta::EXPORT) {
        return export();
    }
    elsif ($view == $Meta::REPORT) {
        return report();
    }
    else {
        Print::print_check_err("Invalid Meta View");
    }

}

# Print the two frames
sub frame {

    my $vol = Args::get_vol('vol');

    Print::print_html_header_frameset(
        "Meta Data Browse on $Caseman::vol2sname{$vol}");
    print "<frameset cols=\"20%,80%\">\n";

    # Print the frame where an addres can be entered and a frame for the
    # contents
    if (exists $Args::enc_args{'meta'}) {
        print
"<frame src=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::ENTER&$Args::baseargs"
          . "&meta=$Args::enc_args{'meta'}\">\n"
          . "<frame src=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::STATS&"
          . "meta=$Args::enc_args{'meta'}&$Args::baseargs\" "
          . "name=\"content\">\n</frameset>\n";
    }
    else {
        print
"<frame src=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::ENTER&$Args::baseargs\">\n"
          . "<frame src=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::BLANK&$Args::baseargs\" "
          . "name=\"content\">\n</frameset>\n";
    }

    Print::print_html_footer_frameset();
    return 0;
}

# Generate the frame to enter the data into
sub enter {
    Print::print_html_header("");
    my $vol   = Args::get_vol('vol');
    my $ftype = $Caseman::vol2ftype{$vol};

    # Address
    print "<form action=\"$::PROGNAME\" method=\"get\" target=\"content\">\n"
      . "<b>$Fs::meta_str{$ftype} Number:</b><br>&nbsp;&nbsp&nbsp;&nbsp;"
      . "<input type=\"text\" name=\"meta\" size=12 maxlength=12";

    print " value=\"$Args::enc_args{'meta'}\"" if exists($Args::args{'meta'});

    print ">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_META\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Meta::STATS\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
      . Args::make_hidden()
      .

      # View Button
      "<p><input type=\"image\" src=\"pict/but_view.jpg\" "
      . "width=45 height=22 alt=\"View\" border=\"0\"></form>\n";

    # Allocation List
    print "<hr><p>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::LIST&$Args::baseargs\" target=\"content\">"
      . "<img src=\"pict/but_alloc_list.jpg\" border=\"0\" "
      . "width=113 height=20 alt=\"Allocation List\">"
      . "</a>\n";

    Print::print_html_footer();
    return 0;
}

# Display the contents of meta
sub stats {
    Print::print_html_header("");

    my $meta    = Args::get_meta('meta');
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Displaying details of $Fs::meta_str{$ftype} $meta"
    );

    my $meta_int = $meta;
    $meta_int = $1 if ($meta =~ /^(\d+)-\d+(-\d)?/);

    my $prev = $meta_int - 1;
    my $next = $meta_int + 1;

    # We need to get the allocation status of this structure
    my $recmode = $File::REC_NO;
    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/ils' -f $ftype -e -o $offset -i $imgtype $img $meta_int");
    while ($_ = Exec::read_pipe_line(*OUT)) {
        chop;
        next unless ($_ =~ /^$meta_int/);
        if ($_ =~ /^$meta_int\|f/) {
            $recmode = $File::REC_YES;
        }
        elsif ($_ =~ /^$meta_int\|a/) {
            $recmode = $File::REC_NO;
        }
        else {
            Print::print_err("Error parsing ils output: $_");
        }
    }
    close(OUT);

    print "<center>\n";
    print
"<a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::STATS&$Args::baseargs&meta=$prev\">"
      . "<img src=\"pict/but_prev.jpg\" alt=\"previous\" "
      . "width=\"89\" height=20 border=\"0\"></a>\n"
      unless ($prev < $Fs::first_meta{$ftype});

    print
"<a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::STATS&$Args::baseargs&meta=$next\">"
      . "<img src=\"pict/but_next.jpg\" alt=\"next\" "
      . "width=\"89\" height=20 border=\"0\"></a>\n<br>";

    # Report
    print "<table cellspacing=\"0\" cellpadding=\"2\">\n<tr>"
      . "<td><a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::REPORT"
      . "&$Args::baseargs&meta=$meta&recmode=$recmode\""
      . " target=\"_blank\">"
      . "<img src=\"pict/but_report.jpg\" alt=\"report\" "
      . "width=88 height=20 border=\"0\">"
      . "</a></td>\n";

    # View (File Mode)
    print "<td><a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_FR"
      . "&$Args::baseargs&meta=$meta&recmode=$recmode"
      . "&dir=$vol-meta-$meta\" target=\"_blank\">"
      . "<img src=\"pict/but_viewcont.jpg\" alt=\"view contents\" "
      . "width=123 height=20 border=\"0\">"
      . "</a></td>\n";

    # Export
    print "<td><a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::EXPORT"
      . "&$Args::baseargs&meta=$meta&recmode=$recmode\">"
      . "<img src=\"pict/but_export.jpg\" alt=\"export\" "
      . "width=123 height=20 border=\"0\">"
      . "</a></td>";

    # Notes
    print "<td><a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::ENTER_FILE"
      . "&$Args::baseargs&meta=$meta&\" "
      . "target=\"_blank\">"
      . "<img src=\"pict/but_addnote.jpg\" alt=\"Add Note\" "
      . "width=\"89\" height=20 border=\"0\">"
      . "</a></td>"
      if ($::USE_NOTES == 1);

    print "</tr></table>\n</center>\n";

    my $tmpr = $Caseman::vol2mnt{$vol};

    if ($ftype =~ /fat/) {
        print
"<a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::FFIND&$Args::baseargs&"
          . "meta=$meta\" target=\"_blank\">Search for File Name</a><br><br>";
    }
    else {

        print "<b>Pointed to by file:</b><br>\n";

        local *OUT;
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/ffind' -f $ftype -a -o $offset -i $imgtype $img $meta");
        my $cnt = 0;
        while ($_ = Exec::read_pipe_line(*OUT)) {
            chop;
            if (/^(\*)\s+\/*(.*)$/) {
                Print::print_output("<tt><font color=\"$::DEL_COLOR[0]\">"
                      . Print::html_encode($tmpr . $2)
                      . "</font></tt> (deleted)<br><br>\n");
            }
            elsif (/^\/(.*)$/) {
                Print::print_output("<tt>"
                      . Print::html_encode($tmpr . $1)
                      . "</tt><br><br>\n");
            }
            else {
                Print::print_output(Print::html_encode($_) . "<br><br>\n");
            }
            $cnt++;
        }
        close(OUT);
        if ($cnt == 0) {
            print "<br>Invalid $Fs::meta_str{$ftype} value<br><br>\n";
            return;
        }
    }

    if ($recmode == $File::REC_YES) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -r -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }
    my $file_type = Exec::read_pipe_line(*OUT);
    close(OUT);

    $file_type = "Error getting file type"
      if ((!defined $file_type) || ($file_type eq ""));

    if ($recmode == $File::REC_YES) {
        print "<b>File Type (Recovered):</b><br>$file_type<br>\n";
    }
    else {
        print "<b>File Type:</b><br>$file_type<br><br>\n";
    }

    # MD5 Value
    if ($recmode == $File::REC_YES) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -r -o $offset -i $imgtype $img $meta | '$::MD5_EXE'"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -o $offset -i $imgtype $img $meta | '$::MD5_EXE'"
        );
    }

    my $md5out = Exec::read_pipe_line(*OUT);
    close(OUT);

    $md5out = "Error getting MD5"
      if ((!defined $md5out) || ($md5out eq ""));

    chomp $md5out;
    if ($recmode == $File::REC_YES) {
        print "<b>MD5 of recovered content:</b><br><tt>$md5out</tt><br><br>\n";
    }
    else {
        print "<b>MD5 of content:</b><br><tt>$md5out</tt><br><br>\n";
    }

    # Hash Database Lookups
    if (
        (
               ($::NSRLDB ne "")
            || ($Caseman::alert_db   ne "")
            || ($Caseman::exclude_db ne "")
        )
        && ($md5out =~ /^$::REG_MD5$/o)
      )
    {

        print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_blank\">\n"
          . Args::make_hidden()
          . "<input type=\"hidden\" name=\"md5\" value=\"$md5out\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_HASH\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Hash::DB_LOOKUP\">\n"
          . "<table cellpadding=\"2\" cellspacing=\"8\"><tr>\n";

        if ($::NSRLDB ne "") {
            print "<td align=\"left\">"
              . "<input type=\"checkbox\" name=\"hash_nsrl\" value=\"1\" CHECKED>"
              . "NSRL</td>\n";
        }
        if ($Caseman::alert_db ne "") {
            print "<td align=\"left\">"
              . "<input type=\"checkbox\" name=\"hash_alert\" value=\"1\" CHECKED>"
              . "Alert Database</td>\n";
        }
        if ($Caseman::exclude_db ne "") {
            print "<td align=\"left\">"
              . "<input type=\"checkbox\" name=\"hash_exclude\" value=\"1\" CHECKED>"
              . "Exclude Database</td>\n";
        }
        print "<td align=\"left\">"
          . "<input type=\"image\" src=\"pict/but_lookup.jpg\" "
          . "width=116 height=20 alt=\"Ok\" border=\"0\">"
          . "</td></tr></table>\n</form>\n";
    }

    # SHA-1 Value
    if ($::SHA1_EXE ne "") {
        if ($recmode == $File::REC_YES) {
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -r -o $offset -i $imgtype $img $meta | '$::SHA1_EXE'"
            );
        }
        else {
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -o $offset -i $imgtype $img $meta | '$::SHA1_EXE'"
            );
        }

        my $sha1out = Exec::read_pipe_line(*OUT);
        close(OUT);

        $sha1out = "Error getting SHA-1"
          if ((!defined $sha1out) || ($sha1out eq ""));

        chomp $sha1out;
        if ($recmode == $File::REC_YES) {
            print
"<b>SHA-1 of recovered content:</b><br><tt>$sha1out</tt><br><br>\n";
        }
        else {
            print "<b>SHA-1 of content:</b><br><tt>$sha1out</tt><br><br>\n";
        }
    }

    # istat output
    print "<b>Details:</b><br><br>\n";
    my $mode  = 0;    # set to 1 when showing blocks
    my $force = 0;    # set to 1 if size of meta is 0

    my @output;
    if (exists($Args::args{'force'})) {
        my $f = Args::get_force();
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/istat' -f $ftype $tz -s $Caseman::ts -B $f -o $offset -i $imgtype $img $meta"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/istat' -f $ftype $tz -s $Caseman::ts -o $offset -i $imgtype $img $meta"
        );
    }
    while ($_ = Exec::read_pipe_line(*OUT)) {
        if ($mode == 1) {
            if (/^Indirect Blocks/) {
                print "$_<br>\n";
                next;
            }
            elsif (/^Recover/) {
                print "$_<br>\n";
                next;
            }
            elsif (/^Type: (\S+) \((\d+\-\d+)\) (.*)$/) {
                print "$1 ("
                  . "<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_FR&$Args::baseargs"
                  . "&meta=$meta_int-$2&dir=$vol-meta-$meta_int-$2\" "
                  . "target=\"_blank\">$2</a>) $3<br>\n";
                next;
            }

            my $blk;
            foreach $blk (split(/ /, $_)) {
                if ($blk =~ /^\d+$/) {
                    print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_DATA&"
                      . "$Args::baseargs&block=$blk\" target=\"_top\">$blk</a>  ";
                }
                else {
                    print "$blk   ";
                }
            }
            print "<br>\n";
        }
        else {
            if (/^Not Allocated$/) {
                print "<font color=\"$::DEL_COLOR[0]\">$_</font><br>\n";
            }
            else {
                print "$_<br>\n";
            }
            $mode = 1 if (/^Direct Blocks|^Sectors/);
            $mode = 1 if (/^Attributes:/);  # HFS gets messed up without ":"
            $mode = 1 if (/^Data Fork Blocks:/); 

            if ((/^size: (\d+)/) && ($1 == 0)) {
                $force = 1;
            }
        }
    }
    close(OUT);

    # display a text box to force X number of blocks to be displayed
    if ($force == 1) {
        print "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . Args::make_hidden()
          . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
          . "<input type=\"hidden\" name=\"meta\" value=\"$meta\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_META\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Meta::STATS\">\n";

        print
"Enter number of $Fs::addr_unit{$ftype}s to display: <input type=\"text\" "
          . "value=5 name=\"force\" size=\"3\">\n";

        print "<input type=\"image\" src=\"pict/but_force.jpg\" "
          . "width=53 height=20 alt=\"Force\" border=\"0\"> (because the size is 0)\n</form>\n";
    }

    Print::print_html_footer();
    return 0;
}

sub findfile {

    Print::print_html_header("Find File");

    my $meta    = Args::get_meta('meta');
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $tmpr    = $Caseman::vol2mnt{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    print "<b>Pointed to by file:</b><br>\n";

    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/ffind' -f $ftype -a -o $offset -i $imgtype $img $meta");
    while ($_ = Exec::read_pipe_line(*OUT)) {
        chop;
        if (/(\*)\s+\/*(.*)/) {
            Print::print_output("<tt><font color=\"$::DEL_COLOR[0]\">"
                  . Print::html_encode($tmpr . $2)
                  . "</font></tt> (deleted)<br>\n");
        }
        elsif (/^\/(.*)$/) {
            Print::print_output(
                "<tt>" . Print::html_encode($tmpr . $1) . "</tt><br>\n");
        }
        else {
            Print::print_output(Print::html_encode($_) . "<br>\n");
        }
    }
    close(OUT);

    Print::print_html_footer();
    return 0;
}

sub export {
    my $meta    = Args::get_meta('meta');
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $recmode = Args::get_recmode();

    Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Saving contents of $Fs::meta_str{$ftype} $meta"
    );

    Print::print_oct_header("$vol" . "-meta" . "$meta" . ".raw");

    local *OUT;
    if ($recmode == $File::REC_YES) {
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/icat' -f $ftype -r -o $offset -i $imgtype $img $meta");
    }
    else {
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/icat' -f $ftype -o $offset -i $imgtype $img $meta");
    }

    print "$_" while ($_ = Exec::read_pipe_data(*OUT, 512));
    close(OUT);

    Print::print_oct_footer();
}

sub report {

    my $meta    = Args::get_meta('meta');
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $recmode = Args::get_recmode();

    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Generating report for $Fs::meta_str{$ftype} $meta"
    );

    Print::print_text_header("filename=$vol-meta$meta.txt");

    print "                 Autopsy $Fs::meta_str{$ftype} Report\n\n"
      . "-" x 70 . "\n"
      . "                    GENERAL INFORMATION\n\n"
      . "$Fs::meta_str{$ftype}: $Args::args{'meta'}\n";

    print "Pointed to by file(s):\n";
    my $tmpr = $Caseman::vol2mnt{$vol};
    local *OUT;

    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/ffind' -f $ftype -a -o $offset -i $imgtype $img $meta");
    while ($_ = Exec::read_pipe_line(*OUT)) {
        chop;
        if (/^(\*)\s+\/*(.*)$/) {
            Print::print_output(
                "  ${tmpr}${2} (deleted)\n");
        }
        elsif (/^\/(.*)$/) {
            Print::print_output("  ${tmpr}${1}\n");
        }
        else {
            Print::print_output("  $_\n");
        }
    }
    close(OUT);

    Exec::exec_pipe(*OUT,
"'$::TSKDIR/istat' -f $ftype $tz -s $Caseman::ts -o $offset -i $imgtype $img $meta | '$::MD5_EXE'"
    );
    my $md5 = Exec::read_pipe_line(*OUT);
    close(OUT);

    $md5 = "Error getting MD5 Value"
      if ((!defined $md5) || ($md5 eq ""));

    chop $md5;
    print "MD5 of istat output: $md5\n";

    if ($::SHA1_EXE ne "") {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/istat' -f $ftype $tz -s $Caseman::ts -o $offset -i $imgtype $img $meta | '$::SHA1_EXE'"
        );
        my $sha1 = Exec::read_pipe_line(*OUT);
        close(OUT);

        $sha1 = "Error getting SHA-1 Value"
          if ((!defined $sha1) || ($sha1 eq ""));

        chop $sha1;
        print "SHA-1 of istat output: $sha1\n";
    }

    print "\nImage: $Caseman::vol2path{$vol}\n";
    if (($Caseman::vol2start{$vol} == 0) && ($Caseman::vol2end{$vol} == 0)) {
        print "Offset: Full image\n";
    }
    elsif ($Caseman::vol2end{$vol} == 0) {
        print "Offset: $Caseman::vol2start{$vol} to end\n";
    }
    else {
        print "Offset: $Caseman::vol2start{$vol} to $Caseman::vol2end{$vol}\n";
    }
    print "File System Type: $ftype\n";

    my $date = localtime();
    print "\nDate Generated: $date\n"
      . "Investigator: $Args::args{'inv'}\n\n"
      . "-" x 70 . "\n"
      . "                   META DATA INFORMATION\n\n";

    Exec::exec_pipe(*OUT,
"'$::TSKDIR/istat' -f $ftype $tz -s $Caseman::ts -o $offset -i $imgtype $img $meta"
    );
    while ($_ = Exec::read_pipe_line(*OUT)) {
        print $_;
    }
    close(OUT);

    if ($recmode == $File::REC_YES) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -r -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }

    my $file_type = Exec::read_pipe_line(*OUT);
    close(OUT);

    $file_type = "Error getting file type"
      if ((!defined $file_type) || ($file_type eq ""));

    print "\nFile Type: $file_type";

    print "\n"
      . "-" x 70 . "\n"
      . "                   VERSION INFORMATION\n\n"
      . "Autopsy Version: $::VER\n";
    print "The Sleuth Kit Version: " . ::get_tskver() . "\n";

    Print::print_text_footer();

    return 0;
}

# Display the meta Allocation Table
sub list {
    my $ILS_GAP = 500;

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $min = 0;

    $min = Args::get_min() if (exists $Args::args{'min'});
    my $max = $min + $ILS_GAP - 1;

    # Because we can not use metas 0 and 1 for most FS, set fmin to the
    # minimum for this fs
    my $fmin = $min;
    $fmin = $Fs::first_meta{$ftype} if ($min < $Fs::first_meta{$ftype});

    Print::print_html_header(
        "$Fs::meta_str{$ftype} Allocation List $fmin -&gt $max");

    Print::log_host_inv(
"$Caseman::vol2sname{$vol}: $Fs::meta_str{$ftype} Allocation List for $min to $max"
    );

    print "<center><H2>$Fs::meta_str{$ftype}: $fmin - $max</H2>";

    # Display next and previous links
    my $tmp;
    if ($min > $Fs::first_meta{$ftype}) {
        $tmp = $min - $ILS_GAP;
        print
"<a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::LIST&$Args::baseargs&min=$tmp\">"
          . "<img src=\"pict/but_prev.jpg\" alt=\"previous\" "
          . "width=\"89\" height=20 border=\"0\"></a> ";
    }
    $tmp = $min + $ILS_GAP;
    print
" <a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::LIST&$Args::baseargs&min=$tmp\">"
      . "<img src=\"pict/but_next.jpg\" alt=\"next\" "
      . "width=\"89\" height=20 border=\"0\"></a><br>";
    print "</center>\n";

    # The list
    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/ils' -e -s $Caseman::ts -f $ftype -o $offset -i $imgtype $img $fmin-$max"
    );
    while ($_ = Exec::read_pipe_line(*OUT)) {
        if (/^($::REG_META)\|([af])\|\d+\|\d+\|\d+\|\d+\|\d+\|/o) {
            print
"<a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::STATS&$Args::baseargs&meta=$1\">"
              . "$1:</a> ";
            if ($2 eq "a") {
                print "allocated<br>\n";
            }
            else {
                print "<font color=\"$::DEL_COLOR[0]\">free</font><br>\n";
            }
        }
    }
    close(OUT);

    # Display next and previous links
    print "<center>\n";
    if ($min > $Fs::first_meta{$ftype}) {
        $tmp = $min - $ILS_GAP;
        print
"<a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::LIST&$Args::baseargs&min=$tmp\">"
          . "<img src=\"pict/but_prev.jpg\" alt=\"previous\" "
          . "width=\"89\" height=20 border=\"0\"></a> ";
    }
    $tmp = $min + $ILS_GAP;
    print
" <a href=\"$::PROGNAME?mod=$::MOD_META&view=$Meta::LIST&$Args::baseargs&min=$tmp\">"
      . "<img src=\"pict/but_next.jpg\" alt=\"next\" "
      . "width=\"89\" height=20 border=\"0\"></a><br>";
    print "</center>\n";

    Print::print_html_footer();
    return 0;

}

# Blank Page
sub blank {
    Print::print_html_header("Metadata Blank Page");
    my $vol   = Args::get_vol('vol');
    my $ftype = $Caseman::vol2ftype{$vol};

    print "<center><h3>Metadata Mode</h3><br>\n"
      . "Here you can view the details about any $Fs::meta_str{$ftype} in the file system.<br>\n"
      . "These are the data structures that store the file details.<br>\n"
      . "Enter the address in the field on the left.\n";
    Print::print_html_footer();
    return 0;
}

