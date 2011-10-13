#
# Notes and sequencer functions
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

package Notes;

use POSIX;

$Notes::ENTER_FILE    = 1;
$Notes::ENTER_DATA    = 2;
$Notes::WRITE_FILE    = 3;
$Notes::WRITE_DATA    = 4;
$Notes::WRITE_SEQ_MAN = 5;
$Notes::READ_NORM     = 6;
$Notes::READ_SEQ      = 7;

sub main {

    # There is no default for Notes
    Args::check_view();

    Print::print_check_error("Notes option is not enabled")
      if ($::USE_NOTES == 0);

    my $view = Args::get_view();

    if ($view == $Notes::ENTER_FILE) {
        return enter_file();
    }
    elsif ($view == $Notes::ENTER_DATA) {
        return enter_data();
    }
    elsif ($view == $Notes::WRITE_FILE) {
        return write_file();
    }
    elsif ($view == $Notes::WRITE_DATA) {
        return write_data();
    }
    elsif ($view == $Notes::WRITE_SEQ_MAN) {
        return write_seq_man();
    }
    elsif ($view == $Notes::READ_NORM) {
        return read_norm();
    }
    elsif ($view == $Notes::READ_SEQ) {
        return read_seq();
    }
    else {
        Print::print_check_err("Invalid Notes View");
    }
}

sub investig_notes_fname {
    return "$::host_dir" . "$::LOGDIR/$Args::args{'inv'}.notes";
}

sub investig_seq_notes_fname {
    return "$::host_dir" . "$::LOGDIR/$Args::args{'inv'}.seq.notes";
}

# window where user can enter a normal and sequencer note for a file
# or meta data structure.
sub enter_file {
    Args::check_vol('vol');
    Args::check_meta('meta');

    my $vol   = Args::get_vol('vol');
    my $ftype = $Caseman::vol2ftype{$vol};
    my $mnt   = $Caseman::vol2mnt{$vol};
    my $meta  = Args::get_meta('meta');

    # A file will have a 'dir' argument and a meta structure will not
    if (exists $Args::args{'dir'}) {
        my $fname = "$mnt$Args::args{'dir'}";
        Print::print_html_header("Notes for file $fname");
        print "<center><b>Enter a note for <tt>$fname</tt> ($meta):</b>"
          . "<br><br>\n";
    }
    else {
        Print::print_html_header("Notes for $Fs::meta_str{$ftype} $meta");
        print "<center><b>Enter a note for $Fs::meta_str{$ftype} $meta:</b>"
          . "<br><br>\n";
    }
    print
"A note works like a bookmark and allows you to later find this data more easily.<br><br>\n";

    # Setup the form
    print "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<textarea rows=10 cols=50 wrap=\"virtual\" name=\"note\">"
      . "</textarea><br>\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_NOTES\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Notes::WRITE_FILE\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
      . "<input type=\"hidden\" name=\"meta\" value=\"$meta\">\n"
      . Args::make_hidden();

    print "<input type=\"hidden\" name=\"dir\" value=\"$Args::args{'dir'}\">\n"
      if (exists $Args::args{'dir'});

    # Option to add a normal note
    print "<input type=\"checkbox\" name=\"norm_note\" value=\"1\" CHECKED>\n"
      . "  Add a Standard Note<br>\n";

    # Sequencer notes - which requires the MAC times for the files
    if ("$Caseman::tz" ne "") {
        $ENV{TZ} = $Caseman::tz;
      POSIX: tzset();
    }

    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $meta_int = $meta;
    $meta_int = $1 if ($meta_int =~ /^(\d+)-\d+(-\d+)?$/);
    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/ils' -s $Caseman::ts -f $ftype -e  -o $offset -i $imgtype $img $meta_int"
    );

    # Get the fourth line
    my $tmp = Exec::read_pipe_line(*OUT);
    $tmp = Exec::read_pipe_line(*OUT);
    $tmp = Exec::read_pipe_line(*OUT);
    $tmp = Exec::read_pipe_line(*OUT);
    close(OUT);
    unless ((defined $tmp)
        && ($tmp =~ /^$::REG_META\|\w\|\d+\|\d+\|(\d+)\|(\d+)\|(\d+)\|/o))
    {
        Print::print_err("Error parsing 'ils' output");
    }

    my $mtime = $1;
    my $atime = $2;
    my $ctime = $3;

    $mtime = localtime($mtime);
    $atime = localtime($atime);
    $ctime = localtime($ctime);

    # Print the Times
    print "<br><hr><b>Add a Sequencer Event:</b><br><br>\n"
      . "A sequencer event will be sorted based on the time so that event reconstruction will be easier<br><br>\n"
      . "<input type=\"checkbox\" name=\"mtime\" value=\"1\">"
      . "&nbsp;&nbsp;M-Time (<tt>$mtime</tt>)<br>\n"
      . "<input type=\"checkbox\" name=\"atime\" value=\"1\">"
      . "&nbsp;&nbsp;A-Time (<tt>$atime</tt>)<br>\n"
      . "<input type=\"checkbox\" name=\"ctime\" value=\"1\">"
      . "&nbsp;&nbsp;C-Time (<tt>$ctime</tt>)<br><hr><br>\n";

    # The OK Button
    print "<br><input type=\"image\" src=\"pict/but_ok.jpg\" "
      . "width=43 height=20 alt=\"Ok\" border=\"0\">\n</form>\n";

    Print::print_html_footer();
    return 0;
}

