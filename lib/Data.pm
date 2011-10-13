#
# Data / Content layer functions
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

package Data;

$Data::FRAME        = 1;
$Data::ENTER        = 2;
$Data::CONT         = 3;
$Data::CONT_MENU    = 4;
$Data::CONT_MENU_FR = 5;
$Data::REPORT       = 6;
$Data::LIST         = 7;
$Data::EXPORT       = 8;
$Data::BLANK        = 9;

# Display types that use the sort variable
$Data::SORT_ASC = 0;
$Data::SORT_HEX = 1;
$Data::SORT_STR = 2;

# Types of block numbers
$Data::ADDR_DD    = 0;
$Data::ADDR_BLKLS = 1;

sub main {

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $Data::FRAME
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    # Check Basic Args
    Args::check_vol('vol');

    # These windows don't need the data unit address
    if ($view == $Data::FRAME) {
        return frame();
    }
    elsif ($view == $Data::ENTER) {
        return enter();
    }
    elsif ($view == $Data::LIST) {
        return list();
    }
    elsif ($view == $Data::BLANK) {
        return blank();
    }

    # These windows do need the data unit address
    Args::check_block();
    if ($view == $Data::CONT) {
        return content();
    }
    elsif ($view == $Data::CONT_MENU) {
        return content_menu();
    }
    elsif ($view == $Data::CONT_MENU_FR) {
        return content_menu_frame();
    }
    elsif ($view == $Data::REPORT) {
        return report();
    }

    elsif ($view == $Data::EXPORT) {
        return export();
    }
    else {
        Print::print_check_err("Invalid Data View");
    }

}

# Generate the 2 frames for block browsing
sub frame {
    Print::print_html_header_frameset("Data Browse on $Args::args{'vol'}");

    print "<frameset cols=\"20%,80%\">\n";

    # Data Contents
    if (exists $Args::args{'block'}) {
        my $len = Args::get_len();

        print "<frame src=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::ENTER&"
          . "$Args::baseargs&block=$Args::enc_args{'block'}\">\n"
          . "<frame src=\"$::PROGNAME?"
          . "mod=$::MOD_DATA&view=$Data::CONT_MENU_FR&"
          . "block=$Args::enc_args{'block'}&$Args::baseargs&len=$len\" "
          . "name=\"content\">\n</frameset>\n";
    }
    else {
        print "<frame src=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::ENTER&"
          . "$Args::baseargs\">\n"
          . "<frame src=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::BLANK&"
          . "$Args::baseargs\" name=\"content\">\n</frameset>\n";
    }

    Print::print_html_footer_frameset();
    return 0;
}

