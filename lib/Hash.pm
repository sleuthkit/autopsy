#
# Hash database and calculation functions
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

package Hash;

$Hash::DB_MANAGER  = 1;
$Hash::DB_LOOKUP   = 2;
$Hash::DB_INDEX    = 3;
$Hash::IMG_VERIFY  = 4;
$Hash::IMG_CALC    = 5;
$Hash::IMG_LIST_FR = 6;
$Hash::IMG_LIST    = 7;
$Hash::BLANK       = 8;

sub main {

    return if ($::LIVE == 1);

    Args::check_view();
    my $view = Args::get_view();

    if ($view == $Hash::BLANK) {
        blank();
        return 0;
    }
    elsif ($view == $Hash::DB_MANAGER) {
        return db_manager();
    }
    elsif ($view == $Hash::DB_LOOKUP) {
        return db_lookup();
    }
    elsif ($view == $Hash::DB_INDEX) {
        return db_index();
    }
    elsif ($view == $Hash::IMG_LIST_FR) {
        return img_list_fr();
    }
    elsif ($view == $Hash::IMG_LIST) {
        return img_list();
    }

    Args::check_vol('vol');
    if ($view == $Hash::IMG_CALC) {
        return img_calc();
    }
    elsif ($view == $Hash::IMG_VERIFY) {
        return img_verify();
    }
    else {
        Print::print_check_err("Invalid Hash View");
    }

}

sub index_md5sum {
    my $db = shift;
    local *OUT;
    Exec::exec_pipe(*OUT, "'$::TSKDIR/hfind' -i md5sum '$db'");
    while ($_ = Exec::read_pipe_line(*OUT)) {
        print "$_<br>\n";
    }
    close(OUT);
}

sub index_nsrl {
    local *OUT;
    Exec::exec_pipe(*OUT, "'$::TSKDIR/hfind' -i nsrl-md5 '$::NSRLDB'");
    while ($_ = Exec::read_pipe_line(*OUT)) {
        print "$_<br>\n";
    }
    close(OUT);
}