# data unit comment
sub enter_data {
    Args::check_vol('vol');

    my $vol   = Args::get_vol('vol');
    my $ftype = $Caseman::vol2ftype{$vol};
    my $block = Args::get_block();
    my $len   = Args::get_len();

    Print::print_html_header("Notes for $Fs::addr_unit{$ftype} $block");

    print
      "<center><b>Enter a note for $Fs::addr_unit{$ftype} $block</b><br><br>\n"
      . "A note works like a bookmark and allows you to later find this data more easily.<br><br>\n"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<textarea rows=10 cols=50 wrap=\"virtual\" name=\"note\">"
      . "</textarea><br>\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_NOTES\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Notes::WRITE_DATA\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
      . "<input type=\"hidden\" name=\"block\" value=\"$block\">\n"
      . "<input type=\"hidden\" name=\"len\" value=\"$len\">\n"
      . "<input type=\"hidden\" name=\"norm_note\" value=\"1\">\n"
      . Args::make_hidden()
      . "<br><input type=\"image\" src=\"pict/but_ok.jpg\" "
      . "width=43 height=20 alt=\"Ok\" border=\"0\">\n</form>\n";

    Print::print_html_footer();
    return 0;

}

# Write the note to the note file
sub write_data {
    Args::check_vol('vol');
    Args::check_block();
    Args::check_len();
    Args::check_note();

    Print::print_html_header("Write a note");

    my $vol    = Args::get_vol('vol');
    my $ftype  = $Caseman::vol2ftype{$vol};
    my $img_sh = $Caseman::vol2sname{$vol};
    my $block  = Args::get_block();
    my $len    = Args::get_len();

    Print::log_host_inv(
        "$img_sh: Creating note for $Fs::addr_unit{$ftype} $block");

    my $notes_file = investig_notes_fname();
    open NOTES, ">>$notes_file" or die "Can't open log: $notes_file";
    print "Note added to $notes_file:<p>\n\n";

    # Date
    my $tmp = localtime();
    print NOTES "$tmp\n";
    print "$tmp<br>\n";

    print NOTES "Volume: $vol  $Fs::addr_unit{$ftype}: $block  Len: $len\n";
    print "Volume: $vol  $Fs::addr_unit{$ftype}: $block  Len: $len<br>\n";

    # The actual notes and a line at the bottom
    print NOTES "\n$Args::args{'note'}\n\n" . "-" x 70 . "\n";
    print "<p>".Print::html_encode($Args::args{'note'})."<p>";
    close(NOTES);

    print "<hr>\n"
      . "You can view the notes from the Host Manager View<p>"
      . "<a href=\"$::PROGNAME?${Args::baseargs_novol}&mod=$::MOD_NOTES&view=$Notes::READ_NORM\">"
      . "<img border=0 src=\"pict/menu_b_note.jpg\" "
      . "width=\"167\" height=20 alt=\"View Notes\"></a>\n";

    Print::print_html_footer();

    return 0;
}

