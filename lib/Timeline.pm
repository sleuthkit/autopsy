#
# Timeline functions
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

package Timeline;

use POSIX;    # needed for tzset

# Changing the order of this may affect the main function ordering

$Timeline::BLANK      = 0;
$Timeline::FRAME      = 1;
$Timeline::TABS       = 2;
$Timeline::BODY_ENTER = 3;
$Timeline::BODY_RUN   = 4;
$Timeline::TL_ENTER   = 5;
$Timeline::TL_RUN     = 6;
$Timeline::VIEW_FR    = 7;
$Timeline::VIEW_MENU  = 8;
$Timeline::VIEW_IDX   = 9;
$Timeline::VIEW_SUM   = 10;
$Timeline::VIEW       = 11;

# Types of modes for fname (i.e. can we overwrite it if it exists)
my $FNAME_MODE_INIT = 0;
my $FNAME_MODE_OVER = 1;

sub main {

    return if ($::LIVE == 1);

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $Timeline::FRAME
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    if ($view < $Timeline::VIEW_FR) {
        if ($view == $Timeline::BLANK) {
            return blank();
        }
        elsif ($view == $Timeline::FRAME) {
            return frame();
        }
        elsif ($view == $Timeline::TABS) {
            return tabs();
        }
        elsif ($view == $Timeline::BODY_ENTER) {
            return body_enter();
        }
        elsif ($view == $Timeline::BODY_RUN) {
            return body_run();
        }
        elsif ($view == $Timeline::TL_ENTER) {
            return tl_enter();
        }
        elsif ($view == $Timeline::TL_RUN) {
            return tl_run();
        }
    }
    else {
        if ($view == $Timeline::VIEW_FR) {
            return view_fr();
        }
        elsif ($view == $Timeline::VIEW_MENU) {
            return view_menu();
        }
        elsif ($view == $Timeline::VIEW_IDX) {
            return view_idx();
        }
        elsif ($view == $Timeline::VIEW_SUM) {
            return view_sum();
        }
        elsif ($view == $Timeline::VIEW) {
            return view();
        }
    }
    Print::print_check_err("Invalid Timeline View");
}

# Call the appropriate function based on the value of sort
sub frame {
    Print::print_html_header_frameset(
        "Timeline: $Args::args{'case'}:$Args::args{'host'}");

    print "<frameset rows=\"38,*\">\n";

    my $submod = $Timeline::BLANK;
    $submod = Args::get_submod() if (exists $Args::args{'submod'});

    # Listing
    print "<frame src=\"$::PROGNAME?mod=$::MOD_TL&view=$Timeline::TABS&"
      . "$Args::baseargs&submod=$submod\">\n";

    my $str = "";

    # Contents
    if ($submod == $Timeline::BLANK) {
        print
"<frame src=\"$::PROGNAME?mod=$::MOD_TL&view=$Timeline::BLANK&$Args::baseargs\" "
          . "name=\"content\">\n</frameset>\n";
        return;
    }
    elsif ($submod == $Timeline::TL_ENTER) {
        $str .= "&body=$Args::args{'body'}" if (exists $Args::args{'body'});
    }
    elsif ($submod == $Timeline::VIEW_FR) {
        $str .= "&tl=$Args::args{'tl'}" if (exists $Args::args{'tl'});
    }

    print
"<frame src=\"$::PROGNAME?mod=$::MOD_TL&view=$submod&$Args::baseargs$str\" "
      . "name=\"content\">\n</frameset>\n";

    Print::print_html_footer_frameset();
    return 0;
}

# The tabs / button images in timeline view
sub tabs {
    Args::check_submod();
    Print::print_html_header_tabs("Timeline Mode Tabs");

    my $submod = Args::get_submod();

    print "<center><table width=\"800\" border=\"0\" "
      . "cellspacing=\"0\" cellpadding=\"0\"><tr>\n";

    # Create Datafile
    print "<td align=\"center\" width=174>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_TL&view=$Timeline::FRAME&"
      . "submod=$Timeline::BODY_ENTER&$Args::baseargs\" target=\"_top\">";

    if ($submod == $Timeline::BODY_ENTER) {
        print "<img border=0 "
          . "src=\"pict/tl_t_data_cur.jpg\" "
          . "width=174 height=38 "
          . "alt=\"Create Data File (Current Mode)\"></a>\n";
    }
    else {
        print "<img border=0 "
          . "src=\"pict/tl_t_data_link.jpg\" "
          . "width=174 height=38 "
          . "alt=\"Create Data File\"></a>\n";
    }

    print "</td>\n"
      . "<td align=\"center\" width=174>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_TL&view=$Timeline::FRAME&"
      . "submod=$Timeline::TL_ENTER&$Args::baseargs\" "
      . "target=\"_top\">";

    # Create Timeline
    if ($submod == $Timeline::TL_ENTER) {
        print "<img border=0 "
          . "src=\"pict/tl_t_tl_cur.jpg\" "
          . "width=174 height=38 "
          . "alt=\"Create Timeline (Current Mode)\"></a>\n";
    }
    else {
        print "<img border=0 "
          . "src=\"pict/tl_t_tl_link.jpg\" "
          . "width=174 height=38 "
          . "alt=\"Create Timeline\"></a>\n";
    }
    print "</td>\n"
      . "<td align=\"center\" width=174>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_TL&view=$Timeline::FRAME&"
      . "submod=$Timeline::VIEW_MENU&$Args::baseargs\" "
      . "target=\"_top\">";

    # View Timeline
    if (($submod == $Timeline::VIEW_FR) || ($submod == $Timeline::VIEW_MENU)) {
        print "<img border=0 "
          . "src=\"pict/tl_t_view_cur.jpg\" "
          . "width=174 height=38 "
          . "alt=\"View Timeline (Current Mode)\"></a>\n";
    }
    else {
        print "<img border=0 "
          . "src=\"pict/tl_t_view_link.jpg\" "
          . "width=174 height=38 "
          . "alt=\"View Timeline\"></a>\n";
    }

    # Notes
    print "</td>\n" . "<td align=\"center\" width=174>";
    if ($::USE_NOTES == 1) {
        print
"<a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::READ_NORM&$Args::baseargs_novol\" "
          . "target=\"_blank\">"
          . "<img border=0 "
          . "src=\"pict/tl_t_notes_link.jpg\" "
          . "width=174 height=38 "
          . "alt=\"View Notes\"></a></td>\n";
    }
    else {
        print "<img border=0 "
          . "src=\"pict/tl_t_notes_org.jpg\" "
          . "width=174 height=38 "
          . "alt=\"View Notes\"></a></td>\n";
    }

    # Help - set to current submod
    print "<td align=\"center\" width=52>"
      . "<a href=\"$::HELP_URL\" target=\"_blank\">"
      . "<img border=0 src=\"pict/tab_help.jpg\" width=52 "
      . "alt=\"Help\"></a></td>\n";

    # Close
    print "<td align=\"center\" width=52>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
      . "view=$Caseman::VOL_OPEN&$Args::baseargs\" target=\"_top\">"
      . "<img border=0 src=\"pict/tab_close.jpg\" width=52 "
      . "alt=\"Exit to Host Manager\"></a></td>\n"
      . "</tr></table>\n";

    Print::print_html_footer_tabs();
    return 0;
}

