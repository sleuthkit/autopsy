#
# File name layer functions
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

# Updated 1/15

package File;

# If the order of these views are changed, then the checks in main
# must be as well
$File::BLANK          = 0;
$File::FRAME          = 1;
$File::DIR_LIST       = 2;
$File::FILE_LIST_DIR  = 3;
$File::FILE_LIST_FILE = 4;
$File::FILE_LIST_DEL  = 5;
$File::FILE_LIST      = 6;
$File::CONT           = 7;
$File::CONT_FR        = 8;
$File::CONT_MENU      = 9;
$File::REPORT         = 10;
$File::EXPORT         = 11;
$File::MD5LIST        = 12;
$File::CONT_IMG       = 13;

$File::REC_NO  = 0;
$File::REC_YES = 1;

sub main {

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $File::FRAME
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    if ($view == $File::BLANK) {
        blank();
        return 0;
    }

    # Check Basic Args
    Args::check_vol('vol');

    # These windows don't need the meta data address
    if ($view < $File::FILE_LIST) {

        if ($view == $File::FRAME) {
            return frame();
        }

        Args::check_dir();
        if ($view == $File::DIR_LIST) {
            return dir_list();
        }
        elsif ($view == $File::FILE_LIST_DIR) {
            return file_list_dir();
        }
        elsif ($view == $File::FILE_LIST_DEL) {
            return file_list_del();
        }
        elsif ($view == $File::FILE_LIST_FILE) {
            return file_list_file();
        }
    }

    # These windows need the meta data address
    Args::check_dir();
    Args::check_meta('meta');

    if ($view < $File::REPORT) {
        if ($view == $File::FILE_LIST) {
            return file_list();
        }
        elsif ($view == $File::CONT) {
            return content();
        }
        elsif ($view == $File::CONT_FR) {
            return content_fr();
        }
        elsif ($view == $File::CONT_MENU) {
            return content_menu();
        }
    }
    else {
        if ($view == $File::REPORT) {
            return report();
        }
        elsif ($view == $File::EXPORT) {
            return export();
        }
        elsif ($view == $File::MD5LIST) {
            return md5list();
        }
        elsif ($view == $File::CONT_IMG) {
            return content_img();
        }
    }

    Print::print_check_err("Invalid File View");
}

# Sorting and display types
my $FIL_SORT_ASC = 0;
my $FIL_SORT_STR = 1;
my $FIL_SORT_HEX = 2;

# Methods of sorting the file listings
my $SORT_DTYPE = 0;    # type according to dentry
my $SORT_ITYPE = 1;    # type according to meta
my $SORT_NAME  = 2;
my $SORT_MOD   = 3;
my $SORT_ACC   = 4;
my $SORT_CHG   = 5;
my $SORT_CRT   = 6;
my $SORT_SIZE  = 7;
my $SORT_GID   = 8;
my $SORT_UID   = 9;
my $SORT_META  = 10;
my $SORT_DEL   = 11;

my $DIRMODE_SHOW   = 1;
my $DIRMODE_NOSHOW = 2;

#
# Make the three frames and fill them in
#
sub frame {
    my $vol = Args::get_vol('vol');
    my $mnt = $Caseman::vol2mnt{$vol};

    my $ftype = $Caseman::vol2ftype{$vol};

    # If we were not given the meta, then look up the root
    unless (exists $Args::args{'meta'}) {
        $Args::args{'meta'} = $Args::enc_args{'meta'} = $Fs::root_meta{$ftype};
    }

    unless (exists $Args::args{'dir'}) {
        $Args::enc_args{'dir'} = $Args::args{'dir'} = "/";
    }

    Args::check_meta('meta');
    Args::check_dir();

    my $meta = Args::get_meta('meta');
    my $dir  = Args::get_dir();

    Print::print_html_header_frameset("$mnt$dir on $Args::args{'vol'}");

    my $sort = $SORT_NAME;
    $sort = $Args::args{'sort'} if (exists $Args::args{'sort'});

    my $dirmode = $DIRMODE_NOSHOW;
    $dirmode = $Args::args{'dirmode'} if (exists $Args::args{'dirmode'});

    print "<frameset cols=\"175,*\">\n";

    # Directory Listing on Left
    my $url =
      "$::PROGNAME?$Args::baseargs&dir=$dir&" . "sort=$sort&dirmode=$dirmode";

    print "<frame src=\"$url&mod=$::MOD_FILE&view=$File::DIR_LIST\">\n";

    # File frameset on right
    print "<frameset rows=\"50%,50%\">\n";

    # File Listings on top
    print
      "<frame src=\"$url&mod=$::MOD_FILE&view=$File::FILE_LIST&meta=$meta\" "
      . "name=\"list\">\n";

    # File Contents
    print "<frame src=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::BLANK&"
      . "$Args::baseargs\" name=\"content\">\n"
      . "</frameset>\n"
      . "</frameset>\n";

    Print::print_html_footer_frameset();
    return 0;
}

#
# Print the directory names for the lhs frame and other
# Search forms
#