sub write_file {
    Args::check_vol('vol');
    Args::check_meta('meta');
    Args::check_note();

    # Get rid of carriage returns that Netscape adds
    $Args::args{'note'} =~ tr/\r//d;

    my $vol = Args::get_vol('vol');
    my $mnt = $Caseman::vol2mnt{$vol};

    my $ftype  = $Caseman::vol2ftype{$vol};
    my $img_sh = $Caseman::vol2sname{$vol};
    my $meta   = Args::get_meta('meta');

    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $fname = "";
    my $type  = "";

    if (exists $Args::args{'dir'}) {
        $Args::args{'dir'} .= "/"
          if ($Args::args{'dir'} eq "");
        $fname = "$mnt$Args::args{'dir'}";

        if (($Args::args{'dir'} =~ /\/$/) || ($Args::args{'dir'} eq "")) {
            Print::log_host_inv(
                "$img_sh: Creating note for directory $fname ($meta)");
            $type = "dir";
        }
        else {
            Print::log_host_inv(
                "$img_sh: Creating note for file $fname ($meta)");
            $type = "file";
        }
    }

    else {
        Print::log_host_inv(
            "$img_sh: Creating note for $Fs::meta_str{$ftype} $meta");
        $type = "$Fs::meta_str{$ftype}";
    }

    Print::print_html_header("Writing a note / event");

    # Get the times for the meta
    # Set the timezone to the host zone
    if ("$Caseman::tz" ne "") {
        $ENV{TZ} = "$Caseman::tz";
        POSIX::tzset();
    }

    my $meta_int = $meta;
    $meta_int = $1 if ($meta_int =~ /^(\d+)-\d+(-\d+)?$/);
    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/ils' -s $Caseman::ts -f $ftype -e -o $offset -i $imgtype $img $meta_int"
    );

    # Skip to the fourth line
    my $tmp = Exec::read_pipe_line(*OUT);
    $tmp = Exec::read_pipe_line(*OUT);
    $tmp = Exec::read_pipe_line(*OUT);
    $tmp = Exec::read_pipe_line(*OUT);
    unless ((defined $tmp)
        && ($tmp =~ /^$::REG_META\|\w\|\d+\|\d+\|(\d+)\|(\d+)\|(\d+)\|/o))
    {
        Print::print_err("Error parsing 'ils' output");
    }
    my $mtime = $1;
    my $atime = $2;
    my $ctime = $3;
    close(OUT);

    # Create a "normal" note
    if ((exists $Args::args{'norm_note'}) && ($Args::args{'norm_note'} == 1)) {

        my $notes_file = investig_notes_fname();
        open NOTES, ">>$notes_file" or die "Can't open log: $notes_file";
        print "Note added to $notes_file:<p>\n\n";

        # Date
        my $tmp = localtime();
        print NOTES "$tmp\n";
        print "$tmp\n";

        # We have a file name
        if ($fname ne "") {
            if ($type eq 'dir') {
                print NOTES "Directory: $fname\n";
                print "Directory: $fname<br>\n";
            }
            else {
                print NOTES "File: $fname\n";
                print "File: $fname<br>\n";
            }
        }
        if ($meta ne "") {
            print NOTES "Volume: $vol  Meta: $meta\n";
            print "Volume: $vol  Meta: $meta<br>\n";
        }
        print NOTES "M-time: " . localtime($mtime) . "\n";
        print "M-time: " . localtime($mtime) . "<br>\n";
        print NOTES "A-time: " . localtime($atime) . "\n";
        print "A-time: " . localtime($atime) . "<br>\n";
        print NOTES "C-time: " . localtime($ctime) . "\n";
        print "C-time: " . localtime($ctime) . "<br>\n";

        # The actual notes and a line at the bottom
        print NOTES "\n$Args::args{'note'}\n\n" . "-" x 70 . "\n";
        print "<p>".Print::html_encode($Args::args{'note'})."<p>";

        close(NOTES);
    }

    # Create a sequencer event - if there are any
    unless (((exists $Args::args{'mtime'}) && ($Args::args{'mtime'} == 1))
        || ((exists $Args::args{'atime'}) && ($Args::args{'atime'} == 1))
        || ((exists $Args::args{'ctime'}) && ($Args::args{'ctime'} == 1)))
    {

        print "<hr>\n"
          . "You can view the notes from the Host Manager View<p>"
          . "<a href=\"$::PROGNAME?${Args::baseargs_novol}&mod=$::MOD_NOTES&view=$Notes::READ_NORM\">"
          . "<img border=0 src=\"pict/menu_b_note.jpg\" "
          . "width=\"167\" height=20 alt=\"View Notes\"></a>\n";

        Print::print_html_footer();

        return 0;
    }

    # Get rid of the carriage returns
    $Args::args{'note'} =~ s/\n/<br>/gs;

    my $notes_file = investig_seq_notes_fname();
    open NOTES, ">>$notes_file" or die "Can't open log: $notes_file";

    if ((exists $Args::args{'mtime'}) && ($Args::args{'mtime'} == 1)) {

        my ($sec, $min, $hour, $mday, $mon, $year, $wday, $yday, $isdst) =
          localtime($mtime);
        $year += 1900;
        $mon  += 1;
        $mday = "0$mday" if ($mday < 10);
        $hour = "0$hour" if ($hour < 10);
        $min  = "0$min"  if ($min < 10);
        $sec  = "0$sec"  if ($sec < 10);

        print NOTES "'$year','$mon','$mday','$hour','$min','$sec',"
          . "'$Args::args{'host'}','$vol','$fname','$meta','',"
          . "'$type','[M-Time]$Args::args{'note'}'\n";

        Print::log_host_inv(
            "$img_sh: M-Time note added for meta $Args::args{'meta'}");
        print "M-Time sequence event added<br>\n";
    }

    if ((exists $Args::args{'atime'}) && ($Args::args{'atime'} == 1)) {
        my ($sec, $min, $hour, $mday, $mon, $year, $wday, $yday, $isdst) =
          localtime($atime);
        $year += 1900;
        $mon  += 1;
        $mday = "0$mday" if ($mday < 10);
        $hour = "0$hour" if ($hour < 10);
        $min  = "0$min"  if ($min < 10);
        $sec  = "0$sec"  if ($sec < 10);

        print NOTES "'$year','$mon','$mday','$hour','$min','$sec',"
          . "'$Args::args{'host'}','$vol','$fname','$meta','',"
          . "'$type','[A-Time]$Args::args{'note'}'\n";

        Print::log_host_inv(
            "$img_sh: A-Time note added for meta $Args::args{'meta'}");
        print "A-Time sequence event added<br>\n";
    }

    if ((exists $Args::args{'ctime'}) && ($Args::args{'ctime'} == 1)) {
        my ($sec, $min, $hour, $mday, $mon, $year, $wday, $yday, $isdst) =
          localtime($ctime);
        $year += 1900;
        $mon  += 1;
        $mday = "0$mday" if ($mday < 10);
        $hour = "0$hour" if ($hour < 10);
        $min  = "0$min"  if ($min < 10);
        $sec  = "0$sec"  if ($sec < 10);

        print NOTES "'$year','$mon','$mday','$hour','$min','$sec',"
          . "'$Args::args{'host'}','$vol','$fname','$meta','',"
          . "'$type','[C-Time]$Args::args{'note'}'\n";

        Print::log_host_inv(
            "$img_sh: C-Time note added for meta $Args::args{'meta'}");
        print "C-Time sequence event added<br>\n";
    }

    close(NOTES);

    print "<p><hr>\n"
      . "You can view the notes and events from the Host Manager View<p>";

    if ((exists $Args::args{'norm_note'}) && ($Args::args{'norm_note'} == 1)) {
        print
"<a href=\"$::PROGNAME?${Args::baseargs_novol}&mod=$::MOD_NOTES&view=$Notes::READ_NORM\">"
          . "<img border=0 src=\"pict/menu_b_note.jpg\" "
          . "width=\"167\" height=20 alt=\"View Notes\"></a>\n";
    }

    print