sub body_enter {
    Print::print_html_header("Enter Data to Make Body File");

    my $i;
    my %mnt2img;

    # Cycle through each image we read from fsmorgue
    foreach $i (keys %Caseman::vol2mnt) {
        next
          unless ($Caseman::vol2cat{$i} eq "part");
        next
          if ( ($Caseman::vol2ftype{$i} eq "swap")
            || ($Caseman::vol2ftype{$i} eq "raw"));
        $mnt2vol{"$Caseman::vol2mnt{$i}--$i"} = $i;
    }

# sort via parent volume, then starting location, and then mount point (which includes the name)
    my @mnt = sort {
        ($Caseman::vol2par{$mnt2vol{$a}} cmp $Caseman::vol2par{$mnt2vol{$b}})
          or ($Caseman::vol2start{$mnt2vol{$a}} <=>
            $Caseman::vol2start{$mnt2vol{$b}})
          or (lc($a) cmp lc($b))
    } keys %mnt2vol;

    print "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::BODY_RUN\">\n"
      . Args::make_hidden()
      . "<p>Here  we will process the file system images, collect the temporal data, and save the data to a single file."
      . "<p>1.  Select one or more of the following images to collect data from:\n"
      . "<table cellspacing=\"8\" cellpadding=\"2\">";

    for (my $i = 0; $i <= $#mnt; $i++) {
        my $vol = $mnt2vol{$mnt[$i]};

        print "<tr><td><input type=\"checkbox\" name=\"$vol\" value=\"1\">"
          . "</td><td><tt>$Caseman::vol2mnt{$vol}</tt></td><td><tt>$Caseman::vol2sname{$vol}</tt></td>"
          . "<td>$Caseman::vol2ftype{$vol}</td>\n";
    }

    print "</table><p>2.  Select the data types to gather:<br>\n"
      . "<table cellspacing=\"8\" cellpadding=\"2\"><tr>"
      . "<td><input type=\"checkbox\" name=\"al_file\" value=\"1\" CHECKED>"
      . "</td><td>Allocated Files</td>"
      . "<td><input type=\"checkbox\" name=\"unal_file\" value=\"1\" CHECKED>"
      . "</td><td>Unallocated Files</td>"
      . "</tr></table>\n"
      . "<p>3.   Enter name of output file (<tt>body</tt>):<br>"
      . "<tt>$::DATADIR/</tt>"
      . "<input type=\"text\" name=\"fname\" value=\"body\">\n"
      . "<input type=\"hidden\" name=\"fname_mode\" value=\"$FNAME_MODE_INIT\">\n"
      . "<p>4.   Generate MD5 Value? "
      . "<input type=\"checkbox\" name=\"md5\" value=\"1\" CHECKED>";

    print "<p><input type=\"image\" src=\"pict/but_ok.jpg\" "
      . "width=43 height=20 alt=\"Ok\" border=\"0\"></form>\n";

    Print::print_html_footer();
    return 0;
}