# Manager/status Window from HOST Manager
sub db_manager {
    Print::print_html_header("Hash Database Manager");

    print <<EOF;

Hash databases allow Autopsy to quickly identify known files.  This includes
files that are known to be good and those that are known to be bad.  The
'hfind' tool is used to lookup entries in the databases and it needs an
index file for each database.  This window allows one to re-index the
database after it has been updated.  

<p>
To edit the location of the databases, you must manually edit the 
<tt>host.aut</tt> file in the host directory.  

<hr>
<center>
<img src=\"pict/hashdb_h_alert.jpg\" alt=\"Alert Database\" border=\"0\">
</center>
<p><b>Overview</b><br>
These files are known to be <U>bad</U> and are the ones that you want to
know about if they are in the image you are analyzing.  For example,
this database would include hashes of known attacker tools, rootkits,
or photographs.  

EOF
    print "<p><b>Details</b><br>\n";
    if ($Caseman::alert_db eq "") {
        print "Location: <tt>Not Configured</tt><br>\n";
    }
    elsif (-e "$Caseman::alert_db") {
        print "Location: <tt>$Caseman::alert_db</tt><br>\n";
        if (-e "$Caseman::alert_db" . "-md5.idx") {
            print "Status: MD5 Index File Exists<br>\n";
        }
        else {
            print "Status: Database has not been MD5 indexed<br>\n";
        }

        # Index Button
        print "<p><a href=\"$::PROGNAME?mod=$::MOD_HASH&"
          . "view=$Hash::DB_INDEX&hash_alert=1&$Args::baseargs\">"
          . "<img src=\"pict/but_indexdb.jpg\" alt=\"Index DB\" "
          . "width=116 height=20 border=\"0\">"
          . "</a>\n";

        # Lookup Button
        if (-e "$Caseman::alert_db" . "-md5.idx") {
            print "<p><b>Lookup</b><br>"
              . "<form action=\"$::PROGNAME\" method=\"get\">"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_HASH\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Hash::DB_LOOKUP\">\n"
              . "<input type=\"hidden\" name=\"hash_alert\" value=\"1\">\n"
              . Args::make_hidden()
              . "<table cellspacing=\"10\" cellpadding=\"2\">\n<tr>\n"
              . "<td align=\"left\">Enter MD5 Value: "
              . "<input type=\"text\" name=\"md5\" size=40 maxlength=32></td>\n"
              . "<td align=\"left\">"
              . "<input type=\"image\" src=\"pict/but_lookup.jpg\" alt=\"Ok\" "
              . "width=116 height=20 border=\"0\">\n"
              . "</td></tr>\n</table>\n"
              . "</form>";
        }
    }
    else {
        print "Location: <tt>$Caseman::alert_db</tt><br>\n"
          . "ERROR: Database not found<br>\n";
    }

    print <<EOF2;
<hr>
<center>
<img src=\"pict/hashdb_h_ig.jpg\" alt=\"Ignore Database\" border=\"0\">
</center>
<p><b>Overview</b><br>
These files are known to be <U>good</U> and are the ones that you
can ignore if they are found in the image you are analyzing.  For
example, this database would include hashes of known system binaries
and other documents that you do not want to waste time on when running
'sorter' or files that you want to confirm were not modified by an 
attacker.  

EOF2

    print "<p><b>Details</b><br>\n";
    if ($Caseman::exclude_db eq "") {
        print "Location: <tt>Not Configured</tt><br>\n";
    }
    elsif (-e "$Caseman::exclude_db") {
        print "Location: <tt>$Caseman::exclude_db</tt><br>\n";
        if (-e "$Caseman::exclude_db" . "-md5.idx") {
            print "Status: MD5 Index File Exists<br>\n";
        }
        else {
            print "Status: Database has not been MD5 indexed<br>\n";
        }

        # Index Button
        print "<p><a href=\"$::PROGNAME?mod=$::MOD_HASH&view=$Hash::DB_INDEX&"
          . "hash_exclude=1&$Args::baseargs\">"
          . "<img src=\"pict/but_indexdb.jpg\" alt=\"Index DB\" "
          . "width=116 height=20 border=\"0\">"
          . "</a>\n";

        # Lookup Button
        if (-e "$Caseman::exclude_db" . "-md5.idx") {
            print "<p><b>Lookup</b><br>"
              . "<form action=\"$::PROGNAME\" method=\"get\">"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_HASH\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Hash::DB_LOOKUP\">\n"
              . "<input type=\"hidden\" name=\"hash_exclude\" value=\"1\">\n"
              . Args::make_hidden()
              . "<table cellspacing=\"10\" cellpadding=\"2\">\n<tr>\n"
              . "<td align=\"left\">Enter MD5 Value: "
              . "<input type=\"text\" name=\"md5\" size=40 maxlength=32></td>\n"
              . "<td align=\"left\">"
              . "<input type=\"image\" src=\"pict/but_lookup.jpg\" alt=\"Ok\" "
              . "width=116 height=20 border=\"0\">\n"
              . "</td></tr>\n</table>\n"
              . "</form>";
        }
    }
    else {
        print "Location: <tt>$Caseman::exclude_db</tt><br>\n"
          . "ERROR: Database not found<br>\n";
    }

    print <<EOF3;
<hr>
<center>
<img src=\"pict/hashdb_h_nsrl.jpg\" alt=\"NSRL Database\" border=\"0\">
</center>
<p><b>Overview</b><br>
These files are known to be <U>good</U> and <U>bad</U>.  It is currently
difficult to distinguish between known good and known bad, but the NSRL
is used in Autopsy to ignore all known files.

EOF3

    print "<p><b>Details</b><br>\n";
    if ($::NSRLDB eq "") {
        print "Location: <tt>Not Configured</tt><br>\n";
    }
    elsif (-e "$::NSRLDB") {
        print "Location: <tt>$::NSRLDB</tt><br>\n";
        if (-e "$::NSRLDB" . "-md5.idx") {
            print "Status: MD5 Index File Exists<br>\n";
        }
        else {
            print "Status: Database has not been MD5 indexed<br>\n";
        }

        # Index Button
        print "<p><a href=\"$::PROGNAME?mod=$::MOD_HASH&view=$Hash::DB_INDEX&"
          . "hash_nsrl=1&$Args::baseargs\">"
          . "<img src=\"pict/but_indexdb.jpg\" alt=\"Index DB\" "
          . "width=116 height=20 border=\"0\">"
          . "</a>\n";

        # Lookup Button
        if (-e "$::NSRLDB" . "-md5.idx") {
            print "<p><b>Lookup</b><br>"
              . "<form action=\"$::PROGNAME\" method=\"get\">"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_HASH\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Hash::DB_LOOKUP\">\n"
              . "<input type=\"hidden\" name=\"hash_nsrl\" value=\"1\">\n"
              . Args::make_hidden()
              . "<table cellspacing=\"10\" cellpadding=\"2\">\n<tr>\n"
              . "<td align=\"left\">Enter MD5 Value: "
              . "<input type=\"text\" name=\"md5\" size=40 maxlength=32></td>\n"
              . "<td align=\"left\">"
              . "<input type=\"image\" src=\"pict/but_lookup.jpg\" "
              . "alt=\"Lookup\" width=116 height=20 border=0>\n"
              . "</td></tr>\n</table>\n"
              . "</form>";
        }
    }
    else {
        print "Location: <tt>$::NSRLDB</tt><br>\n"
          . "ERROR: Database not found<br>\n";
    }

    print <<EOF4;

<hr><center>
<table width=600 cellspacing=\"0\" cellpadding=\"2\">
<tr>
  <td align=center>
    <a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&view=$Caseman::VOL_OPEN&$Args::baseargs\">
    <img src=\"pict/menu_b_close.jpg\" alt=\"Close\" width=\"167\" height=20 border=\"0\">
    </a>
  </td>
  <td align=center>
    <a href=\"$::HELP_URL\" target=\"_blank\">
    <img src=\"pict/menu_b_help.jpg\" alt=\"Help\" 
    width=\"167\" height=20 border=0>
    </a>
  </td>
</tr>
</table>
EOF4

    Print::print_html_footer();
    return 0;
}