sub dir_list {
    Args::check_sort();
    Args::check_dirmode();

    Print::print_html_header("");

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $sort    = Args::get_sort();
    my $dirmode = Args::get_dirmode();
    my $mnt     = $Caseman::vol2mnt{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $lcldir     = "";
    my $prev_plus  = "";    # previous number of '+' directory spacers
    my $prev_fname = "";
    my $prev_meta  = "";

    # Field to enter a directory into:
    print "<p><form action=\"$::PROGNAME\" method=\"get\" target=\"list\">\n"
      . "<center><b>Directory Seek</b></center><br>"
      . "Enter the name of a directory that you want to view.<br>"
      . "<tt>$mnt</tt>"
      . "<input type=\"text\" name=\"dir\" size=24 maxlength=100>\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_FILE\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$File::FILE_LIST_DIR\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
      . "<input type=\"hidden\" name=\"sort\" value=\"$Args::args{'sort'}\">\n"
      . "<input type=\"hidden\" name=\"dirmode\" value=\"$Args::args{'dirmode'}\">\n"
      . Args::make_hidden()
      .

      # View Button
      "<br><input type=\"image\" src=\"pict/but_view.jpg\" "
      . "width=45 height=22 alt=\"View\" border=\"0\"></form>\n";

    # Field to enter a name into:
    print
      "<hr><p><form action=\"$::PROGNAME\" method=\"get\" target=\"list\">\n"
      . "<center><b>File Name Search</b></center><br>"
      . "Enter a Perl regular expression for the file names you want to find.<br><br>\n"
      . "<input type=\"text\" name=\"dir\" size=24 maxlength=100>\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_FILE\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$File::FILE_LIST_FILE\">\n"
      . "<input type=\"hidden\" name=\"vol\" value=\"$Args::args{'vol'}\">\n"
      . "<input type=\"hidden\" name=\"sort\" value=\"$Args::args{'sort'}\">\n"
      . "<input type=\"hidden\" name=\"dirmode\" value=\"$Args::args{'dirmode'}\">\n"
      . Args::make_hidden()
      . "<br>\n"
      .

      # Search Button
      "<input type=\"image\" src=\"pict/but_search.jpg\" "
      . "width=61 height=22 alt=\"Search\" border=\"0\"></form>\n";

    print "<p><hr><p>\n";

    my $base_url = "$::PROGNAME?$Args::baseargs&sort=$sort";

    # All deleted files button
    print "<a href=\"$base_url&mod=$::MOD_FILE&view=$File::FILE_LIST_DEL&"
      . "dir=&dirmode=$dirmode\" target=\"list\">"
      . "<img border=\"0\" src=\"pict/file_b_alldel.jpg\" width=\"127\" "
      . "alt=\"Show All Deleted Files\">"
      . "</a><p>\n";

    # The dirmode arg shows if we should expand the whole directory listing
    # or not
    if ($dirmode == $DIRMODE_NOSHOW) {
        print "<a href=\"$base_url&mod=$::MOD_FILE&view=$File::FRAME&"
          . "dirmode=$DIRMODE_SHOW\" target=\"_parent\">"
          . "<img src=\"pict/file_b_expand.jpg\" alt=\"Expand All Directories\" "
          . "border=\"0\"></a><p><hr>\n";

        return;
    }
    else {
        print "<a href=\"$base_url&mod=$::MOD_FILE&view=$File::FRAME&"
          . "dirmode=$DIRMODE_NOSHOW\" target=\"_parent\">"
          . "<img src=\"pict/file_b_hide.jpg\" alt=\"Hide All Directories\" "
          . "border=\"0\"></a><p><hr>\n";
    }

    $base_url .= "&dirmode=$dirmode";

    Print::log_host_inv("$Args::args{'vol'}: List of all directories");

    # We need to maintain state to create dir and this is done by
    # counting the + marks.
    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/fls' -f $ftype -ruD -o $offset -i $imgtype $img");

    # Print root
    my $url =
        "$base_url&mod=$::MOD_FILE&view=$File::FILE_LIST&"
      . "meta=$Fs::root_meta{$ftype}&dir=";
    print "<p><a href=\"$url\" target=\"list\">$mnt</a><br>\n";

    while ($_ = Exec::read_pipe_line(*OUT)) {
        if (
/^(\*)?(\+*)\s*[\-d]\/[\-d]\s*(\d+)\-?\d*\-?\d*\s*(\(realloc\))?:\t(.+)$/
          )
        {

            my $del   = $1;
            my $plus  = $2;
            my $meta  = $3;
            my $re    = $4;
            my $fname = $5;

            # Adjust the dir value using the '++' values to determine
            # how "deep" we are
            unless ($prev_plus eq $plus) {

                # are we in 1 more
                if ($plus eq $prev_plus . '+') {
                    $lcldir .= ($prev_fname . "/");
                }

                # we are back (at least one)
                elsif (defined $plus) {
                    my @dirs = split('/', $lcldir);
                    my $idx = -1;
                    $lcldir = "";

                    while (($idx = index($plus, '+', $idx + 1)) != -1) {
                        $lcldir .= ($dirs[$idx] . "/");
                    }
                }
            }

            $prev_plus  = $plus;
            $prev_fname = $fname;
            $prev_meta  = $meta;

            $url =
                "$base_url&mod=$::MOD_FILE&view=$File::FILE_LIST&"
              . "meta=$meta&dir="
              . Args::url_encode($lcldir . $fname . "/");

            print "<font color=\"$::DEL_COLOR[0]\">" if defined $del;
            print "+$plus<a href=\"$url\" target=\"list\"><tt>/"
              . Print::html_encode($fname)
              . "</tt></a><br>\n";
            print "</font>" if defined $del;
        }
    }
    close(OUT);
    Print::print_html_footer();
    return 0;

};    # end of FIL_DIR

# Print the files and directories for the upper rhs frame
# These can be sorted in any format
#
# We need to find a way to cache this data
#
sub file_list {
    Args::check_sort();
    Args::check_dirmode();

    my $vol     = Args::get_vol('vol');
    my $sort    = Args::get_sort();
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $meta    = Args::get_meta('meta');
    my $mnt     = $Caseman::vol2mnt{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $fname = "$mnt$Args::args{'dir'}";
    $fname =~ s/\/\//\//g;

    my $sp = "&nbsp;&nbsp;";

    Print::print_html_header("Entries in $fname");

    my (@itype, @dtype, @name, @mod, @acc, @chg, @crt, @size, @gid, @uid,
        @meta);
    my (@dir, @entry, @del, @realloc, @meta_int);

    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    Print::log_host_inv(
        "$Caseman::vol2sname{$vol}: Directory listing of $fname ($meta)");

    local *OUT;

    # execute command
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/fls' -f $ftype -la $tz -s $Caseman::ts -o $offset -i $imgtype $img $meta"
    );

    # Make the big table, small table, and start the current directory

    my $iurl =
"$::PROGNAME?$Args::baseargs&dirmode=$Args::enc_args{'dirmode'}&sort=$sort";

    # base number of columns in table
    my $cols = 15;
    $cols += 2 if ($Fs::has_ctime{$ftype});
    $cols += 2 if ($Fs::has_crtime{$ftype});
    $cols += 2 if ($Fs::has_mtime{$ftype});

    print <<EOF1;
<!-- Big Table -->
<table cellspacing=\"0\" cellpadding=\"2\" border=0>

<!-- Small Table -->
<tr>
  <td colspan=$cols>
    <table border=0 align=\"left\" cellspacing=\"0\" cellpadding=\"2\" width=500>
    <tr>
      <td colspan=2><b>Current Directory:</b> <tt>

        <a href=\"${iurl}&mod=$::MOD_FILE&view=$File::FILE_LIST&meta=$Fs::root_meta{$ftype}&dir=\">$mnt</a>&nbsp;

EOF1

    # Each file in the path will get its own link
    $iurl .= "&mod=$::MOD_FILE&view=$File::FILE_LIST_DIR";
    my $path = "";
    my @dir_split = split('/', $Args::args{'dir'});
    while (scalar @dir_split > 1) {
        my $d = shift @dir_split;

        next if ($d eq '');

        $path .= "$d/";
        print "        <a href=\"${iurl}&dir=$path\">/${d}/</a>&nbsp;\n";
    }
    print "        /$dir_split[0]/&nbsp;\n"
      if (scalar @dir_split == 1);

    print "      </tt></td>\n" . "    </tr>\n";

    # Add Note Button
    $iurl =
"&$Args::baseargs&dir=$Args::enc_args{'dir'}&meta=$Args::enc_args{'meta'}";
    if ($::USE_NOTES == 1) {

        print <<EOF2;
    <tr>
      <td width=\"100\" align=left>
        <a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::ENTER_FILE$iurl\" target=\"_blank\">
          <img border=\"0\" src=\"pict/but_addnote.jpg\" width=\"89\" height=20 alt=\"Add Note About Directory\">
         </a>
      </td>
EOF2

    }

    # Generate MD5 List Button
    print <<EOF3;

      <td width=\"206\" align=left>
        <a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::MD5LIST$iurl\" target=\"_blank\">
          <img border=\"0\" src=\"pict/file_b_md5list.jpg\" width=\"206\" alt=\"Generate list of MD5 values\">
        </a>
      </td>
    </tr>
  <!-- END of Little Table -->
  </table>
  </td>
</tr>
<tr>
  <td colspan=$cols><hr></td>
</tr>

EOF3

    # Make the Table and Headers
    my $url =
        "$::PROGNAME?mod=$::MOD_FILE&view=$File::FRAME&"
      . "$Args::baseargs&meta=$Args::enc_args{'meta'}"
      . "&dir=$Args::enc_args{'dir'}&dirmode=$Args::enc_args{'dirmode'}";

    print "<tr valign=\"MIDDLE\" " . "background=\"$::YEL_PIX\">\n";

    # Print the Headers - If the sorting mode is set to it, then don't
    # make it a link and print a different button
    if ($sort == $SORT_DEL) {
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_del_cur.jpg\" "
          . "width=\"49\" height=20 "
          . "alt=\"Deleted Files\">"
          . "</td>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_DEL";
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<a href=\"$iurl\" target=\"_parent\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_del_link.jpg\" "
          . "width=\"28\" height=20 "
          . "alt=\"Deleted Files\">"
          . "</a></td>\n";
    }

    # type only gets one column for two 'types'
    print "  <td background=\"$::YEL_PIX\">$sp</td>\n"
      . "  <th align=\"center\" background=\"$::YEL_PIX\">"
      . "&nbsp;&nbsp;Type&nbsp;&nbsp;<br>";

    if ($sort == $SORT_DTYPE) {
        print "dir";
    }
    else {
        $iurl = $url . "&sort=$SORT_DTYPE";
        print "<a href=\"$iurl\" target=\"_parent\">dir</a>";
    }

    print "&nbsp;/&nbsp;";

    if ($sort == $SORT_ITYPE) {
        print "in</th>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_ITYPE";
        print "<a href=\"$iurl\" target=\"_parent\">in</a></th>\n";
    }

    print "  <td background=\"$::YEL_PIX\">$sp</td>\n";

    if ($sort == $SORT_NAME) {
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_nam_cur.jpg\" "
          . "width=\"76\" height=20 "
          . "alt=\"File Name\">"
          . "</td>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_NAME";
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<a href=\"$iurl\" target=\"_parent\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_nam_link.jpg\" "
          . "width=\"50\" height=20 "
          . "alt=\"File Name\">"
          . "</a></td>\n";
    }

    print "  <td background=\"$::YEL_PIX\">$sp</td>\n";

    # Modified / Written
    if ($Fs::has_mtime{$ftype}) {
        if ($sort == $SORT_MOD) {
            print "  <td align=\"left\" background=\"$::YEL_PIX\">"
              . "<img border=\"0\" "
              . "src=\"pict/file_h_wr_cur.jpg\" "
              . "width=\"89\" height=20 "
              . "alt=\"Modified/Written Time\">"
              . "</td>\n";
        }
        else {
            $iurl = $url . "&sort=$SORT_MOD";
            print "  <td align=\"left\" background=\"$::YEL_PIX\">"
              . "<a href=\"$iurl\" target=\"_parent\">"
              . "<img border=\"0\" "
              . "src=\"pict/file_h_wr_link.jpg\" "
              . "width=\"60\" height=20 "
              . "alt=\"Modified/Written Time\">"
              . "</a></td>\n";
        }
        print "  <td background=\"$::YEL_PIX\">$sp</td>\n";
    }

    # Accessed
    if ($sort == $SORT_ACC) {
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_acc_cur.jpg\" "
          . "width=\"90\" height=20 "
          . "alt=\"Access Time\">"
          . "</td>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_ACC";
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<a href=\"$iurl\" target=\"_parent\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_acc_link.jpg\" "
          . "width=\"66\" height=20 "
          . "alt=\"Access Time\">"
          . "</a></td>\n";
    }

    print "  <td background=\"$::YEL_PIX\">$sp</td>\n";

    # Change
    if ($Fs::has_ctime{$ftype}) {
        if ($sort == $SORT_CHG) {
            print "  <td align=\"left\" background=\"$::YEL_PIX\">"
              . "<img border=\"0\" "
              . "src=\"pict/file_h_chg_cur.jpg\" "
              . "width=\"90\" height=20 "
              . "alt=\"Change Time\">"
              . "</td>\n";
        }
        else {
            $iurl = $url . "&sort=$SORT_CHG";
            print "  <td align=\"left\" background=\"$::YEL_PIX\">"
              . "<a href=\"$iurl\" target=\"_parent\">"
              . "<img border=\"0\" "
              . "src=\"pict/file_h_chg_link.jpg\" "
              . "width=\"62\" height=20 "
              . "alt=\"Change Time\">"
              . "</a></td>\n";
        }
        print "  <td background=\"$::YEL_PIX\">$sp</td>\n";
    }

    # Create
    if ($Fs::has_crtime{$ftype}) {
        if ($sort == $SORT_CRT) {
            print "  <td align=\"left\" background=\"$::YEL_PIX\">"
              . "<img border=\"0\" "
              . "src=\"pict/file_h_cre_cur.jpg\" "
              . "width=\"84\" height=20 "
              . "alt=\"Create Time\">"
              . "</td>\n";
        }
        else {
            $iurl = $url . "&sort=$SORT_CRT";
            print "  <td align=\"left\" background=\"$::YEL_PIX\">"
              . "<a href=\"$iurl\" target=\"_parent\">"
              . "<img border=\"0\" "
              . "src=\"pict/file_h_cre_link.jpg\" "
              . "width=\"59\" height=20 "
              . "alt=\"Create Time\">"
              . "</a></td>\n";
        }
        print "  <td background=\"$::YEL_PIX\">$sp</td>\n";
    }

    # Size
    if ($sort == $SORT_SIZE) {
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_siz_cur.jpg\" "
          . "width=\"53\" height=20 "
          . "alt=\"Size\">"
          . "</td>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_SIZE";
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<a href=\"$iurl\" target=\"_parent\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_siz_link.jpg\" "
          . "width=\"31\" height=20 "
          . "alt=\"Size\">"
          . "</a></td>\n";
    }

    print "  <td background=\"$::YEL_PIX\">$sp</td>\n";

    # UID
    if ($sort == $SORT_UID) {
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_uid_cur.jpg\" "
          . "width=\"49\" height=20 "
          . "alt=\"UID\">"
          . "</td>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_UID";
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<a href=\"$iurl\" target=\"_parent\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_uid_link.jpg\" "
          . "width=\"27\" height=20 "
          . "alt=\"UID\">"
          . "</a></td>\n";
    }

    print "  <td background=\"$::YEL_PIX\">$sp</td>\n";

    # GID
    if ($sort == $SORT_GID) {
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_gid_cur.jpg\" "
          . "width=\"49\" height=20 "
          . "alt=\"GID\">"
          . "</td>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_GID";
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<a href=\"$iurl\" target=\"_parent\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_gid_link.jpg\" "
          . "width=\"28\" height=20 "
          . "alt=\"GID\">"
          . "</a></td>\n";
    }

    print "  <td background=\"$::YEL_PIX\">$sp</td>\n";

    # meta
    if ($sort == $SORT_META) {
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_meta_cur.jpg\" "
          . "width=\"62\" height=20 "
          . "alt=\"Meta\">"
          . "</td>\n";
    }
    else {
        $iurl = $url . "&sort=$SORT_META";
        print "  <td align=\"left\" background=\"$::YEL_PIX\">"
          . "<a href=\"$iurl\" target=\"_parent\">"
          . "<img border=\"0\" "
          . "src=\"pict/file_h_meta_link.jpg\" "
          . "width=\"41\" height=20 "
          . "alt=\"Meta\">"
          . "</a></td>\n";
    }
    print "</tr>\n";

    my $cnt = 0;
    my %seen;

    # sort fls into arrays
    while ($_ = Exec::read_pipe_line(*OUT)) {
        if (
/^($::REG_MTYPE)\/($::REG_MTYPE)\s*(\*?)\s*($::REG_META)(\(realloc\))?:\t(.+?)\t($::REG_DATE)\t($::REG_DATE)\t($::REG_DATE)\t($::REG_DATE)\t(\d+)\t(\d+)\t(\d+)$/o
          )
        {

            my $lcldir = $Args::args{'dir'};
            $dtype[$cnt]   = $1;
            $itype[$cnt]   = $2;
            $del[$cnt]     = $3;
            $meta[$cnt]    = $4;
            $realloc[$cnt] = "";
            $realloc[$cnt] = $5 if (defined $5);
            $name[$cnt]    = $6;
            $mod[$cnt]     = $7;
            $acc[$cnt]     = $8;
            $chg[$cnt]     = $9;
            $crt[$cnt]     = $10;
            $size[$cnt]    = $11;
            $gid[$cnt]     = $12;
            $uid[$cnt]     = $13;

            if ($meta[$cnt] =~ /^(\d+)(-\d+(-\d+)?)?$/) {
                $meta_int[$cnt] = $1;
            }
            else {
                $meta_int[$cnt] = $meta[$cnt];
            }

            # See if we have already seen this file yet
            if (exists $seen{"$name[$cnt]-$meta[$cnt]"}) {
                my $prev_cnt = $seen{"$name[$cnt]-$meta[$cnt]"};

                # If we saw it while it was deleted, & it
                # is now undel, then update it
                if (   ($del[$cnt] eq "")
                    && ($del[$prev_cnt] eq '*'))
                {
                    $del[$prev_cnt] = $del[$cnt];
                }
                next;

                # Add it to the seen list
            }
            else {
                $seen{"$name[$cnt]-$meta[$cnt]"} = $cnt;
            }

            # We must adjust the dir for directories
            if ($itype[$cnt] eq 'd') {

                # special cases for .. and .
                if ($name[$cnt] eq '..') {
                    my @dirs = split('/', $lcldir);
                    my $i;
                    $lcldir = "";
                    for ($i = 0; $i < $#dirs; $i++) {
                        $lcldir .= ($dirs[$i] . '/');
                    }
                }
                elsif ($name[$cnt] ne '.') {
                    $lcldir .= ($name[$cnt] . '/');
                }
                $name[$cnt] .= '/';
            }
            else {
                $lcldir .= $name[$cnt];
            }

            # format the date so that the time and time zone are on the
            # same line
            $mod[$cnt] = "$1&nbsp;$2"
              if ($mod[$cnt] =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);

            $acc[$cnt] = "$1&nbsp;$2"
              if ($acc[$cnt] =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);

            $chg[$cnt] = "$1&nbsp;$2"
              if ($chg[$cnt] =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);

            $crt[$cnt] = "$1&nbsp;$2"
              if ($crt[$cnt] =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);

            $dir[$cnt]   = Args::url_encode($lcldir);
            $entry[$cnt] = $cnt;
            $cnt++;

        }

        # We missed it for some reason
        else {
            print
"<tr><td colspan=10>Error Parsing File (Invalid Characters?):<br>$_</td></tr>\n";
        }
    }
    close(OUT);

    if ($cnt == 0) {
        print "</table>\n<center>No Contents</center>\n";
        return 0;
    }

    # Sort the above array based on the sort argument
    my @sorted;    # an array of indices

    if ($sort == $SORT_DTYPE) {
        @sorted =
          sort { $dtype[$a] cmp $dtype[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_ITYPE) {
        @sorted =
          sort { $itype[$a] cmp $itype[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_NAME) {
        @sorted = sort { lc($name[$a]) cmp lc($name[$b]) } @entry;
    }
    elsif ($sort == $SORT_MOD) {
        @sorted =
          sort { $mod[$a] cmp $mod[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_ACC) {
        @sorted =
          sort { $acc[$a] cmp $acc[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_CHG) {
        @sorted =
          sort { $chg[$a] cmp $chg[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_CRT) {
        @sorted =
          sort { $crt[$a] cmp $crt[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_SIZE) {
        @sorted =
          sort { $size[$a] <=> $size[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_UID) {
        @sorted =
          sort { $uid[$a] <=> $uid[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_GID) {
        @sorted =
          sort { $gid[$a] <=> $gid[$b] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }
    elsif ($sort == $SORT_META) {
        @sorted = sort {
            $meta_int[$a] <=> $meta_int[$b]
              or lc($name[$a]) cmp lc($name[$b])
        } @entry;
    }
    elsif ($sort == $SORT_DEL) {
        @sorted =
          sort { $del[$b] cmp $del[$a] or lc($name[$a]) cmp lc($name[$b]) }
          @entry;
    }

    # print them based on sorting
    my $row = 0;
    foreach my $i (@sorted) {
        my $url;
        my $target;
        my $color;
        my $lcolor;
        if ($del[$i] eq '*') {
            $color =
              "<font color=\"" . $::DEL_COLOR[$realloc[$i] ne ""] . "\">";
            $lcolor = $color;
        }
        else {
            $color  = "<font color=\"$::NORM_COLOR\">";
            $lcolor = "<font color=\"$::LINK_COLOR\">";
        }

        # directories have different targets and view values
        if ($itype[$i] eq 'd') {
            $target = "list";
            $url    =
                "$::PROGNAME?mod=$::MOD_FILE&view=$File::FILE_LIST&"
              . "$Args::baseargs&meta=$meta_int[$i]"
              . "&sort=$sort&dir=$dir[$i]&dirmode=$Args::enc_args{'dirmode'}";
        }
        else {
            $target = "content";
            $url    =
                "$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_FR&"
              . "$Args::baseargs&meta=$meta[$i]"
              . "&sort=$sort&dir=$dir[$i]&dirmode=$Args::enc_args{'dirmode'}";
            if ($del[$i] eq '*') {
                $url .= "&recmode=$File::REC_YES";
            }
            else {
                $url .= "&recmode=$File::REC_NO";
            }
        }

        if (($row % 2) == 0) {
            print
"<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR\">\n  <td align=\"center\">";
        }
        else {
            print
"<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR_TABLE\">\n  <td align=\"center\">";
        }

        print "<img src=\"pict/file_b_check.jpg\" border=\"0\">\n"
          if ($del[$i] eq '*');

        print "</td>\n"
          . "  <td>$sp</td>\n"
          . "  <td align=\"center\">${color}$dtype[$i]&nbsp;/&nbsp;$itype[$i]</td>\n"
          . "  <td>$sp</td>\n";

        # for valid files and directories make a link
        # Special rule for $OrphanFiles directory and HFS directories, which have a size of 0
        if (
               ($meta_int[$i] >= $Fs::first_meta{$ftype})
            && (($size[$i] > 0) || (($name[$i] =~ /^\$Orphan/) && ($itype[$i] eq 'd')) || (($ftype =~ /hfs/) && ($itype[$i] eq 'd')))
            && (   ($itype[$i] eq 'r')
                || ($itype[$i] eq 'd')
                || ($itype[$i] eq 'v'))
          )
        {
            print "  <td><a href=\"$url\" target=\"$target\">$lcolor";
        }
        else {
            print "  <td>$color";
        }
        print "<tt>"
          . Print::html_encode($name[$i])
          . "</tt></td>\n"
          . "  <td>$sp</td>\n";
        print "  <td>${color}$mod[$i]</td>\n" . "  <td>$sp</td>\n"
          if ($Fs::has_mtime{$ftype});
        print "  <td>${color}$acc[$i]</td>\n" . "  <td>$sp</td>\n";
        print "  <td>${color}$chg[$i]</td>\n" . "  <td>$sp</td>\n"
          if ($Fs::has_ctime{$ftype});
        print "  <td>${color}$crt[$i]</td>\n" . "  <td>$sp</td>\n"
          if ($Fs::has_crtime{$ftype});
        print "  <td>${color}$size[$i]</td>\n"
          . "  <td>$sp</td>\n"
          . "  <td>${color}$uid[$i]</td>\n"
          . "  <td>$sp</td>\n"
          . "  <td>${color}$gid[$i]</td>\n"
          . "  <td>$sp</td>\n";

        # for a valid meta, make a link to meta browsing mode
        if ($meta_int[$i] >= $Fs::first_meta{$ftype}) {
            my $iurl =
"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_META&$Args::baseargs&meta=$meta[$i]";
            print "<td><a href=\"$iurl\" target=\"_top\">$lcolor";
        }
        else {
            print "<td>$color";
        }
        print "$meta[$i]</a> $realloc[$i]</td>\n</tr>\n";

        $row++;
    }

    print "</table>\n";
    Print::print_html_footer();
    return 0;

};    #end of FIL_LIST

# This takes a directory name as an argument and converts it to
# the meta value and calls FIL_LIST
#
# The meta value can be anything when this is run, it will be
# overwritten
sub file_list_dir {

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $dir     = Args::get_dir();
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    Print::log_host_inv(
        "$Args::args{'vol'}: Finding meta data address for $dir");

    # Use 'ifind -n' to get the meta data address for the given name
    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/ifind' -f $ftype -n '$dir'  -o $offset -i $imgtype $img");
    my $meta;

    while ($_ = Exec::read_pipe_line(*OUT)) {
        $meta = $1
          if (/^($::REG_META)$/);
    }
    close(OUT);

    Print::print_check_err("Error finding meta data address for $dir")
      unless (defined $meta);

    Print::print_check_err("Error finding meta data address for $dir: $meta")
      unless ($meta =~ /^$::REG_META$/);

    # Verify it is a directory with istat
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/istat' -f $ftype  -o $offset -i $imgtype $img $meta");

    while ($_ = Exec::read_pipe_line(*OUT)) {

        # This is a directory
        if (   (/mode:\s+d/)
            || (/File Attributes: Directory/)
            || (/^Flags:.*?Directory/))
        {
            close(OUT);

            # Set the meta variables
            $Args::enc_args{'meta'} = $Args::args{'meta'} = $meta;

            $Args::args{'dir'} .= "/"
              unless ($Args::args{'dir'} =~ /.*?\/$/);
            $Args::enc_args{'dir'} .= "/"
              unless ($Args::enc_args{'dir'} =~ /.*?\/$/);

            # List the directory contents
            file_list();

            return 0;
        }
    }
    close(OUT);

    # This is not a directory, so just give a link
    Print::print_html_header("");

    my $meta_int = $meta;
    $meta_int = $1 if ($meta_int =~ /(\d+)-\d+(-\d+)?/);

    my $recmode = $File::REC_NO;
    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/ils' -f $ftype -e  -o $offset -i $imgtype $img $meta_int");
    while ($_ = Exec::read_pipe_line(*OUT)) {
        chop;
        next unless ($_ =~ /^$meta/);
        if ($_ =~ /^$meta\|f/) {
            $recmode = $File::REC_YES;
        }
        elsif ($_ =~ /^$meta\|a/) {
            $recmode = $File::REC_NO;
        }
        else {
            Print::print_err("Error parsing ils output: $_");
        }
    }
    close(OUT);

    print <<EOF;

<tt>$dir</tt> (
<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_META&$Args::baseargs&meta=$meta&recmode=$recmode\" target=\"_top\">
meta $meta</a>) is not a directory.

<p>
<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_FR&$Args::baseargs&meta=$meta&dir=$dir&recmode=$recmode\" target=\"content\">
  <img src=\"pict/but_viewcont.jpg\" height=20 width=123 alt=\"view contents\" border=\"0\">
</a>

EOF

    Print::print_html_footer();
    return 1;

}

# List the files that meet a certain pattern
sub file_list_file {
    Args::check_sort();
    Args::check_dirmode();
    Args::check_dir();

    my $vol     = Args::get_vol('vol');
    my $mnt     = $Caseman::vol2mnt{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $meta    = $Fs::root_meta{$ftype};
    my $sort    = Args::get_sort();
    my $dirmode = Args::get_dirmode();
    my $dir     = Args::get_dir();

    Print::print_html_header(
        "Filtered files on $Caseman::vol2sname{$vol} $mnt");

    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    my $sp = "&nbsp;&nbsp;";

    Print::log_host_inv(
        "$Caseman::vol2sname{$vol}: Listing all files with $dir");

    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/fls' -f $ftype -lpr $tz -s $Caseman::ts -o $offset -i $imgtype $img $meta"
    );

    print "<b>All files with \'<tt>$dir</tt>\' in the name</b><p>\n"
      . "<a href=\"$::PROGNAME?$Args::baseargs&dirmode=$Args::enc_args{'dirmode'}"
      . "&sort=$sort&mod=$::MOD_FILE&view=$File::FILE_LIST"
      . "&meta=$Fs::root_meta{$ftype}&dir=\">"
      . "<img border=\"0\" src=\"pict/file_b_allfiles.jpg\" width=\"112\" "
      . "alt=\"Show All Files\"></a>\n" . "<hr>"
      . "<table cellspacing=\"0\" cellpadding=\"2\"  border=0>\n"
      . "<tr valign=\"MIDDLE\" align=\"left\" "
      . "background=\"$::YEL_PIX\">\n";

    # deleted
    print "<td align=\"left\">"
      . "<img border=\"0\" src=\"pict/file_h_del_link.jpg\" "
      . "width=\"28\" height=20 alt=\"Deleted Files\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # Type
    print "<th align=\"center\">&nbsp;&nbsp;Type&nbsp&nbsp;<br>"
      . "dir&nbsp;/&nbsp;in</th>"
      . "<td>$sp</td>\n";

    # Name
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_nam_link.jpg\" "
      . "width=\"50\" height=20 "
      . "alt=\"File Name\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # Mod / Written
    if ($Fs::has_mtime{$ftype}) {
        print "  <td><img border=\"0\" "
          . "src=\"pict/file_h_wr_link.jpg\" "
          . "width=\"60\" "
          . "alt=\"Written Time\">"
          . "</td>\n"
          . "<td>$sp</td>\n";
    }

    # Access
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_acc_link.jpg\" "
      . "width=\"66\" height=20 "
      . "alt=\"Access Time\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # Change
    if ($Fs::has_ctime{$ftype}) {
        print "  <td><img border=\"0\" "
          . "src=\"pict/file_h_chg_link.jpg\" "
          . "width=\"62\" "
          . "alt=\"Change Time\">"
          . "</td>\n"
          . "<td>$sp</td>\n";
    }

    # Create
    if ($Fs::has_crtime{$ftype}) {
        print "  <td><img border=\"0\" "
          . "src=\"pict/file_h_cre_link.jpg\" "
          . "width=\"59\" "
          . "alt=\"Create Time\">"
          . "</td>\n"
          . "<td>$sp</td>\n";
    }

    # Size
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_siz_link.jpg\" "
      . "width=\"31\" height=20 "
      . "alt=\"Size\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # UID
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_uid_link.jpg\" "
      . "width=\"27\" height=20 "
      . "alt=\"UID\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # GID
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_gid_link.jpg\" "
      . "width=\"28\" height=20 "
      . "alt=\"GID\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # Meta
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_meta_link.jpg\" "
      . "width=\"41\" height=20 "
      . "alt=\"Meta\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    my $row = 0;
    while ($_ = Exec::read_pipe_line(*OUT)) {
        if (
/^($::REG_MTYPE)\/($::REG_MTYPE)\s*(\*?)\s*($::REG_META)(\(realloc\))?:\t(.+?)\t($::REG_DATE)\t($::REG_DATE)\t($::REG_DATE)\t($::REG_DATE)\t(\d+)\t(\d+)\t(\d+)$/o
          )
        {

            # We have to remove the / from the beginning of the file name so
            # save all values so they aren't lost
            my $dt = $1;
            my $it = $2;
            my $d  = $3;
            my $i  = $4;
            my $r  = 0;
            $r = 1 if (defined $5);
            my $n  = $6;
            my $m  = $7;
            my $a  = $8;
            my $c  = $9;
            my $cr = $10;
            my $s  = $11;
            my $g  = $12;
            my $u  = $13;

            if ($n =~ /^\/(.*)/) {
                $n = $1;
            }

            my $p = "";
            my $f = $n;

            if ($n =~ /^(.+?)\/([^\/]+)$/) {
                $p = $1;
                $f = $2;
            }

            next unless ($f =~ /$dir/i);

            my $enc_n = Args::url_encode($n);
            my $iurl  =
"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_META&$Args::baseargs&meta=$i";
            my $i_int = $i;
            $i_int = $1 if ($i =~ /(\d+)-\d+-\d+/);

            if (($row % 2) == 0) {
                print "<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR\">\n";
            }
            else {
                print "<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR_TABLE\">\n";
            }

            print "<td align=\"left\">\n";

            my $color;
            my $lcolor;
            if ($d eq '*') {
                $color  = "<font color=\"" . $::DEL_COLOR[$r] . "\">";
                $lcolor = $color;

                print "<img src=\"pict/file_b_check.jpg\" border=\"0\">\n";

            }
            else {
                $color  = "<font color=\"$::NORM_COLOR\">";
                $lcolor = "<font color=\"$::LINK_COLOR\">";
                print "&nbsp;";
            }

            print "</td><td>$sp</td>";

            print "<td align=\"center\">$color"
              . "$dt&nbsp;/&nbsp;$it</td>"
              . "<td>$sp</td>\n";

            if ($it eq 'd') {
                my $url =
                    "$::PROGNAME?mod=$::MOD_FILE&"
                  . "view=$File::FILE_LIST&$Args::baseargs&meta=$i"
                  . "&sort=$sort&dir=$enc_n&dirmode=$dirmode";

                print "<td>";
                if ($i_int >= $Fs::first_meta{$ftype}) {
                    print "<a href=\"$url\" target=\"_self\">$lcolor";
                }
                else {
                    print "$color";
                }
                print "<tt>"
                  . Print::html_encode($mnt . $n)
                  . "</tt></td>"
                  . "<td>$sp</td>\n";
            }
            else {
                my $url =
                    "$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_FR&"
                  . "$Args::baseargs&meta=$i&sort=$sort&dir=$enc_n";

                if ($d eq '*') {
                    $url .= "&recmode=$File::REC_YES";
                }
                else {
                    $url .= "&recmode=$File::REC_NO";
                }

                print "<td>";
                if (($i_int >= $Fs::first_meta{$ftype}) && ($it eq 'r')) {
                    print "<a href=\"$url\" target=\"content\">$lcolor";
                }
                else {
                    print "$color";
                }
                print "<tt>$mnt$n</tt></td>" . "<td>$sp</td>\n";
            }

            $m = "$1&nbsp;$2"
              if ($m =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);
            $a = "$1&nbsp;$2"
              if ($a =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);
            $c = "$1&nbsp;$2"
              if ($c =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);
            $cr = "$1&nbsp;$2"
              if ($cr =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);

            print "<td>$color$m</td>" . "<td>$sp</td>\n"
              if ($Fs::has_mtime{$ftype});

            print "<td>$color$a</td>" . "<td>$sp</td>\n";
            print "<td>$color$c</td>" . "<td>$sp</td>\n"
              if ($Fs::has_ctime{$ftype});
            print "<td>$color$cr</td>" . "<td>$sp</td>\n"
              if ($Fs::has_crtime{$ftype});

            print "<td>$color$s</td>"
              . "<td>$sp</td>\n"
              . "<td>$color$g</td>"
              . "<td>$sp</td>\n"
              . "<td>$color$u</td>"
              . "<td>$sp</td>\n";

            print "<td>";
            if ($i_int >= $Fs::first_meta{$ftype}) {
                print "<a href=\"$iurl\" target=\"_top\">";
                print "$lcolor$i</a>";
            }
            else {
                print "$color$i";
            }
            print " (realloc)" if $r;
            print "</td></tr>\n";
        }
        else {
            print "Error Parsing File (invalid characters?)<br>: $_\n<br>";
        }

        $row++;
    }
    close(OUT);
    print "</table>\n";

    print "<center>No files found with that pattern</center>\n"
      if ($row == 0);

    Print::print_html_footer();
    return 0;
}

# display deleted files only
#
# Sorting should be added to this
sub file_list_del {
    Args::check_sort();
    Args::check_dirmode();

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $mnt     = $Caseman::vol2mnt{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $meta    = $Fs::root_meta{$ftype};
    my $sort    = Args::get_sort();
    my $dirmode = Args::get_dirmode();

    Print::print_html_header("Deleted files on $Caseman::vol2sname{$vol} $mnt");

    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    my $sp = "&nbsp;&nbsp;";

    Print::log_host_inv("$Caseman::vol2sname{$vol}: Listing all deleted files");

    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/fls' -f $ftype -ldr $tz -s $Caseman::ts  -o $offset -i $imgtype $img $meta"
    );

    print "<b>All Deleted Files</b><p><hr>"
      . "<table cellspacing=\"0\" cellpadding=\"2\"  border=0>\n"
      . "<tr valign=\"MIDDLE\" align=\"left\" "
      . "background=\"$::YEL_PIX\">\n";

    # Type
    print "<th align=\"center\">&nbsp;&nbsp;Type&nbsp&nbsp;<br>"
      . "dir&nbsp;/&nbsp;in</th>"
      . "<td>$sp</td>\n";

    # Name
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_nam_link.jpg\" "
      . "width=\"50\" height=20 "
      . "alt=\"File Name\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # Mod / Written
    if ($Fs::has_mtime{$ftype}) {
        print "  <td><img border=\"0\" "
          . "src=\"pict/file_h_wr_link.jpg\" "
          . "width=\"60\" "
          . "alt=\"Written Time\">"
          . "</td>\n"
          . "<td>$sp</td>\n";
    }

    # Access
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_acc_link.jpg\" "
      . "width=\"66\" height=20 "
      . "alt=\"Access Time\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # Change
    if ($Fs::has_ctime{$ftype}) {
        print "  <td><img border=\"0\" "
          . "src=\"pict/file_h_chg_link.jpg\" "
          . "width=\"62\" "
          . "alt=\"Change Time\">"
          . "</td>\n"
          . "<td>$sp</td>\n";
    }

    # Create
    if ($Fs::has_crtime{$ftype}) {
        print "  <td><img border=\"0\" "
          . "src=\"pict/file_h_cre_link.jpg\" "
          . "width=\"59\" "
          . "alt=\"Create Time\">"
          . "</td>\n"
          . "<td>$sp</td>\n";
    }

    # Size
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_siz_link.jpg\" "
      . "width=\"31\" height=20 "
      . "alt=\"Size\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # UID
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_uid_link.jpg\" "
      . "width=\"27\" height=20 "
      . "alt=\"UID\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # GID
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_gid_link.jpg\" "
      . "width=\"28\" height=20 "
      . "alt=\"GID\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    # Meta
    print "  <td><img border=\"0\" "
      . "src=\"pict/file_h_meta_link.jpg\" "
      . "width=\"41\" height=20 "
      . "alt=\"Meta\">"
      . "</td>\n"
      . "<td>$sp</td>\n";

    my $row = 0;
    while ($_ = Exec::read_pipe_line(*OUT)) {

        if (
/^($::REG_MTYPE)\/($::REG_MTYPE)\s*(\*?)\s*($::REG_META)(\(realloc\))?:\t(.+?)\t($::REG_DATE)\t($::REG_DATE)\t($::REG_DATE)\t($::REG_DATE)\t(\d+)\t(\d+)\t(\d+)$/o
          )
        {

            # We have to remove the / from the beginning of the file name so
            # save all values so they aren't lost
            my $dt = $1;
            my $it = $2;
            my $d  = $3;
            my $i  = $4;
            my $r  = 0;
            $r = 1 if (defined $5);
            my $n  = $6;
            my $m  = $7;
            my $a  = $8;
            my $c  = $9;
            my $cr = $10;
            my $s  = $11;
            my $g  = $12;
            my $u  = $13;

            if ($n =~ /^\/(.*)/) {
                $n = $1;
            }
            my $enc_n = Args::url_encode($n);
            my $iurl  =
"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_META&$Args::baseargs&meta=$i";
            my $i_int = $i;
            $i_int = $1 if ($i =~ /(\d+)-\d+-\d+/);

            if (($row % 2) == 0) {
                print "<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR\">\n";
            }
            else {
                print "<tr valign=\"TOP\" bgcolor=\"$::BACK_COLOR_TABLE\">\n";
            }

            print "<td align=\"center\"><font color=\"$::DEL_COLOR[$r]\">"
              . "$dt&nbsp;/&nbsp;$it</td>"
              . "<td>$sp</td>\n";

            if ($it eq 'd') {
                my $url =
                    "$::PROGNAME?mod=$::MOD_FILE&"
                  . "view=$File::FILE_LIST&$Args::baseargs&meta=$i"
                  . "&sort=$sort&dir=$enc_n&dirmode=$dirmode";

                print "<td>";
                if ($i_int >= $Fs::first_meta{$ftype}) {
                    print "<a href=\"$url\" target=\"_self\">";
                }
                print "<font color=\"$::DEL_COLOR[$r]\"><tt>"
                  . Print::html_encode($mnt . $n)
                  . "</tt></td>"
                  . "<td>$sp</td>\n";
            }
            else {
                my $url =
                    "$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_FR&"
                  . "$Args::baseargs&meta=$i&sort=$sort&dir=$enc_n"
                  . "&recmode=$File::REC_YES";

                print "<td>";
                if (($i_int >= $Fs::first_meta{$ftype}) && ($it eq 'r')) {
                    print "<a href=\"$url\" target=\"content\">";
                }
                print "<font color=\"$::DEL_COLOR[$r]\"><tt>"
                  . Print::html_encode($mnt . $n)
                  . "</tt></td>"
                  . "<td>$sp</td>\n";
            }

            $m = "$1&nbsp;$2"
              if ($m =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);
            $a = "$1&nbsp;$2"
              if ($a =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);
            $c = "$1&nbsp;$2"
              if ($c =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);
            $cr = "$1&nbsp;$2"
              if ($cr =~ /($::REG_DAY\s+$::REG_TIME)\s+($::REG_ZONE2)/o);

            print "<td><font color=\"$::DEL_COLOR[$r]\">$m</td>"
              . "<td>$sp</td>\n"
              if ($Fs::has_mtime{$ftype});

            print "<td><font color=\"$::DEL_COLOR[$r]\">$a</td>"
              . "<td>$sp</td>\n";
            print "<td><font color=\"$::DEL_COLOR[$r]\">$c</td>"
              . "<td>$sp</td>\n"
              if ($Fs::has_ctime{$ftype});
            print "<td><font color=\"$::DEL_COLOR[$r]\">$cr</td>"
              . "<td>$sp</td>\n"
              if ($Fs::has_crtime{$ftype});

            print "<td><font color=\"$::DEL_COLOR[$r]\">$s</td>"
              . "<td>$sp</td>\n"
              . "<td><font color=\"$::DEL_COLOR[$r]\">$g</td>"
              . "<td>$sp</td>\n"
              . "<td><font color=\"$::DEL_COLOR[$r]\">$u</td>"
              . "<td>$sp</td>\n";

            print "<td>";
            if ($i_int >= $Fs::first_meta{$ftype}) {
                print "<a href=\"$iurl\" target=\"_top\">";
            }
            print "<font color=\"$::DEL_COLOR[$r]\">$i</a>";
            print " (realloc)" if $r;
            print "</td></tr>\n";
        }
        else {
            print "Error Parsing File (invalid characters?)<br>: $_\n<br>";
        }

        $row++;
    }
    close(OUT);
    print "</table>\n";

    print "<center>None</center>\n"
      if ($row == 0);

    Print::print_html_footer();
    return 0;
}

# Content Frame
# This creates two frames for the lower rhs frame
#
sub content_fr {
    Print::print_html_header_frameset("");

    my $meta    = Args::get_meta('meta');
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    print "<frameset rows=\"65,*\">\n";

    my $recmode = $File::REC_NO;

    if (exists $Args::enc_args{'recmode'}) {
        $recmode = $Args::enc_args{'recmode'};
    }
    else {

        # We need to get the allocation status of this structure
        my $meta_int = $meta;
        $meta_int = $1 if ($meta_int =~ /(\d+)-\d+(-\d+)?/);

        local *OUT;
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/ils' -f $ftype -e  -o $offset -i $imgtype $img $meta_int"
        );
        while ($_ = Exec::read_pipe_line(*OUT)) {
            chop;
            next unless ($_ =~ /^$meta/);
            if ($_ =~ /^$meta\|f/) {
                $recmode = $File::REC_YES;
            }
            elsif ($_ =~ /^$meta\|a/) {
                $recmode = $File::REC_NO;
            }
            else {
                Print::print_check_err("Error parsing ils output: $_");
            }
        }
    }
    close(OUT);

    # Get the file type so we can show the thumb nails automatically
    if ($recmode == $File::REC_YES) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -r  -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype  -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }

    my $apptype = Exec::read_pipe_line(*OUT);
    close(OUT);

    $apptype = "Error getting file type"
      if ((!defined $apptype) || ($apptype eq ""));

    # The menu for the different viewing options
    print "<frame src=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_MENU&"
      . "$Args::baseargs&dir=$Args::enc_args{'dir'}"
      . "&meta=$Args::enc_args{'meta'}&sort=$FIL_SORT_ASC&recmode=$recmode\">\n";

    # Print the image thumbnail
    if (($apptype =~ /image data/) || ($apptype =~ /PC bitmap data/)) {
        print "<frame src=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT_IMG"
          . "&$Args::baseargs&dir=$Args::enc_args{'dir'}"
          . "&meta=$Args::enc_args{'meta'}"
          . "&sort=$FIL_SORT_ASC&recmode=$recmode\" name=\"cont2\">\n</frameset>";
    }
    else {

        # Where the actual content will be displayed
        print "<frame src=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT"
          . "&$Args::baseargs&dir=$Args::enc_args{'dir'}"
          . "&meta=$Args::enc_args{'meta'}"
          . "&sort=$FIL_SORT_ASC&recmode=$recmode\" name=\"cont2\">\n</frameset>";
    }

    Print::print_html_footer_frameset();
    return 0;
}

# This is the index for the lower rhs frame
# Choose the content display type here
sub content_menu {
    Args::check_sort();
    Args::check_recmode();

    Print::print_html_header("");

    my $meta    = Args::get_meta('meta');
    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $recmode = Args::get_recmode();

    # Get the file type
    if ($recmode == $File::REC_YES) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype -r  -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }
    else {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype  -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
        );
    }

    my $apptype = Exec::read_pipe_line(*OUT);
    close(OUT);

    $apptype = "Error getting file type"
      if ((!defined $apptype) || ($apptype eq ""));

    # We already have the path in the content window below, so save space
    # print "<center><tt>$mnt$Args::args{'dir'}</tt>\n";
    print "<center>\n";

    my $url =
        "&$Args::baseargs&dir=$Args::enc_args{'dir'}"
      . "&meta=$Args::enc_args{'meta'}&recmode=$recmode";

    # Print the options for output display
    print "<table cellspacing=\"0\" cellpadding=\"2\">\n<tr>\n"
      . "<td>ASCII (<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT&"
      . "sort=$FIL_SORT_ASC$url\" target=\"cont2\">display</a> - "
      . "<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::REPORT&"
      . "sort=$FIL_SORT_ASC$url\" target=\"_blank\">report</a>)</td>\n"
      . "<td>*</td>\n"
      . "<td>Hex ("
      . "<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT&"
      . "sort=$FIL_SORT_HEX$url\" target=\"cont2\">display</a> - "
      . "<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::REPORT&"
      . "sort=$FIL_SORT_HEX$url\" target=\"_blank\">report</a>)</td>\n"
      . "<td>*</td>\n"
      . "<td>ASCII Strings ("
      . "<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::CONT&"
      . "sort=$FIL_SORT_STR$url\" target=\"cont2\">display</a> - "
      . "<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::REPORT&"
      . "sort=$FIL_SORT_STR$url\" target=\"_blank\">report</a>)</td>\n"
      . "<td>*</td>\n"
      . "<td><a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::EXPORT&$url\">"
      . "Export</a></td>\n";

    # if the file is either image or HTML, then let them view it
    if (   ($apptype =~ /image data/)
        || ($apptype =~ /PC bitmap data/))
    {
        print "<td>*</td>\n<td><a href=\"$::PROGNAME?"
          . "mod=$::MOD_FILE&view=$File::CONT_IMG$url\""
          . "target=\"cont2\">View</a></td>\n";
    }
    elsif ($apptype =~ /HTML document text/) {
        print "<td>*</td>\n<td><a href=\"$::PROGNAME?"
          . "mod=$::MOD_APPVIEW&view=$Appview::CELL_FRAME$url\""
          . "target=\"_blank\">View</a></td>\n";
    }

    print "<td>*</td>\n"
      . "<td><a href=\"$::PROGNAME?mod=$::MOD_NOTES&view=$Notes::ENTER_FILE$url\" target=\"_blank\">"
      . "Add Note</a></td>\n"
      if ($::USE_NOTES == 1);

    print "</tr></table>\n";

    print "File Type: $apptype\n";
    print
      "<br><font color=\"$::DEL_COLOR[0]\">Deleted File Recovery Mode</font>\n"
      if ($recmode == $File::REC_YES);
    print "</center>\n";

    Print::print_html_footer();
    return 0;
}

#
# Display the actual content here
#
# NOTE: This has a media type of raw text
#
sub content {
    Args::check_sort();
    Args::check_recmode();

    Print::print_text_header();

    my $sort    = Args::get_sort();
    my $meta    = Args::get_meta('meta');
    my $vol     = Args::get_vol('vol');
    my $mnt     = $Caseman::vol2mnt{$vol};
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $recflag = "";
    $recflag = " -r " if (Args::get_recmode() == $File::REC_YES);

    my $fname = "$mnt$Args::args{'dir'}";
    $fname =~ s/\/\//\//g;

    local *OUT;
    if ($sort == $FIL_SORT_ASC) {
        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Viewing $fname ($meta) as ASCII");

        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta"
        );

        print "Contents Of File: $fname\n\n\n";
        Print::print_output($_) while ($_ = Exec::read_pipe_data(*OUT, 1024));
        close(OUT);
    }
    elsif ($sort == $FIL_SORT_HEX) {
        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Viewing $fname ($meta) as Hex");

        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta"
        );

        print "Hex Contents Of File: $fname\n\n\n";
        my $offset = 0;
        while ($_ = Exec::read_pipe_data(*OUT, 1024)) {
            Print::print_hexdump($_, $offset * 1024);
            $offset++;
        }
        close(OUT);
    }
    elsif ($sort == $FIL_SORT_STR) {
        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Viewing $fname ($meta) as strings");

        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta | '$::TSKDIR/srch_strings' -a"
        );

        print "ASCII String Contents Of File: $fname\n\n\n\n";
        Print::print_output($_) while ($_ = Exec::read_pipe_line(*OUT));
        close(OUT);
    }

    Print::print_text_footer();

    return 0;
}

sub content_img {

    Print::print_html_header("image content");

    my $vol   = Args::get_vol('vol');
    my $mnt   = $Caseman::vol2mnt{$vol};
    my $fname = "$mnt$Args::args{'dir'}";
    $fname =~ s/\/\//\//g;

    my $url =
        "&$Args::baseargs&meta=$Args::enc_args{'meta'}"
      . "&dir=$Args::enc_args{'dir'}&"
      . "cell_mode=2&recmode=$Args::enc_args{'recmode'}";

    print "<tt>$fname</tt><br><br>\n"
      . "<table><tr>\n"
      . "<td width=250 align=\"center\">"
      . "<b>Thumbnail:</b><br>"
      . "<img src=\"$::PROGNAME?mod=$::MOD_APPVIEW&view=$Appview::CELL_CONT${url}\" width=\"200\"></td>\n"
      . "<td valign=top>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_APPVIEW&view=$Appview::CELL_CONT${url}\" "
      . "target=_blank>View Full Size Image</a><br>\n</td>\n"
      . "</tr></table>\n";

    Print::print_html_footer();

    return 0;
}

# Export the contents of a file
sub export {

    my $meta = Args::get_meta('meta');
    my $vol  = Args::get_vol('vol');
    my $mnt  = $Caseman::vol2mnt{$vol};

    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $fname = "$mnt$Args::args{'dir'}";
    $fname =~ s/\/\//\//g;

    my $recflag = "";

    $recflag = " -r "
      if ( (exists $Args::enc_args{'recmode'})
        && ($Args::enc_args{'recmode'} == $File::REC_YES));

    Print::log_host_inv(
        "$Caseman::vol2sname{$vol}: Saving contents of $fname ($meta)");

    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta"
    );

    # We can't trust the mnt and dir values (since there
    # could be bad ASCII values, so only allow basic chars into name
    $fname =~ tr/a-zA-Z0-9\_\-\@\,/\./c;
    $fname = $1 if ($fname =~ /^\.(.*)$/);

    Print::print_oct_header("$vol-${fname}");

    print "$_" while ($_ = Exec::read_pipe_data(*OUT, 1024));

    Print::print_oct_footer();

    return 0;
}

# Display a report for a file
# This is intended to have its own window
#
sub report {
    Args::check_sort();

    my $sort = Args::get_sort();
    my $vol  = Args::get_vol('vol');
    my $meta = Args::get_meta('meta');
    my $mnt  = $Caseman::vol2mnt{$vol};

    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $type;
    my $fname = "$mnt$Args::args{'dir'}";
    $fname =~ s/\/\//\//g;
    my $tz = "";
    $tz = "-z '$Caseman::tz'" unless ("$Caseman::tz" eq "");

    my $recflag = "";

    $recflag = " -r "
      if ( (exists $Args::enc_args{'recmode'})
        && ($Args::enc_args{'recmode'} == $File::REC_YES));

    # We can't trust the mnt and dir values (since there
    # could be bad ASCII values, so only allow basic chars into name
    $fname =~ tr/a-zA-Z0-9\_\-\@\,/\./c;
    $fname = $1 if ($fname =~ /^\.+(.*)$/);
    $fname = $1 if ($fname =~ /^(.*?)\.+$/);

    Print::print_text_header("filename=$Args::args{'vol'}-${fname}.txt");

    $fname = "$mnt$Args::args{'dir'}";
    if ($sort == $FIL_SORT_ASC) {
        Print::log_host_inv(
"$Caseman::vol2sname{$vol}: Generating ASCII report for $fname ($meta)"
        );
        $type = "ASCII";
    }
    elsif ($sort == $FIL_SORT_HEX) {
        Print::log_host_inv(
            "$Args::args{'vol'}: Generating Hex report for $fname ($meta)");
        $type = "Hex";
    }
    elsif ($sort == $FIL_SORT_STR) {
        Print::log_host_inv(
"$Args::args{'vol'}: Generating ASCII strings report for $fname ($meta)"
        );
        $type = "string";
    }
    else {
        print "\n\ninvalid sort value";
        return 1;
    }

    # NOTE: There is a space in the beginning of the separator lines in
    # order to make clear@stamper.itconsult.co.uk time stamping happy
    # I think it confuses the lines that begin at the lhs with PGP
    # headers and will remove the second line.
    #
    print "                  Autopsy $type Report\n\n"
      . "-" x 70 . "\n"
      . "                   GENERAL INFORMATION\n\n"
      . "File: $fname\n";

    # Calculate the MD5 value
    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta | '$::MD5_EXE'"
    );
    my $md5 = Exec::read_pipe_line(*OUT);
    close(OUT);

    $md5 = "Error getting MD5 Value"
      if ((!defined $md5) || ($md5 eq ""));

    chomp $md5;
    if ($recflag eq "") {
        print "MD5 of file: $md5\n";
    }
    else {
        print "MD5 of recovered file: $md5\n";
    }

    if ($::SHA1_EXE ne "") {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta | '$::SHA1_EXE'"
        );
        my $sha1 = Exec::read_pipe_line(*OUT);
        close(OUT);

        $sha1 = "Error getting SHA-1 Value"
          if ((!defined $sha1) || ($sha1 eq ""));

        chomp $sha1;
        if ($recflag eq "") {
            print "SHA-1 of file: $sha1\n";
        }
        else {
            print "SHA-1 of recovered file: $sha1\n";
        }
    }

    if ($sort == $FIL_SORT_STR) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta | '$::TSKDIR/srch_strings' -a | '$::MD5_EXE'"
        );
        $md5 = Exec::read_pipe_line(*OUT);
        close(OUT);

        $md5 = "Error getting MD5 Value"
          if ((!defined $md5) || ($md5 eq ""));

        chomp $md5;
        print "MD5 of ASCII strings: $md5\n";

        if ($::SHA1_EXE ne "") {
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta | '$::TSKDIR/srch_strings' -a | '$::SHA1_EXE'"
            );
            $sha1 = Exec::read_pipe_line(*OUT);
            close(OUT);

            $sha1 = "Error getting SHA-1 Value"
              if ((!defined $sha1) || ($sha1 eq ""));

            chomp $sha1;
            print "SHA-1 of ASCII strings: $sha1\n";
        }
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

    # Get the meta details
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/istat' -f $ftype $tz -s $Caseman::ts -o $offset -i $imgtype $img $meta"
    );
    print $_ while ($_ = Exec::read_pipe_line(*OUT));
    close(OUT);

    # File Type
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
    );
    my $apptype = Exec::read_pipe_line(*OUT);
    close(OUT);

    $apptype = "Error getting file type"
      if ((!defined $apptype) || ($apptype eq ""));

    print "\nFile Type: $apptype";

    print "\n" . "-" x 70 . "\n";
    if ($sort == $FIL_SORT_ASC) {
        print "           CONTENT (Non-ASCII data may not be shown)\n\n";
    }
    else {
        print "                        CONTENT\n\n";
    }

    if ($sort == $FIL_SORT_ASC) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta"
        );
        Print::print_output($_) while ($_ = Exec::read_pipe_data(*OUT, 1024));
        close(OUT);
    }
    elsif ($sort == $FIL_SORT_HEX) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta"
        );
        my $offset = 0;
        while ($_ = Exec::read_pipe_data(*OUT, 1024)) {
            Print::print_hexdump($_, $offset * 1024);
            $offset++;
        }
        close(OUT);
    }
    elsif ($sort == $FIL_SORT_STR) {
        Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag -o $offset -i $imgtype $img $meta | '$::TSKDIR/srch_strings' -a"
        );
        Print::print_output($_) while ($_ = Exec::read_pipe_line(*OUT));
        close(OUT);
    }

    print "\n"
      . "-" x 70 . "\n"
      . "                   VERSION INFORMATION\n\n"
      . "Autopsy Version: $::VER\n";
    print "The Sleuth Kit Version: " . ::get_tskver() . "\n";

    Print::print_text_footer();
    return 0;
}