# Frame to enter the data into
sub enter {
    Print::print_html_header("");

    my $vol   = Args::get_vol('vol');
    my $ftype = $Caseman::vol2ftype{$vol};
    my $bs    = Args::get_unitsize();

    print "<form action=\"$::PROGNAME\" method=\"get\" "
      . "target=\"content\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_DATA\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Data::CONT_MENU_FR\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
      . Args::make_hidden()
      .

      # Address
      "<b>$Fs::addr_unit{$ftype} Number:</b><br>&nbsp;&nbsp;&nbsp;&nbsp;"
      . "<input type=\"text\" name=\"block\" size=12 maxlength=12";
    print " value=\"$Args::enc_args{'block'}\""
      if (exists $Args::args{'block'});
    print ">\n";

    # Number of units
    print "<p><b>Number of $Fs::addr_unit{$ftype}" . "s:</b>"
      . "<br>&nbsp;&nbsp;&nbsp;&nbsp;"
      . "<input type=\"text\" name=\"len\" value=\"1\" size=6 maxlength=6>\n";

    print "<p><b>$Fs::addr_unit{$ftype} Size:</b>" . "&nbsp;$bs\n";

    # blkls images do not get to select this
    # if (($ftype ne 'blkls') && ($ftype ne 'swap') && ($ftype ne 'raw')) {
    if ($Fs::is_fs{$ftype} == 1) {
        print "<p><b>Address Type:</b><br>&nbsp;&nbsp;&nbsp;&nbsp;"
          . "<select name=\"btype\" size=1>\n"
          . "<option value=\"$Data::ADDR_DD\" selected>Regular (dd)</option>\n"
          . "<option value=\"$Data::ADDR_BLKLS\">Unallocated (blkls)</option>\n"
          . "</select>\n";
    }
    else {
        print
          "<input type=\"hidden\" name=\"btype\" value=\"$Data::ADDR_DD\">\n";
    }

    # Lazarus
    print "<p><b>Lazarus Addr:</b> "
      . "<input type=\"checkbox\" name=\"btype2\">\n"
      . "<p><input type=\"image\" src=\"pict/but_view.jpg\" "
      . "width=45 height=22 alt=\"View\" border=\"0\">\n"
      . "</form>\n";

    print "<hr><p>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::LIST&$Args::baseargs\" target=\"content\">"
      . "<img src=\"pict/but_alloc_list.jpg\" border=\"0\" "
      . "width=113 height=20 alt=\"Allocation List\"></a><br>\n"
      if ($Fs::is_fs{$ftype} == 1);

 #      unless (($ftype eq 'blkls') || ($ftype eq 'swap') || ($ftype eq 'raw'));

    # If there is a blkls image, then provide a button for it
    if (($ftype ne 'blkls') && (exists $Caseman::vol2blkls{$vol})) {
        print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_FRAME\">\n"
          . "<input type=\"hidden\" name=\"submod\" value=\"$::MOD_DATA\">\n"
          . "<input type=\"hidden\" name=\"vol\" value=\"$Caseman::vol2blkls{$vol}\">\n"
          . Args::make_hidden()
          . "<p><input type=\"image\" src=\"pict/srch_b_lun.jpg\" "
          . "alt=\"Load Unallocated Image\" border=\"0\">\n<br></form>\n";
    }

    # If we are using a blkls image, then give a button for the original
    elsif (($ftype eq 'blkls') && (exists $Caseman::mod2vol{$vol})) {
        print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_FRAME\">\n"
          . "<input type=\"hidden\" name=\"submod\" value=\"$::MOD_DATA\">\n"
          . "<input type=\"hidden\" name=\"vol\" value=\"$Caseman::mod2vol{$vol}\">\n"
          . Args::make_hidden()
          . "<p><input type=\"image\" src=\"pict/srch_b_lorig.jpg\" "
          . "alt=\"Load Original Image\" border=\"0\">\n<br></form>\n";
    }

    Print::print_html_footer();
    return 0;
}

# generate frame for block content
sub content_menu_frame {
    Print::print_html_header_frameset("");

    my $sort = $Data::SORT_ASC;
    $sort = $1
      if ((exists $Args::args{'sort'}) && ($Args::args{'sort'} =~ /^(\d+)$/));

    my $len = Args::get_len();
    if ($len == 0) {
        Print::print_err("Invalid length: 0");
    }

    my $blk;

    my $ifind = Args::get_ifind();

    # off is 1 if a lazarus block number as they are off by one
    my $off = 0;
    $off = -1 if (exists $Args::args{'btype2'});

    # Do we need to convert from blkls value to dd value ?
    if (   (exists $Args::args{'btype'})
        && ($Args::args{'btype'} == $Data::ADDR_BLKLS))
    {

        my $vol     = Args::get_vol('vol');
        my $b       = Args::get_block() + $off;
        my $ftype   = $Caseman::vol2ftype{$vol};
        my $img     = $Caseman::vol2path{$vol};
        my $offset  = $Caseman::vol2start{$vol};
        my $imgtype = $Caseman::vol2itype{$vol};

        local *OUT;
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/blkcalc' -f $ftype -u $b -o $offset -i $imgtype $img");
        $blk = Exec::read_pipe_line(*OUT);
        close(OUT);

        $blk = "Error getting block"
          if ((!defined $blk) || ($blk eq ""));

        if ($blk !~ /^\d+$/) {
            print "$blk\n";
            return 1;
        }
    }
    else {
        $blk = Args::get_block() + $off;
    }

    print "<frameset rows=\"25%,75%\">\n"
      . "<frame src=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT_MENU&$Args::baseargs"
      . "&block=$blk&sort=$sort&len=$len&ifind=$ifind\">\n"
      . "<frame src=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT&$Args::baseargs"
      . "&block=$blk&sort=$sort&len=$len\" name=\"cont2\">\n"
      . "</frameset>";

    Print::print_html_footer_frameset();
    return 0;
}