sub body_run {
    Args::check_fname();
    Args::check_fname_mode();
    Print::print_html_header("Make Body File");

    my $fname_rel = Args::get_fname_rel();
    my $fname     = Args::get_fname();

    my $fname_mode = $Args::args{'fname_mode'};

    if ((-e "$fname") && ($FNAME_MODE_INIT == $fname_mode)) {
        print "File Already Exists: $fname_rel\n";

        my $hidden = Args::make_hidden();

        $hidden .=
            "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::BODY_RUN\">\n";

        my $i;
        foreach $i (%Caseman::vol2mnt) {
            $hidden .= "<input type=\"hidden\" name=\"$i\" value=\"1\">\n"
              if (exists $Args::args{$i});
        }

        $hidden .=
            "<input type=\"hidden\" name=\"al_file\" "
          . "value=\"$Args::args{'al_file'}\">\n"
          if (exists $Args::args{'al_file'});
        $hidden .=
            "<input type=\"hidden\" name=\"unal_file\" "
          . "value=\"$Args::args{'unal_file'}\">\n"
          if (exists $Args::args{'unal_file'});
        $hidden .=
            "<input type=\"hidden\" name=\"md5\" "
          . "value=\"$Args::args{'md5'}\">\n"
          if (exists $Args::args{'md5'});

        # Make a new name
        print "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "New Name: <input type=\"text\" name=\"fname\">"
          . "<table cellspacing=\"30\" cellpadding=\"2\"><tr><td>"
          . "<input type=\"hidden\" name=\"fname_mode\" value=\"$FNAME_MODE_INIT\">\n"
          . "$hidden"
          . "<input type=\"image\" src=\"pict/but_new_name.jpg\" "
          . "width=79 height=20 alt=\"Use New Name\" border=\"0\">\n"
          . "</form></td>\n";

        # Overwrite it
        print "<td><form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"image\" src=\"pict/but_replace.jpg\" "
          . "width=66 height=20 alt=\"Replace\" border=\"0\"><br>\n"
          . "<input type=\"hidden\" name=\"fname\" value=\"$Args::args{'fname'}\">\n"
          . "<input type=\"hidden\" name=\"fname_mode\" value=\"$FNAME_MODE_OVER\">\n"
          . "$hidden"
          . "</form></td></tr></table>";

        return 0;
    }

    # we will be appending to the file so we should del it now
    if (-e "$fname") {
        unlink($fname);
    }

    my $log_files = "";
    my $log_type  = "";

    # What kind of data are we collecting?
    my $al_file = 0;
    if (exists $Args::args{'al_file'}) {
        $al_file = $Args::args{'al_file'};
        $log_type .= "Allocated Files";
    }

    my $unal_file = 0;
    if (exists $Args::args{'unal_file'}) {
        $unal_file = $Args::args{'unal_file'};
        $log_type .= ", " if ($log_type ne "");
        $log_type .= "Unallocated Files";
    }

    if (($unal_file == 0) && ($al_file == 0)) {
        print
          "No data types were selected.  You must select at least one.<br>\n";
        return 1;
    }

    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    my $i;
    my $found = 0;
    local *OUT;

    # Analyze each image - the image names are passed as an argument
    foreach $i (keys %Caseman::vol2mnt) {
        if (exists $Args::args{$i}) {

            $found = 1;
            my $ftype   = $Caseman::vol2ftype{$i};
            my $img     = $Caseman::vol2path{$i};
            my $offset  = $Caseman::vol2start{$i};
            my $imgtype = $Caseman::vol2itype{$i};
            my $mnt     = $Caseman::vol2mnt{$i};

            $log_files .= ", " if ($log_files ne "");
            $log_files .= "$i";

            if (($al_file) && ($unal_file)) {
                print "Running <tt>fls -r -m</tt> on <tt>$i</tt><br>\n";
                Exec::exec_pipe(*OUT,
"'$::TSKDIR/fls' $tz -s $Caseman::ts -m '$mnt' -f $ftype -r -o $offset -i $imgtype $img >> '$fname'"
                );
                print "$_<br>\n" while ($_ = Exec::read_pipe_line(*OUT));
                close(OUT);
            }
            elsif ($al_file) {
                print "Running <tt>fls -ru -m</tt> on <tt>$i</tt><br>\n";
                Exec::exec_pipe(*OUT,
"'$::TSKDIR/fls' $tz -s $Caseman::ts -m '$mnt' -f $ftype -ru -o $offset -i $imgtype $img >> '$fname'"
                );
                print "$_<br>\n" while ($_ = Exec::read_pipe_line(*OUT));
                close(OUT);
            }
            elsif ($unal_file) {
                print "Running <tt>fls -rd -m</tt> on <tt>$i</tt><br>\n";
                Exec::exec_pipe(*OUT,
"'$::TSKDIR/fls' $tz -s $Caseman::ts -m '$mnt' -f $ftype -rd -o $offset -i $imgtype $img >> '$fname'"
                );
                print "$_<br>\n" while ($_ = Exec::read_pipe_line(*OUT));
                close(OUT);
            }
        }
    }

    unless ($found) {
        print
"No images were given for analysis.  At least one must be selected.<br>\n";
        return 1;
    }

    Print::log_host_inv(
        "Saving timeline data for $log_type for $log_files to $fname_rel");

    # append to  host config
    my $bod_vol = Caseman::add_vol_host_config("body", $fname_rel);
    $Caseman::vol2cat{$bod_vol}   = "timeline";
    $Caseman::vol2ftype{$bod_vol} = "body";
    $Caseman::vol2itype{$bod_vol} = "raw";
    $Caseman::vol2path{$bod_vol}  = $fname;
    $Caseman::vol2start{$bod_vol} = 0;
    $Caseman::vol2end{$bod_vol}   = 0;
    $Caseman::vol2sname{$bod_vol} = $fname_rel;

    print "<br>Body file saved to <tt>$fname</tt><br><br>\n"
      . "Entry added to host config file<br><br>\n";

    # Calculate MD5
    if ((exists $Args::args{'md5'}) && ($Args::args{'md5'} == 1)) {
        print "Calculating MD5 Value<br><br>\n";
        my $m = Hash::int_create_wrap($bod_vol);
        print "MD5 Value: <tt>$m</tt><br><br>\n";
    }

    print "<p>The next step is to sort the data into a timeline."
      . "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
      . "<input type=\"hidden\" name=\"body\" value=\"$bod_vol\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::FRAME\">\n"
      . "<input type=\"hidden\" name=\"submod\" value=\"$Timeline::TL_ENTER\">\n"
      . Args::make_hidden()
      . "<input type=\"image\" src=\"pict/but_ok.jpg\" "
      . "width=43 height=20 alt=\"Ok\" border=\"0\">\n</form>\n";

    Print::print_html_footer();
    return 0;
}

my $OTYPE_NORM   = 1;
my $OTYPE_HOURLY = 2;
my $OTYPE_DAILY  = 3;