sub db_index {
    Print::print_html_header("Hash Database Indexing");

    if (   (exists $Args::args{'hash_exclude'})
        && ($Args::args{'hash_exclude'} == 1)
        && ($Caseman::exclude_db ne ""))
    {
        Print::log_host_info("Exclude Database Re-Indexed");
        print "<hr><b>Exclude Database Indexing</b><p>\n";
        index_md5sum($Caseman::exclude_db);
    }

    if (   (exists $Args::args{'hash_alert'})
        && ($Args::args{'hash_alert'} == 1)
        && ($Caseman::alert_db ne ""))
    {
        Print::log_host_info("Alert Database Re-Indexed");
        print "<hr><b>Alert Database Indexing</b><p>\n";
        index_md5sum($Caseman::alert_db);
    }

    if (   (exists $Args::args{'hash_nsrl'})
        && ($Args::args{'hash_nsrl'} == 1)
        && ($::NSRLDB ne ""))
    {
        Print::log_host_info("NSRL Database Re-Indexed");
        print "<hr><b>NSRL Database Indexing</b><p>\n";
        index_nsrl();
    }

    print "<p>Indexing Complete<br>\n"
      . "<hr><p>\n<a href=\"$::PROGNAME?mod=$::MOD_HASH&view=$Hash::DB_MANAGER&"
      . "$Args::baseargs\">\n"
      . "<img src=\"pict/menu_b_hashdb.jpg\" width=\"167\" "
      . "height=20 alt=\"Hash Databases\" border=\"0\"></a>\n";

    Print::print_html_footer();
    return 0;
}