"<a href=\"$::PROGNAME?${Args::baseargs_novol}&mod=$::MOD_NOTES&view=$Notes::READ_SEQ\">"
      . "<img border=0 src=\"pict/menu_b_seq.jpg\" width=\"167\" height=\"20\" "
      . " alt=\"Event Sequencer\"></a>\n";

    Print::print_html_footer();

    return 0;
}

# Display the contents of the "normal" notes file
sub read_norm {
    Print::print_html_header("Contents of Notes File");

    my $notes_file = investig_notes_fname();

    Print::log_host_inv("Viewing contents of notes file ($notes_file)");
    if ((!(-e "$notes_file")) || (-z "$notes_file")) {
        print "No notes have been entered yet.<br>\n"
          . "They can be entered using the <i>Add Note</i> link within each analysis mode.<br>\n";
        return;
    }

    open NOTES, "<$notes_file" or die "Can't open log: $notes_file";

    my $file = "";
    my $dir  = "";

    print "<table width=100%>\n"
      . "<tr bgcolor=$::BACK_COLOR><td align=left>\n";

    my $row = 0;

    # This will need to change whenever the log format changes
    while (<NOTES>) {

        # we need to extract mnt from here
        $file = $1 if (/^File: (.*)$/);
        $dir  = $1 if (/^Directory: (.*)$/);

        # Reset the $file if we are at the end of the current note
        if (/^\-+$/) {
            $file = "";
            $dir  = "";
            if (($row++ % 2) == 0) {
                print
"</td></tr>\n<tr bgcolor=$::BACK_COLOR_TABLE><td align=left>\n";
            }
            else {
                print "</td></tr>\n<tr bgcolor=$::BACK_COLOR><td align=left>\n";
            }
        }
        else {
            print Print::html_encode($_);
        }

        if (/^Volume: ($::REG_VNAME)   Meta: ([0-9\-]+)/o) {
            $vol  = $1;
            $meta = $2;
            next unless (exists $Caseman::vol2cat{$vol});

            # file note
            if ($file ne "") {

                # extract the prepended mnt value
                my $mnt   = $Caseman::vol2mnt{$vol};
                my $fname = "";
                $fname = $1 if ($file =~ /^$mnt\/?(.*)$/);
                print "<a href=\"$::PROGNAME?$Args::baseargs_novol"
                  . "&mod=$::MOD_FILE&view=$File::CONT_FR"
                  . "&vol=$vol&meta=$meta&dir=$fname\" target=\"_blank\">"
                  . "<img src=\"pict/but_view.jpg\" alt=\"view\" "
                  . "width=45 height=22 alt=\"View\" border=\"0\"></a><br>\n";
            }

            # directory note
            elsif ($dir ne "") {

                # extract the prepended mnt value
                my $mnt   = $Caseman::vol2mnt{$vol};
                my $fname = "";
                $fname = $1 if ($dir =~ /^$mnt\/?(.*)$/);
                print "<a href=\"$::PROGNAME?$Args::baseargs_novol"
                  . "&mod=$::MOD_FRAME&submod=$::MOD_FILE&vol=$vol&"
                  . "&meta=$meta&dir=$fname\" target=\"_blank\">"
                  . "<img src=\"pict/but_view.jpg\" alt=\"view\" "
                  . "width=45 height=22 alt=\"View\" border=\"0\"></a><br>\n";

            }

            # meta note
            else {
                print "<a href=\"$::PROGNAME?$Args::baseargs_novol"
                  . "&mod=$::MOD_FRAME&submod=$::MOD_META&vol=$vol"
                  . "&meta=$meta\" target=\"_blank\">"
                  . "<img src=\"pict/but_view.jpg\" alt=\"view\" "
                  . "width=45 height=22 alt=\"View\" border=\"0\"></a><br>\n";

            }
        }

        # block note
        elsif (/^Volume: ($::REG_VNAME)   \w+: ([0-9]+)  Len: (\d+)/o) {
            $vol = $1;
            $blk = $2;
            $len = $3;
            next unless (exists $Caseman::vol2cat{$vol});

            print "<a href=\"$::PROGNAME?$Args::baseargs_novol"
              . "&mod=$::MOD_FRAME&submod=$::MOD_DATA&vol=$vol"
              . "&block=$blk&len=$len\" target=\"_blank\">"
              . "<img src=\"pict/but_view.jpg\" alt=\"view\" "
              . "width=45 height=22 alt=\"View\" border=\"0\"></a><br>\n";

        }
    }

    print "</tr></table>\n";

    # Ok and refresh buttons
    print "<p><center><table width=600>\n"
      . "<tr><td width=300 align=center>\n"
      . "<a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&view=$Caseman::VOL_OPEN&$Args::baseargs\">"
      . "<img border=0 src=\"pict/menu_b_close.jpg\" width=167 height=20 alt=\"close\"></a>\n"
      . "</td><td width=300 align=center>\n"
      . "<a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::READ_NORM&$Args::baseargs\">"
      . "<img border=0 src=\"pict/menu_b_ref.jpg\" width=167 height=20 alt=\"refresh\"></a>\n"
      . "</td></tr></table>\n";

    close(NOTES);

    Print::print_html_footer();

    return 0;
}