sub tl_enter {
    Print::print_html_header("Enter data for timeline");

    my @body;

    # Find the body files if we will be looking for them
    unless ((exists $Args::args{'body'})
        && (exists $Caseman::vol2cat{$Args::args{'body'}}))
    {
        foreach my $k (keys %Caseman::vol2cat) {
            if (   ($Caseman::vol2cat{$k} eq "timeline")
                && ($Caseman::vol2ftype{$k} eq "body"))
            {
                push @body, $k;
            }
        }

        if (scalar(@body) == 0) {
            print "There are currently no <tt>body</tt> files "
              . "for this host.<br>You must create the intermediate "
              . "data file before you can perform this step<br>\n"
              . "<p><a target=\"_top\"  "
              . "href=\"$::PROGNAME?$Args::baseargs&"
              . "mod=$::MOD_TL&view=$Timeline::FRAME&"
              . "submod=$Timeline::BODY_ENTER\">"
              . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
              . "width=\"43\" height=20 border=\"0\">"
              . "</a>\n";
            return 1;
        }
    }
    print "Now we will sort the data and save it to a timeline.<p>\n"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::TL_RUN\">\n"
      . Args::make_hidden()
      . "1.  Select the data input file (<tt>body</tt>):\n"
      . "<table cellspacing=\"0\" cellpadding=\"2\">";

    # if the body file was specified then just print it
    if (exists $Args::args{'body'}) {
        print "<tr><td><input type=\"radio\" name=\"body\" "
          . "value=\"$Args::args{'body'}\" CHECKED>"
          . "</td><td>$Caseman::vol2sname{$Args::args{'body'}}</td>\n";
    }
    else {
        my @body_sort = sort { lc($a) cmp lc($b) } @body;
        my $chk = " CHECKED";
        for (my $i = 0; $i <= $#body_sort; $i++) {
            print "<tr><td><input type=\"radio\" name=\"body\" "
              . "value=\"$body_sort[$i]\" $chk></td><td>$Caseman::vol2sname{$body_sort[$i]}</td>\n";
            $chk = "";
        }
    }

    my $cur_mon  = 1 +    (localtime())[4];
    my $cur_year = 1900 + (localtime())[5];

    # STARTING DATE
    print "</table>\n"
      . "<p>2.  Enter the starting date:<br>\n"
      . "None: <input type=\"radio\" name=\"st_none\" value=\"1\" CHECKED><br>"
      . "Specify: <input type=\"radio\" name=\"st_none\" value=\"0\">  "
      . "<select name=\"st_mon\" size=\"1\">";

    for my $i (1 .. 12) {
        if ($i == $cur_mon) {
            print "<option selected value=\"$i\">$::d2m[$i]</option>\n";
        }
        else {
            print "<option value=\"$i\">$::d2m[$i]</option>\n";
        }
    }

    print "</select>"
      . "<select name=\"st_day\" size=\"1\">"
      . "<option selected>1</option>\n";

    for my $i (2 .. 31) {
        print "<option value=\"$i\">$i</option>\n";
    }

    print "</select>"
      . "<input type=\"text\" name=\"st_year\" size=\"6\" value=\"$cur_year\">\n";

    # END DATE
    print "<p>3.   Enter the ending date:<br>\n"
      . "None: <input type=\"radio\" name=\"end_none\" value=\"1\" CHECKED><br>\n"
      . "Specify: <input type=\"radio\" name=\"end_none\" value=\"0\">  \n"
      . "<select name=\"end_mon\" size=\"1\">\n";

    for my $i (1 .. 12) {
        if ($i == $cur_mon) {
            print "<option selected value=\"$i\">$::d2m[$i]</option>\n";
        }
        else {
            print "<option value=\"$i\">$::d2m[$i]</option>\n";
        }
    }

    print "</select>\n"
      . "<select name=\"end_day\" size=\"1\">\n"
      . "<option selected value=\"1\">1</option>\n";

    for my $i (2 .. 31) {
        print "<option value=\"$i\">$i</option>\n";
    }

    print "</select>"
      . "<input type=\"text\" name=\"end_year\" size=\"6\" value=\"$cur_year\">\n";

    # FILE NAME
    print "<p>4.   Enter the file name to save as:<br>"
      . "<tt>$::DATADIR/</tt><input type=\"text\" size=36 name=\"fname\" value=\"timeline.txt\"><br>\n"
      . "<input type=\"hidden\" name=\"fname_mode\" value=\"$FNAME_MODE_INIT\">\n";

    # Get only the UNIX images - since only they have /etc/passwd and group
    my @unix_imgs;
    my $root_vol = "";
    foreach my $i (keys %Caseman::vol2ftype) {
        my $f = $Caseman::vol2ftype{$i};

        next
          unless (($f =~ /^ext/)
            || ($f =~ /^ufs/)
            || ($f =~ /^linux/)
            || ($f =~ /bsd$/)
            || ($f =~ /^solaris$/)
            || ($f =~ /^bsdi$/));

        push @unix_vols, $i;

        # Keep a reference to an image with '/' as the mounting point
        $root_vol = $i
          if ($Caseman::vol2mnt{$i} eq '/');
    }

    my $cnt = 5;
    if (scalar @unix_vols > 0) {

        print
"<p>$cnt.  Select the UNIX image that contains the /etc/passwd and /etc/group files:<br>\n";
        $cnt++;

        print "<select name=\"pw_vol\">\n";

        # If we did not find an image that has a / mounting point, then
        # we will use none as the default.
        if ($root_vol eq "") {
            print "<option value=\"\" selected>None</option>\n";
        }
        else {
            print "<option value=\"\">None</option>\n";
        }

        foreach my $vol (@unix_vols) {
            if ($root_vol eq $vol) {
                print
"<option value=\"$vol\" selected>$Caseman::vol2sname{$vol} ($Caseman::vol2mnt{$vol})"
                  . "</option>\n";
            }
            else {
                print
"<option value=\"$vol\">$Caseman::vol2sname{$vol} ($Caseman::vol2mnt{$vol})</option>\n";
            }
        }

        print "</select>\n";
    }

    print "<p>$cnt. Choose the output format:<br>\n";
    $cnt++;
    print
"&nbsp;&nbsp;<input type=\"radio\" name=\"sort\" value=\"$OTYPE_NORM\" CHECKED>Tabulated (normal)<br>\n"
      . "&nbsp;&nbsp;<input type=\"radio\" name=\"sort\" value=\"$OTYPE_HOURLY\">Comma delimited with hourly summary<br>\n"
      . "&nbsp;&nbsp;<input type=\"radio\" name=\"sort\" value=\"$OTYPE_DAILY\">Comma delimited with daily summary<br>\n";

    print "<p>$cnt.  Generate MD5 Value? ";
    $cnt++;

    print "<input type=\"checkbox\" name=\"md5\" value=\"1\" CHECKED>\n";

    # Create Button
    print "<p><input type=\"image\" src=\"pict/but_ok.jpg\" "
      . "width=43 height=20 alt=\"Ok\" border=\"0\">\n</form>\n";

    Print::print_html_footer();
    return 0;
}