# Lookup hashes in database
sub db_lookup {
    Print::print_html_header("Hash Database Lookup");

    unless ((exists $Args::args{'md5'})
        && ($Args::args{'md5'} =~ /^$::REG_MD5$/o))
    {
        Print::print_err("Invalid MD5 Argument");
    }

    if (   (exists $Args::args{'hash_nsrl'})
        && ($Args::args{'hash_nsrl'} == 1)
        && ($::NSRLDB ne ""))
    {
        print "<hr><b>NSRL Lookup</b><p>\n";

        if (-e "$::NSRLDB") {
            local *OUT;
            Exec::exec_pipe(*OUT,
                "'$::TSKDIR/hfind' '$::NSRLDB' $Args::args{'md5'}");
            print "$_<br>\n" while ($_ = Exec::read_pipe_line(*OUT));
            close(OUT);
            Print::log_host_inv("NSRL Lookup ($Args::args{'md5'})");
        }
        else {
            print "NSRL Database Missing<br>\n";
            Print::log_host_inv(
                "NSRL Lookup ($Args::args{'md5'}) - Database Missing");
        }
    }

    if (   (exists $Args::args{'hash_exclude'})
        && ($Args::args{'hash_exclude'} == 1)
        && ($Caseman::exclude_db ne ""))
    {
        print "<hr><b>Exclude Database Lookup</b><p>\n";

        if (-e "$Caseman::exclude_db") {
            local *OUT;
            Exec::exec_pipe(*OUT,
                "'$::TSKDIR/hfind' '$Caseman::exclude_db' $Args::args{'md5'}");
            print "$_<br>\n" while ($_ = Exec::read_pipe_line(*OUT));
            close(OUT);
            Print::log_host_inv("Exclude Database Lookup ($Args::args{'md5'})");
        }
        else {
            print "Exclude Database Missing<br>\n";
            Print::log_host_inv(
"Exclude Database Lookup ($Args::args{'md5'}) - Database Missing"
            );
        }
    }

    if (   (exists $Args::args{'hash_alert'})
        && ($Args::args{'hash_alert'} == 1)
        && ($Caseman::alert_db ne ""))
    {
        print "<hr><b>Alert Database Lookup</b><p>\n";

        if (-e "$Caseman::alert_db") {
            local *OUT;
            Exec::exec_pipe(*OUT,
                "'$::TSKDIR/hfind' '$Caseman::alert_db' $Args::args{'md5'}");
            print "$_<br>\n" while ($_ = Exec::read_pipe_line(*OUT));
            close(OUT);
            Print::log_host_inv("Alert Database Lookup ($Args::args{'md5'})");
        }
        else {
            print "Alert Database Missing<br>\n";
            Print::log_host_inv(
                "Alert Database Lookup ($Args::args{'md5'}) - Database Missing"
            );
        }
    }

    print "<hr><p>\n"
      . "If any of the hash databases need to be re-indexed, use the "
      . "<U>Hash Database Manager</U><p>"
      . "<a href=\"$::PROGNAME?mod=$::MOD_HASH&view=$Hash::DB_MANAGER&"
      . "$Args::baseargs\" target=\"_top\">\n"
      . "<img src=\"pict/menu_b_hashdb.jpg\" width=\"167\" "
      . "height=20 alt=\"Hash Databases\" border=\"0\"></a>\n";

    Print::print_html_footer();
    return 0;
}

############ INTEGRITY CHECKS ##################

# Special view for printing integrity check menu
# We show any file that we have a reference for

# pass the md5 hash (from md5.txt) and then the sorted array
sub int_menu_print {
    my %md5s = %{$_[0]};
    my @sort = @{$_[1]};

    for (my $i = 0; $i <= $#sort; $i++) {

        print
"<tr><td align=\"right\"><tt><b>$Caseman::vol2sname{$sort[$i]}</b></tt></td>\n";

        # It already exists, so make verify button
        if (exists $md5s{$sort[$i]}) {
            print "<td><tt>$md5s{$sort[$i]}</tt></td><td>"
              . "<form action=\"$::PROGNAME\" method=\"get\" target=\"cont\">\n"
              . "<input type=\"hidden\" name=\"vol\" value=\"$sort[$i]\">\n"
              . Args::make_hidden()
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_HASH\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Hash::IMG_VERIFY\">\n"
              . "<input type=\"image\" src=\"pict/int_b_valid.jpg\" "
              . "alt=\"Validate\" border=\"0\">\n"
              . "</form></td></tr>\n";
        }

        # we currenly only support integrity for raw and split image formats
        elsif (($Caseman::vol2itype{$sort[$i]} ne "raw")
            && ($Caseman::vol2itype{$sort[$i]} ne "split"))
        {
            print
"<td colspan=2>Integrity checks for image type $Caseman::vol2itype{$sort[$i]} not yet supported</td></tr>\n";
        }

        # Generate New button
        else {
            print "<td>&nbsp;</td><td>"
              . "<form action=\"$::PROGNAME\" method=\"get\" target=\"cont\">\n"
              . "<input type=\"hidden\" name=\"vol\" value=\"$sort[$i]\">\n"
              . Args::make_hidden()
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_HASH\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Hash::IMG_CALC\">\n"
              . "<input type=\"image\" src=\"pict/int_b_calc.jpg\" "
              . "alt=\"Calculate\" border=\"0\">\n"
              . "</form></td></tr>\n";
        }
    }

    return;
}