# Generate the MD5 value for every file in a given directory and save
# them to a text file
sub md5list {
    my $vol  = Args::get_vol('vol');
    my $meta = Args::get_meta('meta');
    my $mnt  = $Caseman::vol2mnt{$vol};

    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $fname = "$mnt$Args::args{'dir'}";
    $fname = 'root' if ($fname eq '/');
    $fname =~ s/\/\//\//g;

    # We can't trust the mnt and dir values (since there
    # could be bad ASCII values, so only allow basic chars into name
    $fname =~ tr/a-zA-Z0-9\_\-\@\,/\./c;

    # remove .'s at beginning and end
    $fname = $1 if ($fname =~ /^\.+(.*)$/);
    $fname = $1 if ($fname =~ /^(.*?)\.+$/);

    Print::print_text_header("filename=$fname.md5");

    $fname = "$mnt$Args::args{'dir'}";
    $fname =~ s/\/\//\//g;

    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/fls' -f $ftype -Fu -o $offset -i $imgtype $img $meta");

    print "MD5 Values for files in $fname ($Caseman::vol2sname{$vol})\n\n";

    while ($_ = Exec::read_pipe_line(*OUT)) {

        # area for allocated files
        if (   (/r\/[\w\-]\s+([\d\-]+):\s+(.*)$/)
            || (/-\/r\s+([\d\-]+):\s+(.*)$/))
        {
            my $in   = $1;
            my $name = $2;

            local *OUT_MD5;
            Exec::exec_pipe(*OUT_MD5,
"'$::TSKDIR/icat' -f $ftype -r -o $offset -i $imgtype $img $in | '$::MD5_EXE'"
            );
            my $md5out = Exec::read_pipe_line(*OUT_MD5);

            $md5out = "Error calculating MD5"
              if ((!defined $md5out) || ($md5out eq ""));

            chomp $md5out;
            print "$md5out\t" . Print::html_encode($name) . "\n";
            close(OUT_MD5);
        }
        elsif (/[\w\-]\/[\w\-]\s+([\d\-]+):\s+(.*)$/) {

           # ignore, non-file types such as sockets or symlinks that do not have
           # MD5 values that make sense
        }

        # Hmmmm
        else {
            print "Error parsing file (invalid characters?): $_\n";
        }
    }
    close(OUT);

    Print::print_text_footer();

    return 0;
}

# Blank Page
sub blank {
    Print::print_html_header("");
    print "<br><center><h3>File Browsing Mode</h3><br>\n"
      . "<p>In this mode, you can view file and directory contents.</p>\n"
      . "<p>File contents will be shown in this window.<br>\n"
      . "More file details can be found using the Metadata link at the end of the list (on the right).<br>\n"
      . "You can also sort the files using the column headers</p>\n";
    Print::print_html_footer();
    return 0;
}