sub tl_run {
    Args::check_fname();
    Args::check_body();
    Args::check_sort();

    Print::print_html_header("Make Timeline");

    my $body      = Args::get_body();
    my $fname     = Args::get_fname();
    my $fname_rel = Args::get_fname_rel();
    my $otype     = Args::get_sort();

    my $fname_mode = $Args::args{'fname_mode'};

    if ((-e "$fname") && ($FNAME_MODE_INIT == $fname_mode)) {
        print "File Already Exists: <tt>$fname_rel</tt><br>\n";

        my $hidden =
          "<input type=\"hidden\" name=\"body\" value=\"$Args::args{'body'}\">"
          . Args::make_hidden();

        $hidden .=
            "<input type=\"hidden\" name=\"st_none\" "
          . "value=\"$Args::args{'st_none'}\">\n"
          if (exists $Args::args{'st_none'});
        $hidden .=
            "<input type=\"hidden\" name=\"st_year\" "
          . "value=\"$Args::args{'st_year'}\">\n"
          if (exists $Args::args{'st_year'});
        $hidden .=
            "<input type=\"hidden\" name=\"st_day\" "
          . "value=\"$Args::args{'st_day'}\">\n"
          if (exists $Args::args{'st_day'});
        $hidden .=
            "<input type=\"hidden\" name=\"st_mon\" "
          . "value=\"$Args::args{'st_mon'}\">\n"
          if (exists $Args::args{'st_mon'});
        $hidden .=
            "<input type=\"hidden\" name=\"end_none\" "
          . "value=\"$Args::args{'end_none'}\">\n"
          if (exists $Args::args{'end_none'});
        $hidden .=
            "<input type=\"hidden\" name=\"end_year\" "
          . "value=\"$Args::args{'end_year'}\">\n"
          if (exists $Args::args{'end_year'});
        $hidden .=
            "<input type=\"hidden\" name=\"end_day\" "
          . "value=\"$Args::args{'end_day'}\">\n"
          if (exists $Args::args{'end_day'});
        $hidden .=
            "<input type=\"hidden\" name=\"end_mon\" "
          . "value=\"$Args::args{'end_mon'}\">\n"
          if (exists $Args::args{'end_mon'});
        $hidden .=
            "<input type=\"hidden\" name=\"tz\" "
          . "value=\"$Args::args{'tz'}\">\n"
          if (exists $Args::args{'tz'});
        $hidden .=
            "<input type=\"hidden\" name=\"pw_vol\" "
          . "value=\"$Args::args{'pw_vol'}\">\n"
          if (exists $Args::args{'pw_vol'});
        $hidden .=
            "<input type=\"hidden\" name=\"md5\" "
          . "value=\"$Args::args{'md5'}\">\n"
          if (exists $Args::args{'md5'});
        $hidden .=
            "<input type=\"hidden\" name=\"sort\" "
          . "value=\"$Args::args{'sort'}\">\n"
          if (exists $Args::args{'sort'});

        # Make a new name
        print "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "New Name: <input type=\"text\" name=\"fname\">"
          . "<table cellspacing=\"30\" cellpadding=\"2\"><tr><td>"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::TL_RUN\">\n"
          . "<input type=\"hidden\" name=\"fname_mode\" value=\"$FNAME_MODE_INIT\">\n"
          . "$hidden\n"
          . "<input type=\"image\" src=\"pict/but_new_name.jpg\" "
          . "width=79 height=20 alt=\"Use New Name\" border=\"0\">\n"
          . "</form></td>\n";

        # Overwrite it
        print "<td><form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"image\" src=\"pict/but_replace.jpg\" "
          . "width=66 height=20 alt=\"Replace\" border=\"0\"><br>\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::TL_RUN\">\n"
          . "<input type=\"hidden\" name=\"fname\" value=\"$Args::args{'fname'}\">\n"
          . "<input type=\"hidden\" name=\"fname_mode\" value=\"$FNAME_MODE_OVER\"
>\n" . "$hidden\n" . "</form></td></tr></table>";

        return 0;
    }

    my $mon;
    my $day;
    my $year;

    my $date = "";

    # Get the start date
    unless ((exists $Args::args{'st_none'}) && ($Args::args{'st_none'} == 1)) {

        if (exists $Args::args{'st_mon'}) {
            Args::check_st_mon();
            $mon = Args::get_st_mon();
            if (($mon < 1) || ($mon > 12)) {
                print("Invalid starting month\n");
                return 1;
            }
            if ($mon < 10) {
                $mon = "0" . $mon;
            }
        }
        if (exists $Args::args{'st_year'}) {
            Args::check_st_year();
            $year = Args::get_st_year();
            if (($year < 1970) || ($year > 2020)) {
                print("Invalid starting year\n");
                return 1;
            }
        }
        if (   (exists $Args::args{'st_day'})
            && ($Args::args{'st_day'} =~ /^(\d\d?)$/))
        {
            $day = $1;
            if (($day < 1) || ($day > 31)) {
                print("Invalid starting day\n");
                return 1;
            }
            if ($day < 10) {
                $day = "0" . $day;
            }
        }
        else {
            print("Invalid start day\n");
            return 1;
        }

        $date = "$year-$mon-$day";
    }

    unless ((exists $Args::args{'end_none'}) && ($Args::args{'end_none'} == 1))
    {

        if ($date eq "") {
            print "Begin date must be given if ending date is given<br>";
            return 1;
        }

        if (   (exists $Args::args{'end_mon'})
            && ($Args::args{'end_mon'} =~ /^(\d\d?)$/))
        {
            $mon = $1;
            if (($mon < 1) || ($mon > 12)) {
                print("Invalid end month\n");
                return 1;
            }
            if ($mon < 10) {
                $mon = "0" . $mon;
            }
        }
        else {
            print("Invalid end month\n");
            return 1;
        }
        if (   (exists $Args::args{'end_year'})
            && ($Args::args{'end_year'} =~ /^(\d\d\d\d)$/))
        {
            $year = $1;
            if (($year < 1970) || ($year > 2020)) {
                print("Invalid ending year\n");
                return 1;
            }

        }
        else {
            print("Invalid end year\n");
            return 1;
        }
        if (   (exists $Args::args{'end_day'})
            && ($Args::args{'end_day'} =~ /^(\d\d?)$/))
        {
            $day = $1;
            if (($day < 1) || ($day > 31)) {
                print("Invalid end day\n");
                return 1;
            }
            if ($day < 10) {
                $day = "0" . $day;
            }
        }
        else {
            print("Invalid end day\n");
            return 1;
        }

        $date .= "..$year-$mon-$day";
    }

    # temp strings for the password and group files
    my $pw_tmp   = "";
    my $gr_tmp   = "";
    my $mac_args = "";
    my $log      = "";

    local *OUT;

    # Password and Group Files
    if ((exists $Args::args{'pw_vol'}) && ($Args::args{'pw_vol'} ne "")) {
        Args::check_vol('pw_vol');
        my $pw_vol = Args::get_vol('pw_vol');

        my $ftype   = $Caseman::vol2ftype{$pw_vol};
        my $img     = $Caseman::vol2path{$pw_vol};
        my $offset  = $Caseman::vol2start{$pw_vol};
        my $imgtype = $Caseman::vol2itype{$pw_vol};

        $log .= "Password & Group File ($pw_vol) ";

        # Get the passwd file meta and copy the file
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/ifind' -f $ftype -n 'etc/passwd' -o $offset -i $imgtype $img"
        );
        my $pwi = Exec::read_pipe_line(*OUT);
        close(OUT);

        $pwi = "Error getting meta for passwd"
          if ((!defined $pwi) || ($pwi eq ""));

        # Do the Taint Checking
        if ($pwi =~ /^($::REG_META)$/) {
            $pwi = $1;

            $log .= "Password Meta Address ($pwi) ";

            # Find a temp name that we can call it
            my $i;
            for ($i = 0;; $i++) {
                unless (-e "$fname.pw-$i") {
                    $pw_tmp = "$fname.pw-$i";
                    last;
                }
            }

            Exec::exec_sys(
"'$::TSKDIR/icat' -f $ftype -o $offset -i $imgtype $img $pwi > '$pw_tmp'"
            );
            $mac_args .= " -p \'$pw_tmp\' ";

        }
        else {
            print(
"Error finding /etc/passwd meta in $Caseman::vol2sname{$pw_vol} ($pwi)<br>"
            );
            Print::log_host_inv(
"$Caseman::vol2sname{$pw_vol}: /etc/passwd file not found for timeline"
            );
        }

        # Get the group file meta and copy the file
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/ifind' -f $ftype -n 'etc/group' -o $offset -i $imgtype $img"
        );
        my $gri = Exec::read_pipe_line(*OUT);
        close(OUT);

        $gri = "Error getting meta for group"
          if ((!defined $gri) || ($gri eq ""));

        # Do the Taint Checking
        if ($gri =~ /^($::REG_META)$/) {
            $gri = $1;

            $log .= "Group Meta Address ($gri) ";

            # Find a temp name that we can call it
            my $i;
            for ($i = 0;; $i++) {
                unless (-e "$fname.gr-$i") {
                    $gr_tmp = "$fname.gr-$i";
                    last;
                }
            }
            Exec::exec_sys(
"'$::TSKDIR/icat' -f $ftype -o $offset -i $imgtype $img $gri > '$gr_tmp'"
            );
            $mac_args .= " -g \'$gr_tmp\' ";
        }
        else {
            print(
"Error finding /etc/group meta in $Caseman::vol2sname{$pw_vol} ($gri)<br>"
            );
            Print::log_host_inv(
"$Caseman::vol2sname{$pw_vol}: /etc/group file not found for timeline"
            );
        }
    }

    if ($date eq "") {
        print
          "Creating Timeline using all dates (Time Zone: $Caseman::tz)<br>\n";
        Print::log_host_inv(
"$Caseman::vol2sname{$body}: Creating timeline using all dates (TZ: $Caseman::tz) ${log}to $fname_rel"
        );
    }
    else {
        print "Creating Timeline for $date (Time Zone: $Caseman::tz)<br>\n";
        Print::log_host_inv(
"$Caseman::vol2sname{$body}: Creating timeline for $date (TZ: $Caseman::tz) ${log}to $fname_rel"
        );
    }

    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    # mactime needs the path to run the 'date' command
    $ENV{PATH} = "/bin:/usr/bin";
    local *OUT;
    if ($otype == $OTYPE_NORM) {
        Exec::exec_pipe(*OUT,
"LANG=C LC_ALL=C '$::TSKDIR/mactime' -b $Caseman::vol2path{$body} $tz -i day '${fname}.sum' $mac_args $date > '$fname'"
        );
    }
    elsif ($otype == $OTYPE_HOURLY) {
        Exec::exec_pipe(*OUT,
"LANG=C LC_ALL=C '$::TSKDIR/mactime' -b $Caseman::vol2path{$body} $tz  -d -i hour '${fname}.sum' $mac_args $date > '$fname'"
        );
    }
    elsif ($otype == $OTYPE_DAILY) {
        Exec::exec_pipe(*OUT,
"LANG=C LC_ALL=C '$::TSKDIR/mactime' -b $Caseman::vol2path{$body} $tz -d -i day '${fname}.sum' $mac_args $date > '$fname'"
        );
    }
    else {
        Print::print_err("Unknown output type");
    }

    print "$_<br>\n" while ($_ = Exec::read_pipe_line(*OUT));
    close(OUT);
    $ENV{PATH} = "";

    # Remove the password and group files
    unlink("$pw_tmp") if ($pw_tmp ne "");
    unlink("$gr_tmp") if ($gr_tmp ne "");

    print "<br>Timeline saved to <tt>$fname</tt><br><br>\n";

    # append to fsmorgue if a normal timeline
    if ($otype == $OTYPE_NORM) {
        my $tl_vol = Caseman::add_vol_host_config("timeline", $fname_rel);
        print "Entry added to host config file<br><br>\n";

        $Caseman::vol2cat{$tl_vol}   = "timeline";
        $Caseman::vol2ftype{$tl_vol} = "timeline";
        $Caseman::vol2itype{$tl_vol} = "raw";
        $Caseman::vol2path{$tl_vol}  = "$fname";
        $Caseman::vol2start{$tl_vol} = 0;
        $Caseman::vol2end{$tl_vol}   = 0;
        $Caseman::vol2sname{$tl_vol} = $fname_rel;

        # Calculate MD5
        if ((exists $Args::args{'md5'}) && ($Args::args{'md5'} == 1)) {
            print "Calculating MD5 Value<br><br>\n";
            my $m = Hash::int_create_wrap($tl_vol);
            print "MD5 Value: <tt>$m</tt><br><br>\n";
        }

        print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
          . "<input type=\"hidden\" name=\"tl\" value=\"$tl_vol\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::FRAME\">\n"
          . "<input type=\"hidden\" name=\"submod\" value=\"$Timeline::VIEW_FR\">\n"
          . Args::make_hidden()
          . "<input type=\"image\" src=\"pict/but_ok.jpg\" "
          . "width=43 height=20 alt=\"Ok\" border=\"0\">\n</form>\n"
          . "(NOTE: It is easier to view the timeline in a text editor than here)";
    }
    else {
        print
          "Comma delimited files cannot be viewed from within Autopsy.<br>\n"
          . "Open it in a spreadsheet or other data processing tool.<br>\n";
    }
    Print::print_html_footer();
    return 0;
}