# Create a frame with two rows, one with the list of images to check
# and then the bottom actually does it.
sub img_list_fr {
    Print::print_html_header_frameset(
        "$Args::args{'case'}:$Args::args{'host'} Integrity Check");

    print "<frameset rows=\"80%,20%\">\n";

    # Block List
    print "<frame src=\"$::PROGNAME?mod=$::MOD_HASH&view=$Hash::IMG_LIST&"
      . "$Args::baseargs\">\n"
      . "<frame src=\"$::PROGNAME?mod=$::MOD_HASH&view=$Hash::BLANK&"
      . "$Args::baseargs\" name=\"cont\">\n"
      . "</frameset>\n";

    Print::print_html_footer_frameset();
    return 0;
}

# Reads the MD5 file to fill in the MENU list for the integrity
# check mode
sub img_list {
    Print::print_html_header("Image Integrity Menu");

    my %md5s;
    my @blkls;
    my @str;
    my @img;
    my @body;
    my @tl;

    # Read the known values if the file exists
    if (open(FILE, "$::host_dir" . "/md5.txt")) {

        # Read the md5 values into a hash
        while (<FILE>) {
            s/^\s+//;
            s/\s+$//;

            if (/($::REG_MD5)\s+(.*)/o) {
                $md5s{"$2"} = $1;
                $md5s{"$2"} =~ tr/[a-f]/[A-F]/;
            }
            else {
                print "Error reading line $. of md5.txt: $_<br>\n";
                return 1;
            }
        }
        close(FILE);
    }

    # sort the images into the different types
    foreach my $k (keys %Caseman::vol2cat) {
        if ($Caseman::vol2cat{$k} eq "image") {
            push @img, $k;
        }
        elsif ($Caseman::vol2ftype{$k} eq "blkls") {
            push @blkls, $k;
        }
        elsif ($Caseman::vol2ftype{$k} eq "strings") {
            push @str, $k;
        }
        elsif ($Caseman::vol2ftype{$k} eq "body") {
            push @body, $k;
        }
        elsif ($Caseman::vol2ftype{$k} eq "timeline") {
            push @tl, $k;
        }
    }

    print "<center><table cellspacing=\"10\" cellpadding=\"2\">";

    #  image files
    if (scalar @img > 0) {
        print "<tr><th colspan=3>"
          . "<img src=\"pict/int_h_img.jpg\" alt=\"Image Files\">"
          . "</th></tr>\n";
        my @sort = sort { $a cmp $b } @img;
        int_menu_print(\%md5s, \@sort);
    }

    # Unallocated (blkls) images
    if (scalar @blkls > 0) {
        print "<tr><th colspan=3>&nbsp;</th></tr>\n"
          . "<tr><th colspan=3>"
          . "<img src=\"pict/int_h_unalloc.jpg\" alt=\"Unallocated Data Files\">"
          . "</th></tr>\n";
        my @sort = sort { $a cmp $b } @blkls;
        int_menu_print(\%md5s, \@sort);
    }

    # Strings files (of blkls or fs images)
    if (scalar @str > 0) {
        print "<tr><th colspan=3>&nbsp;</th></tr>\n"
          . "<tr><th colspan=3>"
          . "<img src=\"pict/int_h_str.jpg\" alt=\"Strings of Images\">"
          . "</th></tr>\n";
        my @sort = sort { $a cmp $b } @str;
        int_menu_print(\%md5s, \@sort);

    }

    # timeline body files
    if (scalar @body > 0) {
        print "<tr><th colspan=3>&nbsp;</th></tr>\n"
          . "<tr><th colspan=3>"
          . "<img src=\"pict/int_h_data.jpg\" alt=\"Timeline Data Files\">"
          . "</th></tr>\n";
        my @sort = sort { $a cmp $b } @body;
        int_menu_print(\%md5s, \@sort);
    }

    # timeline files
    if (scalar @tl > 0) {
        print "<tr><th colspan=3>&nbsp;</th></tr>\n"
          . "<tr><th colspan=3>"
          . "<img src=\"pict/int_h_tl.jpg\" alt=\"Timelines\">"
          . "</th></tr>\n";
        my @sort = sort { $a cmp $b } @tl;
        int_menu_print(\%md5s, \@sort);
    }

    print <<EOF;
</table>
<p>
<table cellspacing=20 width=600 cellpadding=2>
<tr>
  <td><a href=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_CASEMAN&view=$Caseman::VOL_OPEN\" target=\"_top\">
    <img src=\"pict/menu_b_close.jpg\" alt=\"close\" 
    width=\"167\" height=20 border=\"0\">
  </a>
  </td>
  <td><a href=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_HASH&view=$Hash::IMG_LIST_FR\" target=\"_top\">
    <img src=\"pict/menu_b_ref.jpg\" alt=\"Refresh\" 
    width=\"167\" height=20 border=\"0\">
  </a>
  </td>
  <td align=center>
    <a href=\"$::HELP_URL\" target=\"_blank\">
    <img src=\"pict/menu_b_help.jpg\" alt=\"Help\" 
    width=\"167\" height=20 border=0>
  </a>
  </td>
</tr>
</table>

EOF
    Print::print_html_footer();
    return 0;
}