#########################################################################
# Sequencer Code

# Write a sequence event that was manually entered
sub write_seq_man {

    Args::check_note();

    Print::print_html_header("Writing Sequencer Event");

    # Get rid of carriage returns that Netscape adds
    $Args::args{'note'} =~ tr/\r//d;
    $Args::args{'note'} =~ s/\n/<br>/gs;

    if ($Args::args{'note'} eq "") {
        print(  "A comment must be given for the event<br>\n"
              . "<p><a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::READ_SEQ&$Args::baseargs\">"
              . "<img border=0 src=\"pict/menu_b_ok.jpg\" "
              . "width=167 height=20></a>");
        Print::print_err("\n");
    }

    # Check the args and add them to the final string that will be written
    my $str = "";
    unless ((exists $Args::args{'year'})
        && ($Args::args{'year'} =~ /^(\d\d\d\d)$/))
    {
        Print::print_err("Invalid year");
    }
    $str .= "'$1',";

    unless ((exists $Args::args{'mon'}) && ($Args::args{'mon'} =~ /^(\d\d?)$/))
    {
        Print::print_err("Invalid month");
    }
    $str .= "'$1',";

    unless ((exists $Args::args{'day'}) && ($Args::args{'day'} =~ /^(\d\d?)$/))
    {
        Print::print_err("Invalid day");
    }
    $str .= "'$1',";

    unless ((exists $Args::args{'hour'})
        && ($Args::args{'hour'} =~ /^(\d\d?)$/))
    {
        Print::print_err("Invalid hour");
    }
    $str .= "'$1',";

    unless ((exists $Args::args{'min'}) && ($Args::args{'min'} =~ /^(\d\d?)$/))
    {
        Print::print_err("Invalid min");
    }
    $str .= "'$1',";

    unless ((exists $Args::args{'sec'}) && ($Args::args{'sec'} =~ /^(\d\d?)$/))
    {
        Print::print_err("Invalid sec");
    }
    $str .= "'$1',";

    # There are no image, meta, file name, or data unit for this type
    $str .= "'$Args::args{'host'}','','','','',";

    unless ((exists $Args::args{'src'}) && ($Args::args{'src'} =~ /^(\w+)$/)) {
        Print::print_err("Invalid src");
    }
    $str .= "'$1','$Args::args{'note'}'\n";

    # Write the string to the notes file
    my $notes_file = investig_seq_notes_fname();
    open NOTES, ">>$notes_file" or die "Can't open log: $notes_file";
    print NOTES $str;
    close(NOTES);

    # Send a message to the user
    print "Event Added to Sequencer file:<br><br>\n"
      . "$::d2m[$Args::args{'mon'}] $Args::args{'day'}, $Args::args{'year'} "
      . "$Args::args{'hour'}:$Args::args{'min'}:$Args::args{'sec'}<br><br>\n"
      . Print::html_encode($Args::args{'note'})."<br>\n"
      . "<p><a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::READ_SEQ&$Args::baseargs&"
      . "year=$Args::enc_args{'year'}&mon=$Args::enc_args{'mon'}&day=$Args::enc_args{'day'}&"
      . "hour=$Args::enc_args{'hour'}&min=$Args::enc_args{'min'}&sec=$Args::enc_args{'sec'}\">"
      . "<img border=0 src=\"pict/but_ok.jpg\" alt=\"Ok\" "
      . "width=43 height=20></a>\n";

    Print::print_html_footer();
    return 0;
}

