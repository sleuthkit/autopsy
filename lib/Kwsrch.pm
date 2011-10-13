#
# Keyword search mode
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

package Kwsrch;

require 'search.pl';

$Kwsrch::ENTER      = 1;
$Kwsrch::RESULTS_FR = 2;
$Kwsrch::RUN        = 3;
$Kwsrch::LOAD       = 4;
$Kwsrch::BLANK      = 5;

my $IMG_DETAILS = 0x80;

sub main {

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $Kwsrch::ENTER
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    if ($view == $Kwsrch::BLANK) {
        blank();
        return 0;
    }

    # Check Basic Args
    Args::check_vol('vol');

    # These windows don't need the meta data address
    if ($view == $Kwsrch::ENTER) {
        return enter();
    }
    elsif ($view == $Kwsrch::RESULTS_FR) {
        return results_fr();
    }
    elsif ($view == $Kwsrch::RUN) {
        return run();
    }
    elsif ($view == $Kwsrch::LOAD) {
        return load();
    }
    else {
        Print::print_check_err("Invalid Keyword Search View");
    }
}

my $CASE_INSENS = 1;
my $CASE_SENS   = 0;

my $REG_EXP = 1;
my $STRING  = 0;

# Form to enter search data
sub enter {
    my $vol = Args::get_vol('vol');

    Print::print_html_header("Search on $Caseman::vol2sname{$vol}");
    my $ftype = $Caseman::vol2ftype{$vol};

    if ($ftype eq 'blkls') {
        print "<center><h3>Keyword Search of Unallocated Space</h3>\n";
    }
    elsif ($ftype eq 'swap') {
        print "<center><h3>Keyword Search of swap partition</h3>\n";
    }
    elsif ($ftype eq 'raw') {
        print "<center><h3>Keyword Search of raw data</h3>\n";
    }
    elsif ($Caseman::vol2cat{$vol} eq "disk") {
        print "<center><h3>Keyword Search of disk</h3>\n";
    }
    else {
        print
"<center><h3>Keyword Search of Allocated and Unallocated Space</h3>\n";
    }

    # @@@ Fix this - caused by writing all results to a file
    if ($::LIVE == 1) {
        Print::print_err(
"Keyword searching is temporarily not available during live analysis mode"
        );
    }

    print "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "Enter the keyword string or expression to search for:<br> <input type=\"text\" name=\"str\"><br><br>\n"
      . Args::make_hidden()
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_KWSRCH\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Kwsrch::RESULTS_FR\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n";

    print "<table width=400><tr>\n"
      . "<td width=200 align=center><input type=\"checkbox\" name=\"ascii\" value=\"1\" CHECKED>"
      . "ASCII \n</td>"
      . "<td width=200 align=center><input type=\"checkbox\" name=\"unicode\" value=\"1\" CHECKED>"
      . "Unicode</td></tr>\n"
      . "<tr><td align=center><input type=\"checkbox\" name=\"srch_case\" value=\"$CASE_INSENS\">"
      . "Case Insensitive</td>\n"
      . "<td align=center><input type=\"checkbox\" name=\"regexp\" value=\"$REG_EXP\">\n"
      . "<tt>grep</tt> Regular Expression</td></tr></table>\n"
      . "<input type=\"image\" src=\"pict/but_search.jpg\" "
      . "alt=\"Search\" border=\"0\">\n</form>\n";

    if ($::LIVE == 0) {
        print "<table width=600><tr>\n";

        # If we are a non-blkls image and one exists - make a button to load it
        if (($ftype ne 'blkls') && (exists $Caseman::vol2blkls{$vol})) {
            print "<td align=center width=200>"
              . "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_FRAME\">\n"
              . "<input type=\"hidden\" name=\"submod\" value=\"$::MOD_KWSRCH\">\n"
              . "<input type=\"hidden\" name=\"vol\" value=\"$Caseman::vol2blkls{$vol}\">\n"
              . Args::make_hidden()
              . "<input type=\"image\" src=\"pict/srch_b_lun.jpg\" "
              . "alt=\"Load Unallocated Image\" border=\"0\">\n<br></form></td>\n";
        }

        # If we are a blkls and the original exists - make a button to load it
        elsif (($ftype eq 'blkls')
            && (exists $Caseman::mod2vol{$vol}))
        {
            print "<td align=center width=200>"
              . "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_FRAME\">\n"
              . "<input type=\"hidden\" name=\"submod\" value=\"$::MOD_KWSRCH\">\n"
              . "<input type=\"hidden\" name=\"vol\" value=\"$Caseman::mod2vol{$vol}\">\n"
              . Args::make_hidden()
              . "<input type=\"image\" src=\"pict/srch_b_lorig.jpg\" "
              . "alt=\"Load Original Image\" border=\"0\">\n<br></form></td>\n";
        }

        # Strings Button
        if (   (!(exists $Caseman::vol2str{$vol}))
            || (!(exists $Caseman::vol2uni{$vol})))
        {

            my $dest_vol = $vol;
            $dest_vol = $Caseman::mod2vol{$vol}
              if exists($Caseman::mod2vol{$vol});

            print "<td align=center width=200>"
              . "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_DETAILS\">\n"
              . "<input type=\"hidden\" name=\"vol\" value=\"$dest_vol\">\n"
              . Args::make_hidden()
              . "<input type=\"image\" src=\"pict/srch_b_str.jpg\" "
              . "alt=\"Extract Strings\" border=\"0\">\n<br></form></td>\n";
        }

        # Unallocated Space Button
        if (   ($Fs::is_fs{$ftype})
            && (!(exists $Caseman::vol2blkls{$vol})))
        {
            print "<td align=center width=200>"
              . "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_DETAILS\">\n"
              . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
              . Args::make_hidden()
              . "<input type=\"image\" src=\"pict/srch_b_un.jpg\" "
              . "alt=\"Extract Unallocated Space\" border=\"0\">\n<br></form></td>\n";
        }

        print "</tr></table>\n";
    }

    print "<a href=\"help/grep.html\" target=\"_blank\">"
      . "Regular Expression Cheat Sheet</a>\n<br><br>\n";

    print "<p><font color=\"red\">NOTE:</font> The keyword search runs "
      . "<tt>grep</tt> on the image.<br>\n"
      . "A list of what will and "
      . "what will not be found is available "
      . "<a href=\"help/grep_lim.html\" target=\"_blank\">here</a>.<br>\n";

    # Section for previous searches
    if ($::LIVE == 0) {
        my $srch_name = get_srch_fname(0);
        if (-e $srch_name) {
            print "<hr><h3>Previous Searches</h3>\n" . "<table width=600>\n";
            my $row_idx = 0;

            # Cycle through the files
            for (my $srch_idx = 0;; $srch_idx++) {

                $srch_name = get_srch_fname($srch_idx);

                last unless (-e $srch_name);

                # Open the file to get the string and count
                unless (open(SRCH, "$srch_name")) {
                    print "Error opening search file: $srch_name\n";
                    return 1;
                }
                my $prev_str = "";
                my $prev_cnt = 0;

                while (<SRCH>) {
                    unless (/^(\d+)\|(.*?)?\|(.*)$/) {
                        print
                          "Error pasing header of search file: $srch_name\n";
                        return 1;
                    }
                    $prev_cnt = $1;
                    $prev_str = $3;
                    if (length($prev_str) > 32) {
                        $prev_str = substr($prev_str, 0, 32);
                        $prev_str .= "...";
                    }

                    last;
                }
                close(SRCH);

                print "<tr>\n" if ($row_idx == 0);

                print "  <td align=center width=150>\n"
                  . "<form action=\"$::PROGNAME\" method=\"get\">\n"
                  . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_KWSRCH\">\n"
                  . "<input type=\"hidden\" name=\"view\" value=\"$Kwsrch::RESULTS_FR\">\n"
                  . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
                  . "<input type=\"hidden\" name=\"srchidx\" value=\"$srch_idx\">\n"
                  . Args::make_hidden();

                print "<input type=\"SUBMIT\" value=\"".Print::html_encode($prev_str)." ($prev_cnt)\">"
                  . "<br></form>\n";

                if ($row_idx == 3) {
                    print "</tr>\n";
                    $row_idx = 0;
                }
                else {
                    $row_idx++;
                }
            }
            print "</table>\n";
        }
    }

    # Predefined expressions from search.pl
    print "<hr><h3>Predefined Searches</h3>\n";
    print "<table width=600>\n";
    my $row_idx = 0;
    my $r;
    foreach $r (keys %Kwsrch::auto_srch) {

        $Kwsrch::auto_srch_reg{$r} = 0
          unless (defined $Kwsrch::auto_srch_reg{$r});
        $Kwsrch::auto_srch_csense{$r} = 1
          unless (defined $Kwsrch::auto_srch_csense{$r});

        print "<tr>\n" if ($row_idx == 0);

        # @@@ Make a unicode option in predefined

        print "  <td align=center width=150>\n"
          . "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_KWSRCH\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Kwsrch::RESULTS_FR\">\n"
          . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
          . "<input type=\"hidden\" name=\"str\" value=\"$Kwsrch::auto_srch{$r}\">\n"
          . "<input type=\"hidden\" name=\"ascii\" value=\"1\">\n"
          . Args::make_hidden();

        if ($Kwsrch::auto_srch_reg{$r} == 1) {
            print
              "<input type=\"hidden\" name=\"regexp\" value=\"$REG_EXP\">\n";
        }
        if ($Kwsrch::auto_srch_csense{$r} == 0) {
            print
"<input type=\"hidden\" name=\"srch_case\" value=\"$CASE_INSENS\">\n";
        }
        print "<input type=\"SUBMIT\" value=\"$r\"><br></form>\n" . "  </td>\n";

        if ($row_idx == 3) {
            print "</tr>\n";
            $row_idx = 0;
        }
        else {
            $row_idx++;
        }
    }
    print "</table>\n";

    Print::print_html_footer();
    return 0;
}