# Pass the relative path (images/xyz) of the file.  The MD5 is
# returned (or NULL) (in all caps)
sub lookup_md5 {
    my $vol = shift;
    my $md5 = "";

    my $md5_file = "$::host_dir/md5.txt";

    if (-e "$md5_file") {
        unless (open(FILE, $md5_file)) {
            print "Error opening $md5_file<br>\n";
            return "";
        }

        while (<FILE>) {
            s/^\s+//;
            s/\s+$//;

            if (/($::REG_MD5)\s+(.*)/o) {
                my $m = $1;
                if ($2 =~ /$vol$/) {
                    $md5 = $m;
                    $md5 =~ tr/[a-f]/[A-F]/;
                    last;
                }
            }
            else {
                print "Error reading line $. of $md5_file: $_<br>\n";
                return "";
            }
        }
        close(FILE);
    }

    return $md5;
}

sub img_verify {

    Print::print_html_header("Image Integrity Check");

    my $vol = Args::get_vol('vol');

    my $md5 = lookup_md5($vol);

    if ($md5 eq "") {
        print
"The MD5 value of <tt>$Caseman::vol2sname{$vol}</tt> was not found<br>"
          . "It can be calculated by pressing the button below."
          . "<br><br>\n<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_HASH\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Hash::IMG_CALC\">\n"
          . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
          . Args::make_hidden()
          . "<input type=\"image\" src=\"pict/int_b_calc.jpg\" "
          . "alt=\"Calculate\" border=\"0\">\n</form>";
        return 1;
    }

    Print::log_host_inv("$Caseman::vol2sname{$vol}: Checking image integrity");

    print "Original MD5: <tt>$md5</tt><br>\n";

    # We have the original value, now get the new one
    my $img = $Caseman::vol2path{$vol};

    my $cur;
    if ($Caseman::vol2itype{$vol} eq "split") {
        $cur = calc_md5_split($img);
    }
    else {
        $cur = calc_md5($img);
    }

    if ($cur =~ /^$::REG_MD5$/o) {
        print "Current MD5: <tt>$cur</tt><br><br>\n";

        if ($cur eq $md5) {
            print "Pass<br>\n";
            Print::log_host_inv(
                "$Caseman::vol2sname{$vol}: Image integrity check PASSED");
        }
        else {
            print "<font color=\"$::DEL_COLOR[0]\">Fail: Restore from backup"
              . "<br>\n";

            Print::log_host_inv(
                "$Caseman::vol2sname{$vol}: Image integrity check FAILED");
        }
        Print::print_html_footer();
        return 0;
    }
    else {
        print "$cur<br>\n";
        Print::print_html_footer();
        return 1;
    }
}

# Calculate the MD5 value of a file (given the full path)
# return the value in upper case
# This one supports only single files - not split volumes
sub calc_md5 {
    my $img = shift;

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
    Exec::exec_pipe(*OUT, "'$::MD5_EXE' $img");

    alarm(0);
    $SIG{ALRM} = 'DEFAULT';
    print "<br>\n"
      if ($hit_cnt > 0);

    my $out = Exec::read_pipe_line(*OUT);
    close(OUT);

    $out = "Error calculating MD5"
      if ((!defined $out) || ($out eq ""));

    if ($out =~ /^($::REG_MD5)\s+/) {
        my $m = $1;
        $m =~ tr/[a-f]/[A-F]/;
        return $m;
    }
    else {
        return $out;
    }
}