# View the sequencer file
sub read_seq {

    Print::print_html_header("Event Sequencer");

    print "<center>\n" . "<h3>Event Sequencer</h3>\n";

    my $cnt = 0;
    my @entry;
    my (
        @year, @mon,   @day,  @hour, @min,  @sec, @host,
        @vol,  @fname, @meta, @data, @type, @note
    );

    # Read the sequencer file into arrays that will be sorted
    my $notes_file = investig_seq_notes_fname();
    if (-e $notes_file) {

        open NOTES, "$notes_file" or die "Can't open log: $notes_file";
        while (<NOTES>) {

            unless (
/^'?(\d+)'?,'?(\d+)'?,'?(\d+)'?,'?(\d+)'?,'?(\d+)'?,'?(\d+)'?,'?($::REG_HOST)'?,'?($::REG_VNAME)?'?,'?(.*?)'?,'?($::REG_META)?'?,'?(\d+)?'?,'?([\w\s]+)'?,'?(.*?)'?$/
              )
            {
                Print::print_err("Error parsing sequence event entry: $_");
            }

            $year[$cnt]  = $1;
            $mon[$cnt]   = $2;
            $day[$cnt]   = $3;
            $hour[$cnt]  = $4;
            $min[$cnt]   = $5;
            $sec[$cnt]   = $6;
            $host[$cnt]  = $7;
            $vol[$cnt]   = $8;
            $fname[$cnt] = "";
            $fname[$cnt] = $9 if (defined $9);
            $meta[$cnt]  = "";
            $meta[$cnt]  = $10 if (defined $10);
            $data[$cnt]  = "";
            $data[$cnt]  = $11 if (defined $11);
            $type[$cnt]  = $12;
            $note[$cnt]  = $13;

            $entry[$cnt] = $cnt;
            $cnt++;
        }

        close(NOTES);

        # Sort the values by date, source, and then note
        my @sorted = sort {
                 $year[$a] <=> $year[$b]
              or $mon[$a] <=> $mon[$b]
              or $day[$a] <=> $day[$b]
              or $hour[$a] <=> $hour[$b]
              or $min[$a] <=> $min[$b]
              or $sec[$a] <=> $sec[$b]
              or lc($type[$a]) cmp lc($type[$b])
              or lc($note[$a]) cmp lc($note[$b])
        } @entry;

        # Table and header
        print "<table width=800 border=1>\n"
          . "<tr background=\"$::YEL_PIX\">\n"
          . "<th>Date & Time</th>\n"
          . "<th>Source</th>\n"
          . "<th>Event & Note</th></tr>\n";

        # Cycle through the sorted events
        my $row = 0;
        foreach my $i (@sorted) {

            # Alternate row colors
            if (($row % 2) == 0) {
                print "<tr bgcolor=\"$::BACK_COLOR\">\n";
            }
            else {
                print "<tr bgcolor=\"$::BACK_COLOR_TABLE\">\n";
            }

            # Date & Time
            print "<td align=left valign=top>\n"
              . "$::d2m[$mon[$i]]&nbsp;$day[$i],&nbsp;$year[$i]"
              . "&nbsp;$hour[$i]:$min[$i]:$sec[$i]</td>"
              . "<td align=left valign=top>\n";

            # If there is as name, then we will show it
            # @@@ Why does an error message come up from here:
            # Use of uninitialized value in string ne at
            if ($fname[$i] ne "") {

                if (   (exists $vol[$i])
                    && (defined $vol[$i])
                    && ($vol[$i] ne "")
                    && (exists $Caseman::vol2mnt{$vol[$i]})
                    && (exists $meta[$i]))
                {

                    # extract the prepended mnt value
                    my $mnt   = $Caseman::vol2mnt{$vol[$i]};
                    my $fname = "";
                    $fname = $1 if ($fname[$i] =~ /^$mnt\/?(.*)$/);

                    # Check if it is a directory
                    if ($type[$i] eq 'dir') {
                        print "<a href=\"$::PROGNAME?mod=$::MOD_FRAME&"
                          . "submod=$::MOD_FILE&vol=$vol[$i]&$Args::baseargs&meta=$meta[$i]&dir=$fname\" "
                          . "target=\"_blank\">\n"
                          . "<tt>$fname[$i]</tt></a>\n";
                    }
                    else {
                        print "<a href=\"$::PROGNAME?mod=$::MOD_FILE&"
                          . "view=$File::CONT_FR&vol=$vol[$i]&$Args::baseargs&meta=$meta[$i]&dir=$fname\" "
                          . "target=\"_blank\">\n"
                          . "<tt>$fname[$i]</tt></a>\n";
                    }
                }
                else {
                    print "<tt>$fname[$i]</tt>\n";
                }
            }

            # Display the meta value if there was no name
            elsif (($vol[$i] ne "") && (defined $meta[$i]) && ($meta[$i] ne ""))
            {
                my $ftype = $Caseman::vol2ftype{$vol[$i]};

                # Include a link if we can
                if (exists $Caseman::vol2mnt{$vol[$i]}) {
                    print "<a href=\"$::PROGNAME?mod=$::MOD_FRAME&"
                      . "submod=$::MOD_META&vol=$vol[$i]"
                      . "&$Args::baseargs&meta=$meta[$i]\" target=\"_blank\">\n"
                      . "$Fs::meta_str{$ftype}: $meta[$i]</a>\n";
                }
                else {
                    print "$Fs::meta_str{$ftype}: $meta[$i]\n";
                }
            }

            # Otherwise, just give the source type
            else {
                print "$type[$i]\n";
            }
            print "</td>\n";

            # Print the actual note
            $note[$i] = Print::html_encode($note[$i]);
            $note[$i] = "&nbsp;" if ($note[$i] eq "");
            print "<td align=left>$note[$i]</td></tr>\n";

            $row++;
        }

        print "</table>\n";

    }

    # End of if file exists
    else {
        print "No events currently exist<br>\n";
    }

    # Ok and refresh buttons
    print "<p><table width=600>\n"
      . "<tr><td width=300 align=center>\n"
      . "<a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
      . "view=$Caseman::VOL_OPEN&$Args::baseargs\">"
      . "<img border=0 src=\"pict/menu_b_close.jpg\" width=167 height=20 alt=\"close\"></a>\n"
      . "</td><td width=300 align=center>\n"
      . "<a href=\"$::PROGNAME?mod=$::MOD_NOTES&"
      . "view=$Notes::READ_SEQ&$Args::baseargs\">"
      . "<img border=0 src=\"pict/menu_b_ref.jpg\" width=167 height=20 alt=\"refresh\"></a>\n"
      . "</td></tr></table>\n";

    # Manually add a new event
    print "<hr>\n"
      . "<b>Add a New Event</b><br><br>\n"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<textarea rows=10 cols=50 wrap=\"virtual\" name=\"note\">"
      . "</textarea><br>\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_NOTES\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Notes::WRITE_SEQ_MAN\">\n"
      . Args::make_hidden();

    # Month
    print "<table><tr><td align=\"left\">\n";
    print "<p>Date: " . "<select name=\"mon\" size=\"1\">\n";

    my $prev = 1;
    $prev = $Args::args{'mon'}
      if ((exists $Args::args{'mon'}) && ($Args::args{'mon'} =~ /^\d+$/));

    for my $i (1 .. 12) {
        if ($i == $prev) {
            print "<option value=$i selected>$::d2m[$i]</option>\n";
        }
        else {
            print "<option value=\"$i\">$::d2m[$i]</option>\n";
        }
    }

    print "</select>\n";

    # Day
    print "&nbsp;<select name=\"day\" size=\"1\">\n";

    $prev = 1;
    $prev = $Args::args{'day'}
      if ((exists $Args::args{'day'}) && ($Args::args{'day'} =~ /^\d+$/));

    for my $i (1 .. 31) {
        my $dstr = $i;
        $dstr = "0$i" if ($i < 10);

        if ($prev eq $dstr) {
            print "<option value=\"$dstr\" selected>$dstr</option>\n";
        }
        else {
            print "<option value=\"$dstr\">$dstr</option>\n";
        }
    }
    print "</select>\n";

    # Year
    $prev = 1900 + (localtime())[5];
    $prev = $Args::args{'year'}
      if ((exists $Args::args{'year'}) && ($Args::args{'year'} =~ /^\d+$/));

    print "&nbsp;<input type=\"text\" value=\"$prev\" "
      . "name=\"year\" size=\"6\">\n";

    # Hour
    print "&nbsp;&nbsp;<select name=\"hour\" size=\"1\">\n";
    $prev = 0;
    $prev = $Args::args{'hour'}
      if ((exists $Args::args{'hour'}) && ($Args::args{'hour'} =~ /^\d+$/));

    for my $i (0 .. 23) {
        my $hstr = $i;
        $hstr = "0$i" if ($i < 10);

        if ($prev eq $hstr) {
            print "<option value=\"$hstr\" selected>$hstr</option>\n";
        }
        else {
            print "<option value=\"$hstr\">$hstr</option>\n";
        }
    }
    print "</select>\n";

    # Min
    print ":<select name=\"min\" size=\"1\">\n";
    $prev = 0;
    $prev = $Args::args{'min'}
      if ((exists $Args::args{'min'}) && ($Args::args{'min'} =~ /^\d+$/));

    for my $i (0 .. 59) {
        my $mstr = $i;
        $mstr = "0$i" if ($i < 10);
        if ($prev eq $mstr) {
            print "<option value=\"$mstr\" selected>$mstr</option>\n";
        }
        else {
            print "<option value=\"$mstr\">$mstr</option>\n";
        }
    }
    print "</select>\n";

    # Sec
    print ":<select name=\"sec\" size=\"1\">\n";
    $prev = 0;
    $prev = $Args::args{'sec'}
      if ((exists $Args::args{'sec'}) && ($Args::args{'sec'} =~ /^\d+$/));

    for my $i (0 .. 59) {
        my $sstr = $i;
        $sstr = "0$i" if ($i < 10);

        if ($prev eq $sstr) {
            print "<option value=\"$sstr\" selected>$sstr</option>\n";
        }
        else {
            print "<option value=\"$sstr\">$sstr</option>\n";
        }
    }
    print "</select></td></tr>\n" . "<tr><td>&nbsp;</td></tr>\n";

    # Type
    print "<tr><td align=\"left\">Event Source: <select name=\"src\" size=1>\n"
      . "<option value=\"firewall\">firewall</option>\n"
      . "<option value=\"ids\">ids</option>\n"
      . "<option value=\"isp\">isp</option>\n"
      . "<option value=\"log\">log</option>\n"
      . "<option value=\"other\" selected>other</option>\n"
      . "<option value=\"person\">person</option>\n"
      . "</select></td></tr>\n"
      . "<tr><td>&nbsp;</td></tr>\n";

    print
"<tr><td align=\"center\"><input type=\"image\" src=\"pict/menu_b_add.jpg\" "
      . "width=167 height=20 alt=\"Add\" border=\"0\">\n</form></td></tr></table>\n";

    Print::print_html_footer();
    return 0;
}