sub view_menu {
    Print::print_html_header("View Timeline Menu");

    my @tl;

    # Find the timelines in the images hash
    foreach my $k (keys %Caseman::vol2cat) {
        if (   ($Caseman::vol2cat{$k} eq "timeline")
            && ($Caseman::vol2ftype{$k} eq "timeline"))
        {
            push @tl, $k;
        }
    }

    if (scalar(@tl) == 0) {
        print "There are currently no timeline files in the "
          . "host config file.<br>One must first be created before you "
          . "can view it<br>\n";

        print "<p><a target=\"_top\"  "
          . "href=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_TL&view=$Timeline::FRAME&"
          . "submod=$Timeline::TL_ENTER\">"
          . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
          . "width=\"43\" height=20 border=\"0\">"
          . "</a>\n";

        return 1;
    }

    print "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::VIEW_FR\">\n"
      . Args::make_hidden()
      . "1.  Select the timeline file:\n"
      . "<table cellspacing=\"0\" cellpadding=\"2\">\n";

    my @tl_sort = sort { lc($a) cmp lc($b) } @tl;
    for (my $i = 0; $i <= $#tl_sort; $i++) {
        print "<tr><td><input type=\"radio\" name=\"tl\" "
          . "value=\"$tl_sort[$i]\"></td><td>$Caseman::vol2sname{$tl_sort[$i]}</td>\n";
    }

    print "</table>\n"
      . "<input type=\"image\" src=\"pict/but_ok.jpg\" "
      . "width=43 height=20 alt=\"Ok\" border=\"0\">\n</form>\n";

    Print::print_html_footer();
    return 0;
}