# Same as the version above, but this one can do split images
# it fails though if the file is not a multiple of 512
sub calc_md5_split {
    my $img = shift;

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

    # We use the blkls method so that we can handle split images
    Exec::exec_pipe(*OUT, "'$::TSKDIR/blkls' -f raw -e $img | '$::MD5_EXE'");

    alarm(0);
    $SIG{ALRM} = 'DEFAULT';
    print "<br>\n"
      if ($hit_cnt > 0);

    my $out = Exec::read_pipe_line(*OUT);
    close(OUT);

    $out = "Error calculating MD5"
      if ((!defined $out) || ($out eq ""));

    if ($out =~ /^($::REG_MD5)\s+/) {
        my $m = $1;
        $m =~ tr/[a-f]/[A-F]/;
        return $m;
    }
    else {
        return $out;
    }
}

# Pass it the full path and the short name
# and it adds it to md5.txt and returns the MD5
sub int_create_wrap {
    my $vol = shift;
    my $img = $Caseman::vol2path{$vol};

    my $m;
    if (   (exists $Caseman::vol2itype{$vol})
        && ($Caseman::vol2itype{$vol} eq "split"))
    {
        $m = calc_md5_split($img);
    }
    else {
        $m = calc_md5($img);
    }
    Caseman::update_md5($vol, $m) if ($m =~ /^$::REG_MD5$/o);
    return $m;
}

sub img_calc {
    Print::print_html_header("Image Integrity Creation");
    my $vol = Args::get_vol('vol');
    print "Calculating MD5 value for <tt>$Caseman::vol2sname{$vol}</tt><br>\n";
    Print::log_host_inv("$Caseman::vol2sname{$vol}: Calculating MD5 value");

    my $m = int_create_wrap($vol);

    print "MD5: <tt>$m</tt><br>\n";
    print "<br>Value saved to host file<br><br>\n";

    Print::print_html_footer();
    return 0;
}

# Conver the 'image' format to the 'volume' format
# Make one central file
sub convert {
    my %img2vol = %{shift()};

    Print::log_host_info("Converting format of MD5 hash files");

    # Get out of here if there are no hash files
    return 0
      unless ((-e "$::host_dir" . "$::IMGDIR" . "/md5.txt")
        || (-e "$::host_dir" . "$::DATADIR" . "/md5.txt"));

    # We are going ot make a single file
    my $md5_file_new = "$::host_dir" . "/md5.txt";
    open MD5_NEW, ">$md5_file_new"
      or die "Can't open writing file: $md5_file_new";

    # Read the md5s for the image directory
    my $md5_file = "$::host_dir" . "$::IMGDIR" . "/md5.txt";
    if (open(FILE, $md5_file)) {

        # Read the md5 values into a hash
        while (<FILE>) {
            s/^\s+//;
            s/\s+$//;

            if (/($::REG_MD5)\s+(.*)/o) {
                my $md5 = $1;
                my $img = $2;

                unless (exists $img2vol{$img}) {
                    print STDERR
"Error finding image during hash file conversion: $img.  Skipping\n";
                    next;
                }
                my $vol = $img2vol{$img};

                print MD5_NEW "$md5 $vol\n";
            }
            else {
                print MD5_NEW "$_";
            }
        }
        close(FILE);
        rename $md5_file, $md5_file . ".bak";
    }

    # Now do the data directory
    $md5_file = "$::host_dir" . "$::DATADIR" . "/md5.txt";
    if (open(FILE, $md5_file)) {

        # Read the md5 values into a hash
        while (<FILE>) {
            s/^\s+//;
            s/\s+$//;

            if (/($::REG_MD5)\s+(.*)/o) {
                my $md5 = $1;
                my $img = $2;

                unless (exists $img2vol{$img}) {
                    print STDERR
"Error finding image during hash file conversion: $img.  Skipping\n";
                    next;
                }
                my $vol = $img2vol{$img};

                print MD5_NEW "$md5 $vol\n";
            }
            else {
                print MD5_NEW "$_";
            }
        }
        close(FILE);
        rename $md5_file, $md5_file . ".bak";
    }

    close(MD5_NEW);
    return 0;
}

# Blank Page
sub blank {
    Print::print_html_header("");
    print "<!-- This Page Intentionally Left Blank -->\n";
    Print::print_html_footer();
    return 0;
}