# Conver the 'image' format to the 'volume' format
sub convert {
    my %img2vol = %{shift()};

    my @invs = Caseman::read_invest();
    if (scalar @invs == 0) {
        push @invs, "unknown";
    }

    foreach $i (@invs) {
        my $notes_file = "$::host_dir" . "$::LOGDIR/$i.notes";

        if ((!(-e "$notes_file")) || (-z "$notes_file")) {
            next;
        }
        Print::log_host_info(
            "Converting format of notes file for $i ($notes_file)");

        open NOTES, "<$notes_file" or die "Can't open log: $notes_file";

        my $notes_file_new = $notes_file . ".new";
        open NOTES_NEW, ">$notes_file_new"
          or die "Can't open writing log: $notes_file_new";

        while (<NOTES>) {

            if (/Image: ($::REG_IMG)\s+(.*)$/) {
                my $img  = $1;
                my $addr = $2;

                unless (exists $img2vol{$img}) {
                    print STDERR
"Error finding image during notes conversion: $img.  Not converting\n";
                    next;
                }
                my $vol = $img2vol{$img};

                # Convert old description to last versions
                $addr =~ s/Inode:/Meta:/;
                print NOTES_NEW "Volume: $vol   $addr\n";
            }
            else {
                print NOTES_NEW $_;
            }
        }

        close(NOTES);
        close(NOTES_NEW);
        rename $notes_file,     $notes_file . ".bak";
        rename $notes_file_new, $notes_file;
    }

    # NOw do sequence notes
    foreach $i (@invs) {
        my $notes_file = "$::host_dir" . "$::LOGDIR/$i.seq.notes";
        if ((!(-e "$notes_file")) || (-z "$notes_file")) {
            next;
        }

        open NOTES, "$notes_file" or die "Can't open log: $notes_file";

        $notes_file_new = $notes_file . ".new";
        open NOTES_NEW, ">$notes_file_new"
          or die "Can't open log for updating: $notes_file_new";

        while (<NOTES>) {

            # No image in entry
            if (/^'\d+','\d+','\d+','\d+','\d+','\d+','$::REG_HOST','','/) {
                print NOTES_NEW $_;
            }
            elsif (
/^('\d+','\d+','\d+','\d+','\d+','\d+','$::REG_HOST',')($::REG_IMG)(','.*)$/
              )
            {
                my $pre  = $1;
                my $img  = $2;
                my $post = $3;
                unless (exists $img2vol{$img}) {
                    print STDERR
"Error finding image during notes conversion: $img.  Not converting\n";
                    next;
                }
                my $vol = $img2vol{$img};
                print NOTES_NEW $pre . $vol . $post . "\n";
            }
            else {
                print NOTES_NEW "$_";
                return;
            }
        }

        close(NOTES);
        close(NOTES_NEW);
        rename $notes_file,     $notes_file . ".bak";
        rename $notes_file_new, $notes_file;
    }

    return 0;
}