# MAIN WITH RESULTS
# Page that makes frame with the results on left and data units on right
sub results_fr {
    my $vol = Args::get_vol('vol');

    # A string was given for a new search
    if (exists $Args::args{'str'}) {
        Args::check_str();

        Print::print_html_header_frameset(
            "Search on $Caseman::vol2sname{$vol} for $Args::args{'str'}");

        print "<frameset cols=\"35%,65%\">\n";

        my $srch_case = "";
        $srch_case = "&srch_case=$Args::args{'srch_case'}"
          if (exists $Args::args{'srch_case'});

        my $regexp = "";
        $regexp = "&regexp=$Args::args{'regexp'}"
          if (exists $Args::args{'regexp'});

        my $ascii = "";
        $ascii = "&ascii=$Args::args{'ascii'}"
          if (exists $Args::args{'ascii'});

        my $unicode = "";
        $unicode = "&unicode=$Args::args{'unicode'}"
          if (exists $Args::args{'unicode'});

        # Block List
        print "<frame src=\"$::PROGNAME?"
          . "mod=$::MOD_KWSRCH&view=$Kwsrch::RUN&"
          . "$Args::baseargs$srch_case$regexp&str=$Args::enc_args{'str'}$ascii$unicode\">\n";
    }
    elsif (exists $Args::args{'srchidx'}) {
        Args::check_srchidx();

        Print::print_html_header_frameset(
"Search on $Caseman::vol2sname{$vol} for Index $Args::args{'srchidx'}"
        );

        print "<frameset cols=\"35%,65%\">\n";

        # Block List
        print "<frame src=\"$::PROGNAME?"
          . "mod=$::MOD_KWSRCH&view=$Kwsrch::LOAD&"
          . "$Args::baseargs&srchidx=$Args::enc_args{'srchidx'}\">\n";
    }

    # Block Contents
    print "<frame src=\"$::PROGNAME?mod=$::MOD_KWSRCH&view=$Kwsrch::BLANK&"
      . "$Args::baseargs\" name=\"content\">\n"
      . "</frameset>\n";

    Print::print_html_footer_frameset();
    return 0;
}