sub view_fr {
    Args::check_tl();

    Print::print_html_header_frameset("");
    my $tl_vol = Args::get_tl();
    my $tl     = $Caseman::vol2path{$tl_vol};
    my $url    = "";

    unless (exists $Args::args{'st_mon'}) {

        unless (open(TL, $tl)) {
            print("Error opening $tl");
            return (1);
        }

        my $beg_mon;
        my $beg_year;
        my $cnt = 0;
        while (<TL>) {
            $cnt++;
            if (/^(?:\w\w\w )?(\w\w\w)\s+\d\d\s+(\d\d\d\d)\s+\d\d:\d\d:\d\d/) {
                $url = "tl=$tl_vol&st_mon=$::m2d{$1}&st_year=$2";

            }
            last;
        }
        close(TL);

        if ($cnt == 0) {
            print "Empty timeline<br>\n";
            return 1;
        }
        if ($url eq "") {
            print "Invalid Timeline<br>\n";
            return 1;
        }
    }
    else {
        $url =
            "tl=$tl_vol&st_mon=$Args::enc_args{'st_mon'}&"
          . "st_year=$Args::enc_args{'st_year'}";
    }

    print "<frameset rows=\"65,*\">\n"
      . "<frame src=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_TL&"
      . "view=$Timeline::VIEW_IDX&$url\">\n"
      . "<frame src=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_TL&"
      . "view=$Timeline::VIEW&$url\">\n</frameset>";

    Print::print_html_footer();
    return 0;
}

sub view_idx {
    Args::check_st_mon();
    Args::check_st_year();
    Args::check_tl();

    Print::print_html_header("View Timeline Index");

    my $mon    = Args::get_st_mon();
    my $year   = Args::get_st_year();
    my $tl_vol = Args::get_tl();
    my $tl     = $Caseman::vol2path{$tl_vol};

    print "<center>";
    my $url =
        "$::PROGNAME?$Args::baseargs&mod=$::MOD_TL&view=$Timeline::VIEW_FR&"
      . "tl=$tl_vol";

    # Next and Previous pointers
    my $pyear = $year;
    my $pmon  = $mon - 1;
    if ($pmon == 0) {
        $pmon = 12;
        $pyear--;
    }
    print "<table cellspacing=\"0\" cellpadding=\"2\">\n"
      . "<tr><td align=\"center\">"
      . "<a href=\"$url&st_mon=$pmon&st_year=$pyear\" target=\"_parent\">"
      . "&lt;- $::d2m[$pmon] $pyear</a></td>\n"
      . "<td>&nbsp;</td>\n";

    if (-e "${tl}.sum") {
        print "<td><a href=\"$::PROGNAME?$Args::baseargs&"
          . "mod=$::MOD_TL&view=$Timeline::VIEW_SUM&"
          . "tl=$tl_vol\" target=\"_parent\">"
          . "Summary</td>\n"
          . "<td>&nbsp;</td>\n";
    }

    my $nyear = $year;
    my $nmon  = $mon + 1;
    if ($nmon == 13) {
        $nmon = 1;
        $nyear++;
    }

    print "<td align=\"center\">"
      . "<a href=\"$url&st_mon=$nmon&st_year=$nyear\" target=\"_parent\">"
      . "$::d2m[$nmon] $nyear -&gt</a></td></tr></table>\n";

    # Make a form to enter the next month and year to show.
    # it defaults to the current location
    print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_parent\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_TL\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Timeline::VIEW_FR\">\n"
      . "<input type=\"hidden\" name=\"tl\" value=\"$tl_vol\">\n"
      . Args::make_hidden()
      .

      "<table cellspacing=\"0\" cellpadding=\"2\">\n"
      . "<tr><td align=\"center\"><select name=\"st_mon\" size=\"1\">\n";

    for my $i (1 .. 12) {
        if ($i == $mon) {
            print "<option selected value=\"$i\">$::d2m[$i]</option>\n";
        }
        else {
            print "<option value=\"$i\">$::d2m[$i]</option>\n";
        }
    }

    print "</select></td>"
      . "<td align=\"center\">"
      . "<input type=\"text\" name=\"st_year\" size=\"6\" value=\"$year\">"
      . "</td>"
      . "<td align=\"center\">"
      . "<input type=\"image\" src=\"pict/but_ok.jpg\" alt=\"Ok\" "
      . "width=43 height=20 border=\"0\"></td>\n"
      . "</tr></table></form>\n";

    Print::print_html_footer();
    return 0;
}