sub print_ifind {
    my $block = Args::get_block();

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Finding $Fs::meta_str{$ftype} for data unit $block"
    );

    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/ifind' -f $ftype -d $block -o $offset -i $imgtype $img");
    my $meta = Exec::read_pipe_line(*OUT);
    close(OUT);

    $meta = "Error getting meta address"
      if ((!defined $meta) || ($meta eq ""));

    if ($meta =~ /^($::REG_META)$/o) {
        $meta = $1;
        my $tmpr = $Caseman::vol2mnt{$vol};
        print "<b>Pointed to by $Fs::meta_str{$ftype}:</b> "
          . "<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_META&$Args::baseargs&"
          . "meta=$meta\" target=\"_top\">$meta</a><br>\n";

        print "<b>Pointed to by file:</b>\n";
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/ffind' -f $ftype -a -o $offset -i $imgtype $img $meta");
        while ($_ = Exec::read_pipe_line(*OUT)) {
            chop;

            # Make it red if it is deleted
            if (/^(\*)\s+\/*(.*)$/) {
                Print::print_output("<tt><font color=\"$::DEL_COLOR[0]\">"
                      . Print::html_encode(${tmpr} . ${2})
                      . "</font></tt> (deleted)<br>\n");
            }

            # If it starts with a '/' then it must be a file
            elsif (/^\/(.*)$/) {
                Print::print_output("<tt>"
                      . Print::html_encode(${tmpr} . ${1})
                      . "</tt><br>\n");
            }

            # this must be an error
            else {
                Print::print_output(Print::html_encode($_) . "<br>\n");
            }
        }
        close(OUT);
    }
    else {
        print "$meta\n";
    }
}

# Generate index for block content
sub content_menu {
    Print::print_html_header("");

    my $block = Args::get_block();
    my $prev  = $block - 1;
    my $next  = $block + 1;

    my $sort    = Args::get_sort();
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $ifind = Args::get_ifind();

    my $len = Args::get_len();
    my $bs  = Args::get_unitsize();

    if ($len == 0) {
        Print::print_err("Invalid length: 0");
    }

    print "<center>";
    my $url =
        "$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT_MENU_FR&"
      . "$Args::baseargs&sort=$sort&len=$len"
      . "&ifind=$ifind";

    # Next and Previous pointers
    print "<table cellspacing=\"0\" cellpadding=\"2\">\n" . "<tr>\n";

    # Previous
    if ($prev < $Fs::first_addr{$ftype}) {
        print "<td align=\"right\">&nbsp;</td>\n";
    }
    else {
        print "<td align=\"right\">"
          . "<a href=\"$url&block=$prev\" target=\"_parent\">\n"
          . "<img src=\"pict/but_prev.jpg\" alt=\"previous\" "
          . "width=\"89\" height=20 border=\"0\"></a></td>\n";
    }

    # Next
    print "<td align=\"left\"><a href=\"$url&block=$next\""
      . " target=\"_parent\">"
      . "<img src=\"pict/but_next.jpg\" alt=\"next\" "
      . "width=\"89\" height=20 border=\"0\"></a></td>\n</tr>\n";

    print "<tr><td align=\"right\"><a href=\"$::PROGNAME?"
      . "mod=$::MOD_DATA&view=$Data::EXPORT&$Args::baseargs&"
      . "block=$block&len=$len\">"
      . "<img src=\"pict/but_export.jpg\" border=\"0\" alt=\"Export\" "
      . "width=123 height=20></a></td>\n";

    if ($::USE_NOTES == 1) {
        print "<td align=\"left\">"
          . "<a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::ENTER_DATA&$Args::baseargs&block=$block&len=$len\" "
          . "target=\"_blank\">"
          . "<img src=\"pict/but_addnote.jpg\" border=\"0\" "
          . "width=\"89\" height=20 alt=\"Add Note\"></a></td>\n";
    }
    else {
        print "<td align=\"left\">&nbsp;</td>\n";
    }

    print "</tr></table>\n";

    # Display formats
    print "<table cellspacing=\"0\" cellpadding=\"2\">\n" . "<tr><td>ASCII (";
    if ($sort == $Data::SORT_ASC) {
        print "display - ";
    }
    else {
        print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT_MENU_FR&"
          . "$Args::baseargs&"
          . "sort=$Data::SORT_ASC&block=$block&len=$len\" target=\"_parent\">"
          . "display</a> - \n";
    }

    print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::REPORT&"
      . "$Args::baseargs&sort=$Data::SORT_ASC"
      . "&block=$block&len=$len\" target=\"_blank\">report</a>)</td>\n"
      . "<td>*</td>\n";

    print "<td>Hex (";
    if ($sort == $Data::SORT_HEX) {
        print "display - ";
    }
    else {
        print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT_MENU_FR&"
          . "$Args::baseargs&"
          . "sort=$Data::SORT_HEX&block=$block&len=$len\" target=\"_parent\">"
          . "display</a> - \n";
    }

    print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::REPORT&"
      . "$Args::baseargs&sort=$Data::SORT_HEX"
      . "&block=$block&len=$len\" target=\"_blank\">report</a>)</td>\n"
      . "<td>*</td>\n";

    print "<td>ASCII Strings (";
    if ($sort == $Data::SORT_STR) {
        print "display - ";
    }
    else {
        print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT_MENU_FR&"
          . "$Args::baseargs&"
          . "sort=$Data::SORT_STR&block=$block&len=$len\" target=\"_parent\">"
          . "display</a> - \n";
    }

    print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::REPORT&"
      . "$Args::baseargs&sort=$Data::SORT_STR"
      . "&block=$block&len=$len\" target=\"_blank\">report</a>)</td>\n"
      . "</tr></table>\n";

    # Special case for 'blkls' b.c. we need to specify original data unit size
    local *OUT;
    if ($ftype eq 'blkls') {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype -u $bs  -o $offset -i $imgtype $img $block | '$::FILE_EXE' -z -b -"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype  -o $offset -i $imgtype $img $block | '$::FILE_EXE' -z -b -"
        );
    }
    my $file_type = Exec::read_pipe_line(*OUT);
    close(OUT);

    $file_type = "Error getting file type"
      if ((!defined $file_type) || ($file_type eq ""));

    print "<b>File Type:</b> $file_type<br></center>\n";

    if ($len == 1) {
        print "<b>$Fs::addr_unit{$ftype}:</b> $block<br>\n";
    }
    else {
        my $end = $block + $len - 1;
        print "<b>$Fs::addr_unit{$ftype}" . "s:</b> $block-$end<br>\n";
    }
    if ($Fs::is_fs{$ftype} == 1) {

        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/blkstat' -f $ftype  -o $offset -i $imgtype $img $block"
        );

        my $cnt = 0;
        while ($_ = Exec::read_pipe_line(*OUT)) {

            if ($_ =~ /((Not )?Allocated)/) {
                print "<font color=\"$::DEL_COLOR[0]\">" if (defined $2);
                print "<b>Status:</b> $1<br>";
                print "</font>" if (defined $2);
            }
            elsif ($_ =~ /Group: (\d+)/) {
                print "<b>Group:</b> $1<br>\n";
            }
            $cnt++;
        }
        close(OUT);
        if ($cnt == 0) {
            print "Invalid $Fs::addr_unit{$ftype} address<br>\n";
            return;
        }

        # Make ifind an option
        $url =
            "$::PROGNAME?mod=$::MOD_DATA&view=$Data::CONT_MENU&"
          . "$Args::baseargs&sort=$sort&len=$len&block=$block";
        if ($ifind == 0) {
            print "<a href=\"$url&ifind=1\">Find Meta Data Address</a><br>\n";
        }
        else {
            print "<a href=\"$url&ifind=0\">Hide Meta Data Address</a><br>\n";
            print_ifind();
        }
    }

    # Option to view original if it exists
    if (   ($ftype eq 'blkls')
        && (exists $Caseman::mod2vol{$vol}))
    {
        print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&"
          . "view=$Data::CONT_MENU_FR&${Args::baseargs_novol}"
          . "&vol=$Caseman::mod2vol{$vol}&"
          . "block=$block&sort=$sort&len=$len&btype=$Data::ADDR_BLKLS\" "
          . "target=\"_parent\">View Original</a><br>\n";
    }

    Print::print_html_footer();
    return 0;
}

#Display actual block content
sub content {
    Args::check_sort();

    Print::print_text_header();

    my $sort    = Args::get_sort();
    my $block   = Args::get_block();
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $len = Args::get_len();
    my $bs  = Args::get_unitsize();

    my $range = "";
    if ($len == 0) {
        print "Invalid length: 0\n";
        exit(1);
    }
    elsif ($len == 1) {
        $range = "$Fs::addr_unit{$ftype} $block";
    }
    else {
        my $end = $block + $len - 1;
        $range = "$Fs::addr_unit{$ftype}" . "s $block-$end";
    }
    my $str     = "Contents of $range in $Caseman::vol2sname{$vol}\n\n\n";
    my $log_str = "contents of $range";

    my $usize_str = "";
    $usize_str = " -u $bs "
      if ($ftype eq 'blkls');

    local *OUT;
    if ($sort == $Data::SORT_HEX) {
        print "Hex " . $str;
        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Displaying Hex $log_str");
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str -h -o $offset -i $imgtype $img $block $len"
        );
    }
    elsif ($sort == $Data::SORT_ASC) {
        print "ASCII " . $str;
        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Displaying ASCII $log_str");
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str -a -o $offset -i $imgtype $img $block $len"
        );
    }
    elsif ($sort == $Data::SORT_STR) {
        print "ASCII String " . $str;
        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Displaying string $log_str");
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str -o $offset -i $imgtype $img $block $len | '$::TSKDIR/srch_strings' -a"
        );
    }
    print $_ while ($_ = Exec::read_pipe_data(*OUT, 512));
    close(OUT);

    Print::print_text_footer();

    return 0;
}

sub report {
    Args::check_sort();

    my $sort    = Args::get_sort();
    my $vol     = Args::get_vol('vol');
    my $block   = Args::get_block();
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $len     = Args::get_len();
    my $type;

    if ($len == 0) {
        print("Invalid length: 0");
        exit(1);
    }
    my $bs = Args::get_unitsize();

    my $usize_str = "";
    $usize_str = " -u $bs " if ($ftype eq 'blkls');

    Print::print_text_header("$vol" . "-"
          . "$Fs::addr_unit{$ftype}"
          . "$Args::args{'block'}"
          . ".txt");

    if ($sort == $Data::SORT_ASC) {
        Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Generating ASCII report on data unit $block"
        );
        $type = "ascii";
    }
    elsif ($sort == $Data::SORT_STR) {
        Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Generating ASCII strings report on data unit $block"
        );
        $type = "string";
    }
    elsif ($sort == $Data::SORT_HEX) {
        Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Generating hex report on data unit $block"
        );
        $type = "hex";
    }
    else {
        print "\n\n";
        print "invalid sort value";
        return 1;
    }

    print "                 Autopsy $type $Fs::addr_unit{$ftype} Report\n\n"
      . "-" x 70 . "\n"
      . "                   GENERAL INFORMATION\n\n";

    if ($len == 1) {
        print "$Fs::addr_unit{$ftype}: $Args::args{'block'}\n";
    }
    else {
        my $end = $block + $len - 1;
        print "$Fs::addr_unit{$ftype}" . "s: $Args::args{'block'}-$end\n";
    }
    print "$Fs::addr_unit{$ftype} Size: $bs\n";

    # if (($ftype ne 'blkls') && ($ftype ne 'raw') && ($ftype ne 'swap')) {
    if ($Fs::is_fs{$ftype} == 1) {

        local *OUT;
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/ifind' -f $ftype -d $block  -o $offset -i $imgtype $img"
        );
        my $meta = Exec::read_pipe_line(*OUT);
        close(OUT);

        $meta = "Error getting meta address"
          if ((!defined $meta) || ($meta eq ""));

        if ($meta =~ /^($::REG_META)$/o) {
            my $tmpi = $1;
            print "\nPointed to by $Fs::meta_str{$ftype}: $tmpi\n";

            my $tmpr = $Caseman::vol2mnt{$vol};
            print "Pointed to by files:\n";
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/ffind' -f $ftype -a  -o $offset -i $imgtype $img $tmpi"
            );
            while ($_ = Exec::read_pipe_line(*OUT)) {
                chop;
                if (/^(\*)\s+\/*(.*)$/) {
                    Print::print_output(
                        "  $tmpr$2 (deleted)\n");
                }
                elsif (/^\/(.*)$/) {
                    Print::print_output(
                        "  $tmpr$1\n");
                }
                else {
                    Print::print_output("  $_\n");
                }
            }
            close(OUT);
        }
        else {
            print "Not allocated to any meta data structures\n";
        }
    }    # not blkls

    Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str  -o $offset -i $imgtype $img $block $len | '$::MD5_EXE'"
    );
    my $md5 = Exec::read_pipe_line(*OUT);
    close(OUT);

    $md5 = "Error getting md5"
      if ((!defined $md5) || ($md5 eq ""));

    chop $md5;
    print "MD5 of raw $Fs::addr_unit{$ftype}: $md5\n";

    if ($sort == $Data::SORT_HEX) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str -h  -o $offset -i $imgtype $img $block $len | '$::MD5_EXE'"
        );
    }
    elsif ($sort == $Data::SORT_ASC) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str -a  -o $offset -i $imgtype $img $block $len | '$::MD5_EXE'"
        );
    }
    elsif ($sort == $Data::SORT_STR) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str  -o $offset -i $imgtype $img $block $len | '$::TSKDIR/srch_strings' -a | '$::MD5_EXE'"
        );
    }

    $md5 = Exec::read_pipe_line(*OUT);
    close(OUT);

    $md5 = "Error getting md5"
      if ((!defined $md5) || ($md5 eq ""));

    chop $md5;
    print "MD5 of $type output: $md5\n";

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
      . "Investigator: $Args::args{'inv'}\n"
      . "-" x 70 . "\n"
      . "                        CONTENT\n\n";

    if ($sort == $Data::SORT_HEX) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str -h  -o $offset -i $imgtype $img $block $len"
        );
    }
    elsif ($sort == $Data::SORT_ASC) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str -a  -o $offset -i $imgtype $img $block $len"
        );
    }
    elsif ($sort == $Data::SORT_STR) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype $usize_str  -o $offset -i $imgtype $img $block $len | '$::TSKDIR/srch_strings' -a"
        );
    }
    Print::print_output($_)
      while ($_ = Exec::read_pipe_data(*OUT, 512));
    close(OUT);

    print "\n"
      . "-" x 70 . "\n"
      . "                   VERSION INFORMATION\n\n"
      . "Autopsy Version: $::VER\n";
    print "The Sleuth Kit Version: " . ::get_tskver() . "\n";

    Print::print_text_footer();

    return 0;
}

#
# Display the block allocation list
#
sub list {
    Print::print_html_header("Block Allocation List");

    my $BLKLS_GAP = 500;

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $min = 0;
    $min = Args::get_min() if (exists $Args::args{'min'});
    my $max = $min + $BLKLS_GAP - 1;

    # set fmin to the minimum for the file system
    my $fmin = $min;
    $fmin = $Fs::first_addr{$ftype} if ($min < $Fs::first_addr{$ftype});

    Print::log_host_inv(
        "$Caseman::vol2sname{$vol}: Block Allocation List for $min to $max");
    print "<center><H2>$Fs::addr_unit{$ftype}: $min - $max</H2>";

    my $tmp;

    if ($min - $BLKLS_GAP >= 0) {
        $tmp = $min - $BLKLS_GAP;
        print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::LIST&"
          . "$Args::baseargs&min=$tmp\">"
          . "<img src=\"pict/but_prev.jpg\" alt=\"previous\" "
          . "width=\"89\" height=20 border=\"0\"></a> ";
    }
    $tmp = $min + $BLKLS_GAP;
    print " <a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::LIST&"
      . "$Args::baseargs&min=$tmp\">"
      . "<img src=\"pict/but_next.jpg\" alt=\"next\" "
      . "width=\"89\" height=20 border=\"0\"></a><br>";
    print "</center>\n";

    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkls' -el -f $ftype  -o $offset -i $imgtype $img $fmin-$max"
    );
    while ($_ = Exec::read_pipe_line(*OUT)) {
        if (/^(\d+)\|([af])/) {
            print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&"
              . "view=$Data::CONT_MENU_FR&$Args::baseargs&block=$1\">"
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

    print "<center>\n";
    if ($min - $BLKLS_GAP >= 0) {
        $tmp = $min - $BLKLS_GAP;
        print "<a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::LIST&"
          . "$Args::baseargs&min=$tmp\">"
          . "<img src=\"pict/but_prev.jpg\" alt=\"previous\" "
          . "width=\"89\" height=20 border=\"0\"></a> ";
    }
    $tmp = $min + $BLKLS_GAP;
    print " <a href=\"$::PROGNAME?mod=$::MOD_DATA&view=$Data::LIST&"
      . "$Args::baseargs&min=$tmp\">"
      . "<img src=\"pict/but_next.jpg\" alt=\"next\" "
      . "width=\"89\" height=20 border=\"0\"></a><br>";
    print "</center>\n";

    Print::print_html_footer();
    return 0;
}

sub export {
    my $block   = Args::get_block();
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $len     = Args::get_len();
    my $bs      = Args::get_unitsize();

    Print::print_oct_header(
        "$vol" . "-" . "$Fs::addr_unit{$ftype}" . "$block" . ".raw");

    Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Saving contents of data unit $block (unit size: $bs  number: $len)"
    );

    local *OUT;
    if ($ftype eq 'blkls') {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype -u $bs  -o $offset -i $imgtype $img $block $len"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $ftype  -o $offset -i $imgtype $img $block $len"
        );
    }
    print "$_" while ($_ = Exec::read_pipe_data(*OUT, 512));
    close(OUT);

    Print::print_oct_footer();
    return 0;
}

# Blank Page
sub blank {
    Print::print_html_header("Data Unit Blank Page");
    my $vol   = Args::get_vol('vol');
    my $ftype = $Caseman::vol2ftype{$vol};

    print "<center><h3>Data Unit Mode</h3><br>\n"
      . "Here you can view the contents of any $Fs::addr_unit{$ftype} in the file system.<br>\n"
      . "Enter the address in the field on the left.\n";
    Print::print_html_footer();
    return 0;
}