# Find an empty file to save the keyword searches to
sub find_srch_file {
    my $vol = Args::get_vol('vol');

    my $out_name = "$::host_dir" . "$::DATADIR/$Caseman::vol2sname{$vol}";
    my $i;
    for ($i = 0; -e "${out_name}-${i}.srch"; $i++) { }

    return "${out_name}-${i}.srch";
}

# Pass the index
# return the full path of the file returned
sub get_srch_fname {
    my $idx = shift;
    my $vol = Args::get_vol('vol');
    return "$::host_dir"
      . "$::DATADIR"
      . "/$Caseman::vol2sname{$vol}-${idx}.srch";
}

sub load {
    Args::check_srchidx();

    Print::print_html_header("");

    if ($::LIVE == 1) {
        print "Searches cannot be loaded during live analysis<br>\n";
        return 1;
    }

    my $srch_name = get_srch_fname($Args::args{'srchidx'});

    print "<b><a href=\"$::PROGNAME?mod=$::MOD_KWSRCH&view=$Kwsrch::ENTER&"
      . "$Args::baseargs\" "
      . "target=\"_parent\">New Search</a></b>\n<p>";

    print_srch_results($srch_name);

    Print::print_html_footer();
    return 0;
}

# performs actual search, saves hits to file, and calls method to print
sub run {
    Args::check_str();

    Print::print_html_header("");

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $orig_str = Args::get_str();
    my $grep_str = $orig_str;       # we will escape some values in the grep ver

    # Check for a search string
    if ($orig_str eq "") {
        print "You must enter a string value to search<br>\n";
        print "<b><a href=\"$::PROGNAME?mod=$::MOD_KWSRCH&view=$Kwsrch::ENTER&"
          . "$Args::baseargs\" target=\"_parent\">New Search</a></b>\n<p>";
        return 1;
    }

    my $log = "";                   # Log entry string

    my $ascii   = 0;
    my $unicode = 0;

    if ((exists $Args::args{'ascii'}) && ($Args::args{'ascii'} == 1)) {
        $ascii = 1;
        $log .= "ASCII, ";
    }
    if ((exists $Args::args{'unicode'}) && ($Args::args{'unicode'} == 1)) {
        $unicode = 1;
        $log .= "Unicode, ";
    }

    if (($ascii == 0) && ($unicode == 0)) {
        print "You must choose either ASCII or Unicode to search<br>\n";
        print "<b><a href=\"$::PROGNAME?mod=$::MOD_KWSRCH&view=$Kwsrch::ENTER&"
          . "$Args::baseargs\" target=\"_parent\">New Search</a></b>\n<p>";
        return 1;
    }

    my $grep_flag = "";    # Flags to pass to grep

    # Check if search is case insensitive
    my $case = 0;
    if (   (exists $Args::args{'srch_case'})
        && ($Args::args{'srch_case'} == $CASE_INSENS))
    {
        $grep_flag = "-i";
        $case      = 1;
        $log .= "Case Insensitive ";
    }

    # Check if search is a regular expression
    my $regexp = 0;
    if ((exists $Args::args{'regexp'}) && ($Args::args{'regexp'} == $REG_EXP)) {
        $grep_flag .= " -E";
        $regexp = 1;
        $log .= "Regular Expression ";
    }

    # if not a reg-exp, we need to escape some special values that
    # 'grep' will misinterpret
    else {
        $grep_str =~ s/\\/\\\\/g;    # \
        $grep_str =~ s/\./\\\./g;    # .
        $grep_str =~ s/\[/\\\[/g;    # [
        $grep_str =~ s/\^/\\\^/g;    # ^
        $grep_str =~ s/\$/\\\$/g;    # $
        $grep_str =~ s/\*/\\\*/g;    # *
             # We need to add ' to end begin and end of the search as well
        if ($grep_str =~ /\'/) {
            $grep_str =~ s/\'/\\\'/g;    # '
            $grep_str = "'$grep_str'";
        }
        $grep_str =~ s/^\-/\\\-/;        # starting with - (mistakes for an arg)
    }

    Print::log_host_inv(
        "$Caseman::vol2sname{$vol}: ${log}search for $grep_str");

    # Get the addressable unit of image
    my $bs = Args::get_unitsize();

    # $norm_str is normalized to find the "hit" in the output
    my $norm_str = $orig_str;

    # make this lowercase if we are doing case insens
    $norm_str =~ tr/[A-Z]/[a-z]/ if ($case == 1);

    my $norm_str_len = length($norm_str);

    # array to pass to printing method
    my @results;

    my $name_uni = "";
    my $name_asc = "";

    # round 0 is for ASCII and 1 is for Unicode
    for (my $i = 0; $i < 2; $i++) {

        next if (($i == 0) && ($ascii == 0));
        next if (($i == 1) && ($unicode == 0));

        @results = ();

        local *OUT;

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

        if ($i == 0) {
            print "<b>Searching for ASCII</b>: ";
        }
        else {
            print "<b>Searching for Unicode</b>: ";
        }

        # if the string is less than 4 chars, then it will not be in the
        # strings file so it will be searched for the slow way
        if (length($orig_str) < 4) {
            my $ltmp = length($orig_str);

            if ($i == 0) {
                if (   (($ftype eq "raw") || ($ftype eq "swap"))
                    && ($Caseman::vol2end{$vol} != 0))
                {
                    Exec::exec_pipe(*OUT,
                            "'$::TSKDIR/blkls' -e -f $ftype -i $imgtype $img "
                          . $Caseman::vol2start{$vol} . "-"
                          . $Caseman::vol2end{$vol}
                          . " | '$::TSKDIR/srch_strings' -a -t d -$ltmp | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
                else {
                    Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkls' -e -f $ftype -o $offset -i $imgtype $img | '$::TSKDIR/srch_strings' -a -t d -$ltmp | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
            }

            else {
                if (   (($ftype eq "raw") || ($ftype eq "swap"))
                    && ($Caseman::vol2end{$vol} != 0))
                {
                    Exec::exec_pipe(*OUT,
                            "'$::TSKDIR/blkls' -e -f $ftype -i $imgtype $img "
                          . $Caseman::vol2start{$vol} . "-"
                          . $Caseman::vol2end{$vol}
                          . " | '$::TSKDIR/srch_strings' -a -t d -e l -$ltmp | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
                else {
                    Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkls' -e -f $ftype -o $offset -i $imgtype $img | '$::TSKDIR/srch_strings' -a -t d -e l -$ltmp | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
            }
        }

        # Use the strings file if it exists
        elsif (($i == 0) && (defined $Caseman::vol2str{$vol})) {
            my $str_vol = $Caseman::vol2path{$Caseman::vol2str{$vol}};
            Exec::exec_pipe(*OUT,
                "'$::GREP_EXE' $grep_flag '$grep_str' $str_vol");
        }
        elsif (($i == 1) && (defined $Caseman::vol2uni{$vol})) {
            my $str_vol = $Caseman::vol2path{$Caseman::vol2uni{$vol}};
            Exec::exec_pipe(*OUT,
                "'$::GREP_EXE' $grep_flag '$grep_str' $str_vol");
        }

        # Run strings on the image first and then grep that
        else {
            if ($i == 0) {
                if (   (($ftype eq "raw") || ($ftype eq "swap"))
                    && ($Caseman::vol2end{$vol} != 0))
                {
                    Exec::exec_pipe(*OUT,
                            "'$::TSKDIR/blkls' -e -f $ftype -i $imgtype $img "
                          . $Caseman::vol2start{$vol} . "-"
                          . $Caseman::vol2end{$vol}
                          . " | '$::TSKDIR/srch_strings' -a -t d | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
                else {
                    Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkls' -e -f $ftype -o $offset -i $imgtype $img | '$::TSKDIR/srch_strings' -a -t d | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
            }
            else {
                if (   (($ftype eq "raw") || ($ftype eq "swap"))
                    && ($Caseman::vol2end{$vol} != 0))
                {
                    Exec::exec_pipe(*OUT,
                            "'$::TSKDIR/blkls' -e -f $ftype -i $imgtype $img "
                          . $Caseman::vol2start{$vol} . "-"
                          . $Caseman::vol2end{$vol}
                          . " | '$::TSKDIR/srch_strings' -a -t d -e l | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
                else {
                    Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkls' -e -f $ftype -o $offset -i $imgtype $img | '$::TSKDIR/srch_strings' -a -t d -e l | '$::GREP_EXE' $grep_flag '$grep_str'"
                    );
                }
            }
        }

        alarm(0);
        $SIG{ALRM} = 'DEFAULT';

        # Cycle through the results and put them in an array
        while ($_ = Exec::read_pipe_line(*OUT)) {

            # Parse out the byte offset and hit string
            if (/^\s*(\d+)\s+(.+)$/) {
                my $off          = $1;
                my $hit_str_orig = $2;
                my $idx          = 0;

                # Make a copy that we can modify & play with
                my $hit_str = $hit_str_orig;
                $hit_str =~ tr/[A-Z]/[a-z]/ if ($case == 1);

                # How long was the string that we hit?
                my $hit_str_len = length($hit_str);

                # I'm not sure how to find a grep re in the hit yet, so
                # for now we do not get the exact offset
                if ($regexp) {
                    my $b = int($off / $bs);
                    my $o = int($off % $bs);

                    # $hit =~ s/\n//g;
                    push @results, "${b}|${o}|";
                    next;
                }

                # There could be more than one keyword in the string
                # so cycle through all of them
                my $psize = scalar(@results);
                while (($idx = index($hit_str, $norm_str, $idx)) > -1) {

                    # The summary of the hit starts 5 chars before it
                    my $sum_min = $idx - 5;
                    $sum_min = 0 if ($sum_min < 0);

                    # Goto 5 after, if there is still space
                    my $sum_max = $idx + $norm_str_len + 5;
                    $sum_max = $hit_str_len if ($sum_max > $hit_str_len);

                    my $sum_hit =
                      substr($hit_str_orig, $sum_min, $sum_max - $sum_min);

                    # remove new lines
                    $sum_hit =~ s/\n/ /g;

                    # The actual offset for Unicode is 2 bytes per char
                    my $tmpidx = $idx;
                    $tmpidx *= 2
                      if ($i == 1);

                    my $b = int(($off + $tmpidx) / $bs);
                    my $o = int(($off + $tmpidx) % $bs);

                    push @results, "${b}|${o}|$sum_hit";

                    # advance index to find next hit
                    $idx++;
                }

                # If we did not find a term, then just print what
                # was found-this occurs bc index does not find it
                # sometimes.
                if ($psize == scalar(@results)) {

                    my $b = int($off / $bs);
                    my $o = int($off % $bs);

                    # $hit =~ s/\n//g;
                    push @results, "${b}|${o}|";
                    next;
                }
            }

            # A negative offset is common on FreeBSD with large images
            elsif (/^\s*(\-\d+):?\s*(.+)$/) {
                print "ERROR: Negative byte offset ($1) Your version of "
                  . "strings likely does not support large files: $2<br>\n";
            }
            else {
                print "Error parsing grep result: $_<br>\n";
            }
        }
        close(OUT);

        print " <b>Done</b><br>";
        my $cnt = scalar(@results);

        my $srch_name = "";
        if ($::LIVE == 0) {
            print "<b>Saving</b>: ";

            # Find a file to save the results to
            $srch_name = find_srch_file();
            unless (open(IDX, ">$srch_name")) {
                print "Error opening $srch_name\n";
                return (1);
            }

            # Print the header
            if ($i == 0) {
                print IDX "$cnt|${grep_flag}|${orig_str}|ascii\n";
                $name_asc = $srch_name;
            }
            else {
                print IDX "$cnt|${grep_flag}|${orig_str}|unicode\n";
                $name_uni = $srch_name;
            }

            for (my $a = 0; $a < $cnt; $a++) {
                print IDX "$results[$a]\n";
            }
            close(IDX);
            print " <b>Done</b><br>\n";
        }
        if ($i == 0) {
            print "$cnt hits";
            print "- <a href=\"#ascii\">link to results</a>" if ($cnt > 0);
            print "<br>\n";
        }
        else {
            print "$cnt hits";
            print "- <a href=\"#unicode\">link to results</a>" if ($cnt > 0);
            print "<br>\n";
        }
        print "<hr>\n";
    }

    print "<b><a href=\"$::PROGNAME?mod=$::MOD_KWSRCH&view=$Kwsrch::ENTER&"
      . "$Args::baseargs\" "
      . "target=\"_parent\">New Search</a></b>\n<p>";

    if ($::LIVE == 0) {
        if ($ascii == 1) {
            print_srch_results($name_asc);
        }

        if ($unicode == 1) {
            print_srch_results($name_uni);
        }
    }

    Print::print_html_footer();
    return 0;
}

# Args are search string, grep flags, and array of hits
sub print_srch_results {

    if (scalar(@_) != 1) {
        print "Missing Args for print_srch_results()\n";
        return 1;
    }

    my $srch_name = shift;
    my $vol       = Args::get_vol('vol');
    my $ftype     = $Caseman::vol2ftype{$vol};

    my $addr_str = $Fs::addr_unit{$ftype};

    unless (open(SRCH, "$srch_name")) {
        print "Error opening search file: $srch_name\n";
        return 1;
    }

    my @results;
    my $grep_str  = "";
    my $grep_flag = "";
    my $cnt       = 0;
    my $type      = 0;    # ASCII iis 0 and Unicode is 1

    my $prev = -1;

    while (<SRCH>) {

        # The first line is a header
        if ($. == 1) {
            if (/^(\d+)\|(.*?)?\|(.*)$/) {
                $cnt       = $1;
                $grep_flag = $2;
                $grep_str  = $3;
                $type      = 0;
            }
            else {
                print "Error pasing header of search file: $srch_name\n";
                close(SRCH);
                return 1;
            }

            if ($grep_str =~ /^(.*?)\|unicode$/) {
                $grep_str = $1;
                $type     = 1;
            }
            elsif ($grep_str =~ /^(.*?)\|ascii$/) {
                $grep_str = $1;
            }

            my $grep_str_html = Print::html_encode($grep_str);
            print "<hr>\n";
            if ($type == 0) {
                print "<a name=\"ascii\">\n";
            }
            else {
                print "<a name=\"unicode\">\n";
            }

            if ($cnt == 0) {
                print "<b><tt>$grep_str_html</tt> was not found</b><br>\n";
            }
            elsif ($cnt == 1) {
                print
"<b>1 occurrence of <tt>$grep_str_html</tt> was found</b><br>\n";
            }
            else {
                print
"<b>$cnt occurrences of <tt>$grep_str_html</tt> were found</b><br>\n";
            }

            print "Search Options:<br>\n";
            if ($type == 0) {
                print "&nbsp;&nbsp;ASCII<br>\n";
            }
            else {
                print "&nbsp;&nbsp;Unicode<br>\n";
            }

            if ($grep_flag =~ /\-i/) {
                print "&nbsp;&nbsp;Case Insensitive<br>\n";
            }
            else {
                print "&nbsp;&nbsp;Case Sensitive<br>\n";
            }
            if ($grep_flag =~ /\-E/) {
                print "&nbsp;&nbsp;Regular Expression<br>\n";
            }

            print "<hr>\n";

            if ($cnt > 1000) {
                print "There were more than <U>1000</U> hits.<br>\n";
                print "Please revise the search to a managable amount.\n";
                print
                  "<p>The $cnt hits can be found in: <tt>$srch_name</tt><br>\n";
                close(SRCH);
                return 0;
            }

            next;
        }

        unless (/^(\d+)\|(\d+)\|(.*)?$/) {
            print "Error parsing results: $_\n";
            close(SRCH);
            return 1;
        }

        my $blk = $1;
        my $off = $2;
        my $str = $3;

        if ($blk != $prev) {
            my $url =
"$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT_MENU_FR&$Args::baseargs&block=$blk";

            print "<br>\n$addr_str $blk (<a href=\"$url&sort=$Data::SORT_HEX\" "
              . "target=content>Hex</a> - "
              . "<a href=\"$url&sort=$Data::SORT_ASC\" target=content>"
              . "Ascii</a>";

            print
" - <a href=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_DATA&view=$Data::CONT_MENU_FR&"
              . "mnt=$Args::enc_args{'mnt'}&vol=$Caseman::mod2vol{$vol}&"
              . "btype=$Data::ADDR_BLKLS&block=$blk\" target=content>Original</a>"
              if ( ($ftype eq 'blkls')
                && (exists $Caseman::mod2vol{$vol}));

            print ")<br>";
            $prev = $blk;
        }

        my $occ = $. - 1;
        if ($str ne "") {
            $str = Print::html_encode($str);
            print "$occ: $off (<tt>$str</tt>)<br>\n";
        }
        else {
            print "$occ: $off<br>\n";
        }
    }

    close(SRCH);
    return 0;
}

# Blank Page
sub blank {
    Print::print_html_header("");
    print "<!-- This Page Intentionally Left Blank -->\n";
    Print::print_html_footer();
    return 0;
}

1;