# Display the contents of the summary file (hits per day) and show
# it as hits per month
sub view_sum {
    Args::check_tl();

    Print::print_html_header("View Timeline Summary");

    my $tl_vol = Args::get_tl();
    my $tl     = $Caseman::vol2path{$tl_vol};

    $tl .= ".sum";

    open(TL, "<$tl") or die "Can not open $tl";
    my $url =
        "$::PROGNAME?$Args::baseargs&mod=$::MOD_TL&"
      . "view=$Timeline::VIEW_FR&tl=$tl_vol";

    print "<p>This page provides a monthly summary of activity.<br>"
      . "Each day that has activity is noted with the number of events<br>\n";

    my $p_year = "";
    my $p_mon  = "";

    print "<p><table cellspacing=2 border=0>\n";

    while (<TL>) {
        my @a = split(/ /, $_);
        next unless (scalar(@a) == 5);
        my $mon  = $::m2d{$a[1]};
        my $year = $a[3];
        $year = $1 if ($year =~ /^(\d{4,4}):$/);

        if (($p_year ne $year) || ($p_mon ne $mon)) {
            print "<tr><td colspan=6 align=left>"
              . "<a href=\"$url&st_mon=$mon&st_year=$year\">"
              . "$a[1] $year</a></td></tr>\n";

            $p_year = $year;
            $p_mon  = $mon;
        }
        print "<tr><td>&nbsp;&nbsp;</td><td>$a[0]</td>"
          . "<td>$a[1]</td><td>$a[2]</td><td>$year</td><td>($a[4])</td></tr>\n";
    }
    print "</table>\n";

    close(TL);

    Print::print_html_footer();
    return 0;
}

# display a given month of the timeline
sub view {
    Args::check_tl();
    Args::check_st_mon();
    Args::check_st_year();

    my $tl_vol  = Args::get_tl();
    my $tl      = $Caseman::vol2path{$tl_vol};
    my $st_mon  = Args::get_st_mon();
    my $st_year = Args::get_st_year();

    Print::print_html_header("View $st_mon, $st_year of Timeline");

    unless (open(TL, "$tl")) {
        print("Error opening $tl");
        return (1);
    }

    Print::log_host_inv(
        "$Args::args{'tl'}: Viewing timeline for $::d2m[$st_mon] $st_year");

    print "<table cellspacing=\"5\" cellpadding=\"2\" width=100%>\n";

    # zone identifies if we should be printing or not
    my $zone = 0;
    my $row  = 0;
    while (<TL>) {
        if (
/^(?:(\w\w\w\s+)?(\w\w\w\s+\d\d\s+\d\d\d\d)\s+(\d\d:\d\d:\d\d))?\s+(\d+)\s+([macb\.]+)\s+([-\/\?\w]+)\s+([\d\w\/]+)\s+([\d\w\/]+)\s+($::REG_META)\s+(.*)$/o
          )
        {

            my $day = "";
            $day = $1 if (defined $1);
            my $date = "";
            $date = $2 if (defined $2);
            my $time = "";
            $time = $3 if (defined $3);
            my $sz  = $4;
            my $mac = $5;
            my $p   = $6;
            my $u   = $7;
            my $g   = $8;
            my $i   = $9;
            my $f   = $10;

            # we must break this down to see if we can skip it or not
            if ($date ne "") {
                if ($date =~ /^(\w\w\w)\s+\d\d\s+(\d\d\d\d)$/) {
                    if ($2 < $st_year) {
                        next;
                    }
                    elsif (($2 == $st_year) && ($::m2d{$1} < $st_mon)) {
                        next;
                    }
                    elsif ($2 > $st_year) {
                        last;
                    }
                    elsif (($2 == $st_year) && ($::m2d{$1} > $st_mon)) {
                        last;
                    }
                    else {
                        $zone = 1;
                    }
                }
            }

            # we need to print this entry
            if ($zone) {

                # the deleted meta <blah-dead-2> entries screw up in HTML
                $f = "&lt;$1 &gt" if ($f =~ /^<(.*?)>$/);

                if (($row % 2) == 0) {
                    print "<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR\">\n";
                }
                else {
                    print
                      "<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR_TABLE\">\n";
                }

                print "<td>$day&nbsp;$date&nbsp;$time</td>"
                  . "<td>$sz</td><td>$mac</td><td>$p</td>"
                  . "<td>$u</td><td>$g</td><td>$i</td><td>"
                  . Print::html_encode($f)
                  . "</td></tr>\n";

                $row++;
            }
        }
        else {
            print "Error parsing timeline: "
              . Print::html_encode($_)
              . "<br>\n";
        }
    }
    close(TL);
    print "</table>";

    Print::print_html_footer();
    return 0;
}

# Blank Page
sub blank {
    Print::print_html_header("");
    print "<center><h3>File Activity Timelines</h3>\n"
      . "Here you can create a timeline of file activity.<br>\n"
      . "This process requires two steps:<p>\n"
      . "1.  <b>Create Data File</b> from file system data&nbsp;&nbsp;->"
      . "&nbsp;&nbsp;2.  <b>Create Timeline</b> from the data file\n"
      . "<p>Use the tabs above to start.\n";
    Print::print_html_footer();
    return 0;
}
