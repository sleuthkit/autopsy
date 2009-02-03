#
# Case Management methods, including the windows and functions to read the
# config files
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

package Caseman;

# If the order of these views are changed, then the order of the main
# function may have to be as well

# Case Views
$Caseman::CASE_NEW      = 1;
$Caseman::CASE_NEW_DOIT = 2;
$Caseman::CASE_OPEN     = 3;
$Caseman::CASE_OPEN_LOG = 4;
$Caseman::CASE_DETAILS  = 5;

# $Caseman::CASE_DEL = 6;
my $CASE_MAX = 5;

# Host Views
$Caseman::HOST_ADD      = 7;
$Caseman::HOST_ADD_DOIT = 8;
$Caseman::HOST_OPEN     = 9;
$Caseman::HOST_OPEN_LOG = 10;
$Caseman::HOST_DETAILS  = 11;

# $Caseman::HOST_DEL = 12;
my $HOST_MAX = 11;

# Image Views
$Caseman::IMG_ADD       = 13;
$Caseman::IMG_ADD_PREP  = 14;
$Caseman::IMG_ADD_DOIT  = 15;
$Caseman::VOL_OPEN      = 16;
$Caseman::VOL_OPEN_LOG  = 17;
$Caseman::VOL_DETAILS   = 18;
$Caseman::IMG_DEL       = 19;
$Caseman::VOL_MAKESTR   = 20;
$Caseman::VOL_MAKEBLKLS = 21;
my $IMG_MAX = 21;

# Module Variables
# %vol2par - Volume to parent volume (vol to img, str to vol)
# %vol2start - Starting sector of volume in image
# %vol2end  - ending sector of volume in image
# %vol2cat - The big picture type of volume (part, disk, strings, blkls)
# %vol2ftype - The file system type (fat, dos, ntfs etc.)
# %vol2itype - The image file type (could be for a parent image)
# %vol2dtype- the disk type
# %mod2vol;   # Mapping for image, given the strings or blkls
# %vol2mnt;   # Mapping for mount point, given the vol name
# %vol2str;   # Mapping for ASCII strings file, given  the vol name
# %vol2uni;   # Mapping for Unicode strings file, given  the vol name
# %vol2blkls;   # Mapping for blkls file, given  the vol name
# %vol2path - full file path of volume
# %vol2sname - short name of volume

sub main {

    # By default, show the case open window
    $Args::args{'view'} = $Args::enc_args{'view'} = $Caseman::CASE_OPEN
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    # The only live function is for the open img
    if ($::LIVE == 1) {
        Args::check_inv();
        if ($view == $Caseman::VOL_OPEN) {
            return vol_open();
        }

        Args::check_vol('vol');

        # Args::check_ftype();
        # Args::check_mnt();

        if ($view == $Caseman::VOL_OPEN_LOG) {
            return vol_open_log();
        }
        else {
            Print::print_check_err(
                "Invalid Live Analysis Case Management View");
        }
        return 0;
    }

    # Case functions
    if ($view <= $CASE_MAX) {
        if ($view == $Caseman::CASE_OPEN) {
            return case_open();
        }
        elsif ($view == $Caseman::CASE_NEW) {
            return case_new();
        }

        Args::check_case();
        $::case_dir = "$::LOCKDIR/" . Args::get_case() . "/";
        $::case_dir =~ s/\/\//\//g;

        if ($view == $Caseman::CASE_OPEN_LOG) {
            return case_open_log();
        }
        elsif ($view == $Caseman::CASE_NEW_DOIT) {
            return case_new_doit();
        }
        elsif ($view == $Caseman::CASE_DETAILS) {
            return case_details();
        }
    }

    Args::check_case();
    $::case_dir = "$::LOCKDIR/" . Args::get_case() . "/";
    $::case_dir =~ s/\/\//\//g;

    # Host functions
    if ($view <= $HOST_MAX) {
        if ($view == $Caseman::HOST_OPEN) {
            return host_open();
        }
        elsif ($view == $Caseman::HOST_ADD) {
            return host_add();
        }

        Args::check_host();
        $::host_dir = "$::case_dir" . Args::get_host() . "/";
        $::host_dir =~ s/\/\//\//g;
        if ($view == $Caseman::HOST_ADD_DOIT) {
            return host_add_doit();
        }

        Caseman::read_host_config();
        if ($view == $Caseman::HOST_OPEN_LOG) {
            return host_open_log();
        }
        elsif ($view == $Caseman::HOST_DETAILS) {
            return host_details();
        }
    }

    Args::check_host();
    $::host_dir = "$::case_dir" . Args::get_host() . "/";
    $::host_dir =~ s/\/\//\//g;
    Caseman::read_host_config();
    Args::check_inv();

    if ($view <= $IMG_MAX) {
        if ($view == $Caseman::VOL_OPEN) {
            return vol_open();
        }
        elsif ($view == $Caseman::IMG_ADD) {
            return img_add();
        }
        elsif ($view == $Caseman::IMG_ADD_PREP) {
            return img_add_prep();
        }
        elsif ($view == $Caseman::IMG_ADD_DOIT) {
            return img_add_doit();
        }

        Args::check_vol('vol');

        if ($view == $Caseman::VOL_OPEN_LOG) {
            return vol_open_log();
        }
        elsif ($view == $Caseman::VOL_DETAILS) {
            return vol_details();
        }

        #       elsif ($view == $Caseman::IMG_DEL) {
        # 		return img_del();
        # 	}
        elsif ($view == $Caseman::VOL_MAKESTR) {
            return vol_makestr();
        }
        elsif ($view == $Caseman::VOL_MAKEBLKLS) {
            return vol_makeblkls();
        }
    }

    Print::print_check_err("Invalid Case Management View");
}

####################################################################
# General menu Functions

sub print_menu_tabs {

    if ($::LIVE == 1) {
        print "<h2>Live Analysis Mode</h2>\n";
    }

    print "<table width=\"600\" height=\"60\" background=\"$::YEL_PIX\" "
      . "border=\"0\" cellspacing=\"0\" cellpadding=\"0\">\n<tr>\n"
      . "<td align=\"center\" width=\"200\">";

    my $view = Args::get_view();

    # Case Gallery Tab
    if ($view == $Caseman::CASE_OPEN) {
        print "<img border=0 src=\"pict/menu_t_cg_cur.jpg\" "
          . "width=200 height=65 alt=\"Case Gallery (Current Mode)\">\n";
    }
    elsif ($::LIVE == 1) {
        print "<img border=0 src=\"pict/menu_t_cg_org.jpg\" "
          . "width=200 height=65 alt=\"Case Gallery\">\n";
    }
    else {
        print "<a href=\"$::PROGNAME?"
          . "mod=$::MOD_CASEMAN&view=$Caseman::CASE_OPEN\">"
          . "<img border=0 src=\"pict/menu_t_cg_link.jpg\" "
          . "width=200 height=65 alt=\"Case Gallery\"></a>\n";
    }
    print "</td>\n" . "<td align=\"center\" width=\"200\">";

    # Host Gallery Tab
    # Current
    if ($view == $Caseman::HOST_OPEN) {
        print "<img border=0 src=\"pict/menu_t_hg_cur.jpg\" "
          . "width=200 height=65 alt=\"Host Gallery (Current Mode)\">\n";
    }

    # Link
    elsif (($view == $Caseman::VOL_OPEN) && ($::LIVE == 0)) {
        print "<a href=\"$::PROGNAME?"
          . "mod=$::MOD_CASEMAN&view=$Caseman::HOST_OPEN"
          . "&case=$Args::args{'case'}\">"
          . "<img border=0 src=\"pict/menu_t_hg_link.jpg\" "
          . "width=200 height=65 alt=\"Host Gallery\"></a>\n";
    }

    # Non-link
    else {
        print "<img border=0 src=\"pict/menu_t_hg_org.jpg\" "
          . "width=200 height=65 alt=\"Host Gallery (Not Available)\">\n";
    }

    print "</td>\n" . "<td align=\"center\" width=\"200\">";

    # Host Manager Tab
    # Current
    if ($view == $Caseman::VOL_OPEN) {
        print "<img border=0 src=\"pict/menu_t_hm_cur.jpg\" "
          . "width=200 height=65 alt=\"Host Manager (Current Mode)\">\n";
    }

    # non-link
    else {
        print "<img border=0 src=\"pict/menu_t_hm_org.jpg\" "
          . "width=200 height=65 alt=\"Host Manager (Not Available)\">\n";
    }

    print "</td>\n</tr>\n" . "</table>\n";
}

####################################################################
# Case Functions

# if no args are passed, return case config using args{'case'},
# else use the case value passed
#
# Case config:
# In case directory with case_name.case
sub case_config_fname {
    if (scalar(@_) == 1) {
        my $c = shift;
        return "$::LOCKDIR/" . "$c/case.aut";
    }
    else {
        return "$::LOCKDIR/" . "$Args::args{'case'}/case.aut";
    }
}

# Read case config and save it to $Caseman::cvals
sub read_case_config {
    return if ($::LIVE == 1);

    my $case;

    if (scalar(@_) == 1) {
        $case = shift;
    }
    else {
        $case = Args::get_case();
    }

    my $fname = case_config_fname($case);

    %Caseman::cvals = ();

    open CONFIG, "<$fname"
      or die "Can't open case config file ($fname)";

    while (<CONFIG>) {
        next if ((/^\#/) || (/^\s+$/));
        s/^\s+//;
        s/\s+$//;
        $Caseman::cvals{$1} = Print::html_encode($2) if (/^(\S+)\s+(.*)$/);
    }
    close(CONFIG);

    $Caseman::cvals{'desc'} = "None Provided"
      unless (exists $Caseman::cvals{'desc'});

    $Caseman::cvals{'created'} = "unknown"
      unless (exists $Caseman::cvals{'created'});
}

sub case_new {
    Print::print_html_header("Create A New Case");

    print <<EOF;
<br>
<br>
<center>
<img src=\"pict/menu_h_cnew.jpg\" alt=\"New Case\">
<br><br><br>

<table width=\"600\" background=\"$::YEL_PIX\" cellspacing=\"0\"
  cellpadding=\"2\" border=0>
<form action=\"$::PROGNAME\" method=\"get\">
<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">
<input type=\"hidden\" name=\"view\" value=\"$Caseman::CASE_NEW_DOIT\">
<tr>
  <td colspan=3 align=left>
    1.  <b>Case Name:</b> The name of this investigation.  It can contain only letters, numbers, and symbols.
  </td>
</tr>
<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=2><input type=\"text\" name=\"case\"></td>
</tr>

<tr><td colspan=3>&nbsp;</td></tr>

<tr>
  <td colspan=3 align=left>
    2.  <b>Description:</b> An optional, one line description of this case.
  </td>
</tr>
<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=2><input type=\"text\" name=\"desc\" size=32 maxlength=32></td>
</tr>

<tr><td colspan=3>&nbsp;</td></tr>

<tr>
  <td colspan=3 align=left>
    3.  <b>Investigator Names:</b> The optional names (with no spaces) of the investigators for this case.
  </td>
</tr>
<tr>
  <td>&nbsp;</td>
  <td align=left><tt>a.</tt> <input type=\"text\" name=\"inv1\"></td>
  <td align=left><tt>b.</tt> <input type=\"text\" name=\"inv2\"></td>
</tr>
<tr>
  <td>&nbsp;</td>
  <td align=left><tt>c.</tt> <input type=\"text\" name=\"inv3\"></td>
  <td align=left><tt>d.</tt> <input type=\"text\" name=\"inv4\"></td>
</tr>
<tr>
  <td>&nbsp;</td>
  <td align=left><tt>e.</tt> <input type=\"text\" name=\"inv5\"></td>
  <td align=left><tt>f.</tt> <input type=\"text\" name=\"inv6\"></td>
</tr>
<tr>
  <td>&nbsp;</td>
  <td align=left><tt>g.</tt> <input type=\"text\" name=\"inv7\"></td>
  <td align=left><tt>h.</tt> <input type=\"text\" name=\"inv8\"></td>
</tr>
<tr>
  <td>&nbsp;</td>
  <td align=left><tt>i.</tt> <input type=\"text\" name=\"inv9\"></td>
  <td align=left><tt>j.</tt> <input type=\"text\" name=\"inv10\"></td>
</tr>
</table>

<br><br>
<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">
<tr>
  <td align=center>
    <input type=\"image\" src=\"pict/menu_b_cnew.jpg\" 
      alt=\"Create Case\" width=\"176\" height=20 border=0>
  </td>
</form>
  <td align=center>
    <form action=\"$::PROGNAME\" method=\"get\">
    <input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">
    <input type=\"hidden\" name=\"view\" value=\"$Caseman::CASE_OPEN\">
    <input type=\"image\" src=\"pict/menu_b_cancel.jpg\" 
    alt=\"Cancel\" width=\"167\" height=20 border=0>
    </form>
  </td>
  <td align=center><a href=\"$::HELP_URL\" 
    target=\"_blank\">
    <img src=\"pict/menu_b_help.jpg\" alt=\"Help\" 
    width=\"167\" height=20 border=0></a>
  </td>
</tr>
</table>
EOF

    Print::print_html_footer();
    return;
}

# Create the directory and case configuration file
# Gets the input from CASE_NEW
sub case_new_doit {
    Print::print_html_header("Creating Case: $Args::args{'case'}");
    my $case = $Args::args{'case'};

    print "<h3>Creating Case: <tt>$case</tt></h3>\n";

    # Make the directory
    if (-d "$::case_dir") {

        # we can't send all of this to print_err, bc it doesn't want HTML
        print "Error: $::case_dir already exists<br>"
          . "Please remove the directory and its contents and try again"
          . "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
          . "view=$Caseman::CASE_OPEN\">"
          . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
          . "width=\"43\" height=20 border=\"0\"></a>\n";
        Print::print_err("\n");
    }

    unless (mkdir "$::case_dir", $::MKDIR_MASK) {
        Print::print_err("Error making directory $::case_dir: $!");
    }

    print "Case directory (<tt>$::case_dir</tt>) created<br>\n";
    Print::log_case_info("Case $case created");

    my $fname = Caseman::case_config_fname();

    open CASE_CONFIG, ">$fname" or die "Can't open case config: $fname";

    print CASE_CONFIG "# Autopsy case config file\n"
      . "# Case: $case\n\n"
      . "created "
      . localtime() . "\n";

    if ((exists $Args::args{'desc'}) && ($Args::args{'desc'} ne "")) {
        Print::print_err(
            "Invalid Description\n" . "Use the browser's back button to fix")
          if ($Args::args{'desc'} =~ /\n/);

        print CASE_CONFIG "desc $Args::args{'desc'}\n";
    }
    print CASE_CONFIG "images	$::IMGDIR\n";
    print CASE_CONFIG "data		$::DATADIR\n";
    print CASE_CONFIG "log		$::LOGDIR\n";
    print CASE_CONFIG "reports	$::REPDIR\n";

    close CASE_CONFIG;
    print "Configuration file (<tt>$fname</tt>) created<br>\n";

    my $iname = investig_fname();
    open INVES, ">>$iname" or die "Can't open investigators file: $iname";

    my @invs;
    if (   (exists $Args::args{'inv1'})
        && ($Args::args{'inv1'} ne "")
        && ($Args::args{'inv1'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv2'})
        && ($Args::args{'inv2'} ne "")
        && ($Args::args{'inv2'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv3'})
        && ($Args::args{'inv3'} ne "")
        && ($Args::args{'inv3'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv4'})
        && ($Args::args{'inv4'} ne "")
        && ($Args::args{'inv4'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv5'})
        && ($Args::args{'inv5'} ne "")
        && ($Args::args{'inv5'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv6'})
        && ($Args::args{'inv6'} ne "")
        && ($Args::args{'inv6'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv7'})
        && ($Args::args{'inv7'} ne "")
        && ($Args::args{'inv7'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv8'})
        && ($Args::args{'inv8'} ne "")
        && ($Args::args{'inv8'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv9'})
        && ($Args::args{'inv9'} ne "")
        && ($Args::args{'inv9'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }
    if (   (exists $Args::args{'inv10'})
        && ($Args::args{'inv10'} ne "")
        && ($Args::args{'inv10'} =~ /^\s*($::REG_INVESTIG)\s*$/o))
    {
        print INVES "$1\n";
        push @invs, $1;
    }

    close(INVES);

    Print::log_session_info("Case $case created");

    print "<br><br>We must now create a host for this case.\n";

    print "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_ADD\">\n"
      . "<input type=\"hidden\" name=\"case\" value=\"$case\">\n";

    if (scalar @invs == 0) {
        print "<option hiddden name=\"inv\" value=\"unknown\">\n";
    }
    else {
        print "<br><br>Please select your name from the list: "
          . "<select name=\"inv\" size=\"1\">\n";

        foreach $i (@invs) {
            print "<option value=\"$i\">$i</option>\n";
        }
        print "</select>\n";
    }

    print "<br><br>"
      . "<input type=\"image\" src=\"pict/menu_b_hnew.jpg\" alt=\"Add New Host\" "
      . "height=20 border=\"0\"></form>\n";

    Print::print_html_footer();
    return;
}

# Open a Case
# This provides a form with a list of options
sub case_open {
    Print::print_html_header("Open A Case");

    # Read the directories of the Evidence Locker into an array
    # Verify that there is a config file in the directory
    my @cases;
    opendir CASES, $::LOCKDIR or die "Can't open $::LOCKDIR directory: $!";
    foreach my $c (readdir CASES) {
        next if (($c eq '.') || ($c eq '..'));
        my $cfile = Caseman::case_config_fname($c);

        push @cases, $c
          if ((-d "$::LOCKDIR/$c") && (-e "$cfile"));
    }
    closedir CASES;

    print "<br><br><center>";

    # Were there any cases?
    if (scalar @cases == 0) {
        print "No cases exist in <tt>$::LOCKDIR</tt><br><br>\n"
          . "Select the New Case button below to make one<br>\n";
    }
    else {

        print "Select the case to open or create a new one<br>\n";

        print_menu_tabs();

        print "<table width=\"600\" background=\"$::YEL_PIX\" "
          . " cellspacing=\"0\" cellpadding=\"2\" border=0>\n";

        print "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::CASE_OPEN_LOG\">\n"
          . Args::make_hidden()
          . "<tr><th>Name</th>"
          . "<th>Description</th>"
          . "<td>&nbsp;</td></tr>\n";

        my $first = 0;
        foreach my $c (@cases) {

            print "<tr><td align=\"left\">"
              . "<input type=\"radio\" name=\"case\" value=$c";
            if ($first == 0) {
                print " CHECKED";
                $first = 1;
            }
            print ">" . Print::html_encode($c) . "</td>";

            Caseman::read_case_config($c);

            print "<td>$Caseman::cvals{'desc'}</td>"
              . "<td align=center>"
              . "<a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
              . "view=$Caseman::CASE_DETAILS&case=$c\">"
              . "details</a></td>"
              . "</tr>\n";
        }
        print "</table>\n";
    }

    print "<br><br>"
      . "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">\n"
      . "<tr>\n";

    # Print the OK button if there were cases
    if (scalar @cases != 0) {
        print "<td align=center>"
          . "<input type=\"image\" src=\"pict/menu_b_ok.jpg\" "
          . "width=167 height=20 alt=\"Ok\" border=0>"
          . "</form></td>\n\n";
    }

    # Print a 'New Case' Button
    print "<td align=center>"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::CASE_NEW\">\n"
      . "<input type=\"image\" src=\"pict/menu_b_cnew.jpg\" "
      . "width=167 height=20 alt=\"New Case\" border=0>\n"
      . "</form></td>"
      .

      # Print a Menu Button
      "<td align=center>"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"image\" src=\"pict/menu_b_menu.jpg\" "
      . "width=167 height=20 alt=\"Main Menu\" border=0>\n"
      . "</form></td></tr></table>\n";

    print "<table width=600 cellspacing=\"0\" cellpadding=\"2\">\n<tr>"
      . "<td>&nbsp;</td>\n"
      . "<td align=center width=200><a href=\"$::HELP_URL\" "
      . " target=\"_blank\">"
      . "<img src=\"pict/menu_b_help.jpg\" alt=\"Help\" "
      . "width=\"167\" height=20 border=0>"
      . "</a></td>"
      . "<td>&nbsp;</td>\n"
      . "</tr>\n"
      . "</table>";

    Print::print_html_footer();
    return;
}

# Log that a given case was opened and then proceed to open a host
sub case_open_log {
    Print::log_session_info("Case $Args::args{'case'} opened");
    Print::log_case_info("Case $Args::args{'case'} opened");
    $Args::args{'view'} = $Args::enc_args{'view'} = $Caseman::HOST_OPEN;
    host_open();
}

# Display Case Details
sub case_details {

    Print::print_html_header("Details of $Args::args{'case'}");

    read_case_config();

    print "<br><br>"
      . "<center>"
      . "<img src=\"pict/menu_h_cdet.jpg\" alt=\"Case Details\">"
      . "<br><br><br>\n"
      . "<table width=\"600\" cellspacing=\"0\" background=\"$::YEL_PIX\" "
      . "cellpadding=\"2\" border=0>\n"
      . "  <tr><td align=\"right\" width=300><b>Name:</b></td>"
      . "<td align=\"left\" width=300><tt>$Args::args{'case'}</tt></td></tr>\n"
      .

      # Description
      "  <tr><td align=\"right\"><b>Description:</b></td>"
      . "<td align=\"left\"><tt>$Caseman::cvals{'desc'}</tt></td></tr>\n"
      . "  <tr><td align=\"right\"><b>Created:</b></td>"
      . "<td align=\"left\"><tt>$Caseman::cvals{'created'}</tt></td></tr>\n";

    # Display the valid investigators
    my @invs = read_invest();
    my $cnt  = 0;
    print "  <tr><td colspan=\"2\">&nbsp;</td></tr>\n"
      if (scalar @invs > 0);

    foreach my $i (@invs) {
        if ($cnt == 0) {
            print "  <tr><td align=\"right\"><b>Investigators:</b></td>";
            $cnt++;
        }
        else {
            print "  <tr><td>&nbsp;</td>";
        }
        print "<td align=\"left\"><tt>"
          . Print::html_encode($i)
          . "</tt></td></tr>\n";
    }

    print "</table>\n"
      . "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&view=$Caseman::CASE_OPEN\">"
      . "<img src=\"pict/menu_b_ok.jpg\" alt=\"Ok\" "
      . "width=\"167\" height=20 border=\"0\"></a>";

    Print::print_html_footer();
    return 0;
}

####################################################################
# Host Functions

# if no args are passed, return host config using args{'host'},
# else use the host value passed
sub host_config_fname {
    if (scalar(@_) == 1) {
        my $h = shift;
        return "$::case_dir" . "$h/host.aut";
    }
    else {
        return "$::host_dir" . "host.aut";
    }
}

# Converts the original single image host config to the volume-based config
sub convert_host_config {

    return if ($::LIVE == 1);

    my $host = Args::get_host();

    Print::log_host_info("Converting host config files");
    print STDERR "Converting host config file to new format\n";

    # The file to convert
    my $cfile = host_config_fname();
    unless (open(FILE, $cfile)) {
        Print::print_check_err("Error opening $cfile");
    }

    my $tmpcfile = "$::host_dir" . "host-convert.aut";
    unless (open(FILE_TMP, ">>$tmpcfile")) {
        Print::print_check_err("Error opening $tmpcfile");
    }

    my $img_cnt = 0;
    my $vol_cnt = 0;
    $img2vol{'qazwsxedc'} = ""; # stores the image path to partition / file name
    $img2vol2{'qazwsxedc'} =
      "";    # stores the image path to file name (no partitions)

    while (<FILE>) {
        if ((/^\#/) || (/^\s+$/)) {
            print FILE_TMP $_;
            next;
        }

        # remove whitespace
        s/^\s+//;
        s/\s+$//;

        # normal file system image entry
        #
        # 'image	images/hda1.dd		openbsd		/usr
        if (/^image\s+($::REG_IMG)\s+([\w\-]+)\s+([\w\-\_\.\/:\\]+)$/o) {

            my $i = $1;
            my $t = $2;
            my $r = $3;

            # Add trailing / to original mount point if needed
            if (($r !~ /.*?\/$/) && ($r !~ /.*?\\$/)) {
                $r .= '/';
            }
            my $vnum = "vol" . $vol_cnt;
            my $inum = "img" . $img_cnt;
            $img2vol{$i}  = $vnum;
            $img2vol2{$i} = $inum;

            print FILE_TMP "image   $inum     raw     $i\n";
            print FILE_TMP "part    $vnum     $inum   0   0   $t  $r\n";

            $img_cnt++;
            $vol_cnt++;
        }

        # swap
        # swap		images/hda3.dd
        elsif (/^swap\s+($::REG_IMG)\s*$/o) {
            my $i = $1;

            my $vnum = "vol" . $vol_cnt;
            my $inum = "img" . $img_cnt;
            $img2vol{$i}  = $vnum;
            $img2vol2{$i} = $inum;

            print FILE_TMP "image   $inum     raw     $i\n";
            print FILE_TMP "part	$vnum   $inum 0   0   swap\n";
            $img_cnt++;
            $vol_cnt++;

        }

        # raw
        # raw	images/hda3.dd
        elsif (/^raw\s+($::REG_IMG)\s*$/o) {
            my $i = $1;
            $img2vol{$i}  = "vol" . $vol_cnt;
            $img2vol2{$i} = "img" . $img_cnt;

            print FILE_TMP "image   img" . $img_cnt . "     raw     $i\n";
            print FILE_TMP "part    vol" . $vol_cnt . "   img" . $img_cnt
              . " 0   0   raw\n";
            $img_cnt++;
            $vol_cnt++;
        }

        # entry for a strings or blkls file
        #
        # strings 	data/hda1.str		images/hda1.dd
        elsif (/^strings\s+($::REG_IMG)\s+($::REG_IMG)$/o) {
            my $i = $1;
            my $o = $2;

            if (exists $img2vol{$o}) {
                my $vname = $img2vol{$o};
                print FILE_TMP "strings vol" . $vol_cnt . "     $vname  $i\n";
                $img2vol{$i}  = "vol" . $vol_cnt;
                $img2vol2{$i} = "vol" . $vol_cnt;
            }
            else {
                print STDERR "Error: Volume for strings $o not found<br>\n";
            }
            $vol_cnt++;
        }

        # entry for a strings or blkls file
        #
        # unistrings 	data/hda1.str		images/hda1.dd
        elsif (/^unistrings\s+($::REG_IMG)\s+($::REG_IMG)$/o) {
            my $i = $1;
            my $o = $2;

            if (exists $img2vol{$o}) {
                my $vname = $img2vol{$o};
                print FILE_TMP "unistrings    vol" . $vol_cnt
                  . "   $vname     $i\n";
                $img2vol{$i}  = "vol" . $vol_cnt;
                $img2vol2{$i} = "vol" . $vol_cnt;
            }
            else {
                print STDERR
                  "Error: Volume for unicode strings $o not found<br>\n";
            }
            $vol_cnt++;
        }

        # blkls entry
        # blkls data/image.blkls	[images/image.dd]
        elsif (/^blkls\s+($::REG_IMG)\s*($::REG_IMG)$/o) {
            my $i = $1;
            my $o = $2;

            $img2vol{$i} = "vol" . $vol_cnt;

            if (exists $img2vol{$o}) {
                my $vname = $img2vol{$o};
                print FILE_TMP "blkls     vol" . $vol_cnt . "  $vname     $i\n";
                $img2vol{$i}  = "vol" . $vol_cnt;
                $img2vol2{$i} = "vol" . $vol_cnt;
            }
            else {
                print STDERR "Error: Volume for blkls $o not found<br>\n";
            }
            $vol_cnt++;
        }

        # body data/body.txt
        elsif (/^body\s+($::REG_IMG)$/o) {
            my $i = $1;
            print FILE_TMP "body    vol" . $vol_cnt . " $i\n";
            $img2vol{$i}  = "vol" . $vol_cnt;
            $img2vol2{$i} = "vol" . $vol_cnt;

            $vol_cnt++;
        }

        # timeline data/timeline.txt
        elsif (/^timeline\s+($::REG_IMG)$/o) {
            my $i = $1;
            print FILE_TMP "timeline    vol" . $vol_cnt . " $i\n";
            $img2vol{$i}  = "vol" . $vol_cnt;
            $img2vol2{$i} = "vol" . $vol_cnt;
            $vol_cnt++;
        }

        # timezone XYZ
        elsif (/^timezone\s+($::REG_ZONE_ARGS)$/o) {
            print FILE_TMP "$_\n";
        }

        # timeskew XYZ
        elsif (/^timeskew\s+($::REG_SKEW)$/o) {
            print FILE_TMP "$_\n";
        }

        # desc XYZ
        elsif (/^desc\s+(.*)$/) {
            print FILE_TMP "$_\n";
        }

        # hash databases
        elsif (/^alert_db\s+'(.*)'$/) {
            print FILE_TMP "$_\n";
        }
        elsif (/^exclude_db\s+'(.*)'$/) {
            print FILE_TMP "$_\n";
        }
        else {
            my $msg =
                "Error: invalid entry in $cfile:$." . "\n"
              . "image	path	fs_type		mnt_point\n"
              . "strings	path	orig_img\n"
              . "blkls		path	[orig_img]\n"
              . "body		path\n"
              . "timeline	path\n"
              . "timezone	TZ\n"
              . "desc		DESCRIPTION\n";
            Print::print_check_err($msg);
        }
    }

    close(FILE);
    close(FILE_TMP);
    unless (rename $cfile, $cfile . ".bak") {
        print STDERR "Error backing up original host config file\n";
    }
    unless (rename $tmpcfile, $cfile) {
        print STDERR "Error renaming new host config file\n";
    }

    Notes::convert(\%img2vol);
    Hash::convert(\%img2vol2);
}

# reads host config file and sets global hash values for images and other
sub read_host_config {

    if ($::LIVE == 1) {
        %Caseman::mod2vol    = ();
        %Caseman::vol2str    = ();
        %Caseman::vol2uni    = ();
        %Caseman::vol2blkls  = ();
        $Caseman::tz         = "";
        $Caseman::ts         = 0;
        $Caseman::host_desc  = "";
        $Caseman::exclude_db = "";
        $Caseman::alert_db   = "";
        return;
    }

    my $host = Args::get_host();

    my $cfile = host_config_fname();
  restart:
    unless (open(FILE, $cfile)) {
        Print::print_check_err("Error opening $cfile");
    }

    %Caseman::vol2mnt   = ();
    %Caseman::vol2ftype = ();
    %Caseman::vol2dtype = ();
    %Caseman::vol2cat   = ();
    %Caseman::mod2vol   = ();
    %Caseman::vol2str   = ();
    %Caseman::vol2uni   = ();
    %Caseman::vol2blkls = ();
    %Caseman::vol2par   = ();
    %Caseman::vol2start = ();
    %Caseman::vol2end   = ();
    $Caseman::vol2path  = ();
    $Caseman::vol2sname = ();

    $Caseman::tz         = "";
    $Caseman::ts         = 0;
    $Caseman::host_desc  = "";
    $Caseman::exclude_db = "";
    $Caseman::alert_db   = "";

    while (<FILE>) {
        next if ((/^\#/) || (/^\s+$/));

        # remove whitespace
        s/^\s+//;
        s/\s+$//;

        # old file system image entry
        #
        # 'image	images/hda1.dd		openbsd		/usr
        if (/^image\s+($::REG_IMG)\s+([\w\-]+)\s+([\w\-\_\.\/:\\]+)$/o) {

            close(FILE);
            convert_host_config();
            goto restart;
        }
        elsif (
            /^image\s+($::REG_INAME)\s+($::REG_IMGTYPE)\s+($::REG_IMG_CONFIG)$/o
          )
        {
            my $me = $1;
            my $t  = $2;
            my $i  = $3;

            unless ((-e "$::host_dir$i")
                || ((-l "$::host_dir$i") && (-e readlink "$::host_dir$i")))
            {
                Print::print_check_err(
                        "Error: image $i in ${host}.host:$. not found: "
                      . "$::host_dir"
                      . "$i \nEdit the config file and refresh your browser\n"
                      . "(Or your version of Perl does not support large files)"
                );
            }

            if (exists $Caseman::vol2path{$me}) {
                $Caseman::vol2path{$me} .= " \'$::host_dir" . "$i\'";
            }
            else {
                $Caseman::vol2path{$me}  = "\'$::host_dir" . "$i\'";
                $Caseman::vol2sname{$me} = $i;
                $Caseman::vol2sname{$me} = $1 if ($i =~ /\/($::REG_FILE)$/);

                $Caseman::vol2par{$me}   = "";
                $Caseman::vol2cat{$me}   = "image";
                $Caseman::vol2itype{$me} = $t;
                $Caseman::vol2ftype{$me} = "";

                $Caseman::vol2start{$me} = 0;
                $Caseman::vol2end{$me}   = 0;
            }
        }
        elsif (
/^part\s+($::REG_VNAME)\s+($::REG_INAME)\s+(\d+)\s+(\d+)\s+($::REG_FTYPE)\s*([\w\-\_\.\/:\\]+)?$/o
          )
        {
            my $par = $2;
            my $me  = $1;
            my $s   = $3;
            my $e   = $4;
            my $t   = $5;
            my $r   = $6;    # Not defined for swap and raw

            unless (exists $Fs::addr_unit{$t}) {
                Print::print_check_err(
                        "Error: unknown type: $t in host config: $."
                      . "\nEdit the file and refresh your browser");
            }

            if (exists $Caseman::vol2itype{$par}) {
                $Caseman::vol2itype{$me} = $Caseman::vol2itype{$par};
            }
            else {
                Print::print_check_err(
"Error: Image $par for partition $me was not found in config: $."
                      . "\nEdit the file and refresh your browser");
            }

            $Caseman::vol2ftype{$me} = $t;
            $Caseman::vol2cat{$me}   = "part";
            $Caseman::vol2start{$me} = $s;
            $Caseman::vol2end{$me}   = $e;

            # Add trailing / to original mount point if needed
            if ((defined $r) && ($r !~ /.*?\/$/) && ($r !~ /.*?\\$/)) {
                $r .= '/';
            }
            $Caseman::vol2mnt{$me}   = $r;
            $Caseman::vol2par{$me}   = $par;
            $Caseman::vol2path{$me}  = $Caseman::vol2path{$par};
            $Caseman::vol2sname{$me} =
              $Caseman::vol2sname{$par} . "-" . $s . "-" . $e;
        }
        elsif (/^disk\s+($::REG_VNAME)\s+($::REG_INAME)\s+($::REG_FTYPE)?$/o) {
            my $par = $2;
            my $me  = $1;
            my $t   = $3;

            unless (exists $Vs::type{$t}) {
                Print::print_check_err(
                    "Error: unknown volume system type: $t in host config: $."
                      . "\nEdit the file and refresh your browser");
            }

            if (exists $Caseman::vol2itype{$par}) {
                $Caseman::vol2itype{$me} = $Caseman::vol2itype{$par};
            }
            else {
                Print::print_check_err(
"Error: Image $par for disk $me was not found in config: $.\n"
                      . "Edit the file and refresh your browser");
            }

            $Caseman::vol2ftype{$me} = "raw";
            $Caseman::vol2dtype{$me} = $t;
            $Caseman::vol2cat{$me}   = "disk";
            $Caseman::vol2start{$me} = 0;
            $Caseman::vol2end{$me}   = 0;
            $Caseman::vol2mnt{$me}   = "";
            $Caseman::vol2par{$me}   = $par;
            $Caseman::vol2path{$me}  = $Caseman::vol2path{$par};
            $Caseman::vol2sname{$me} = $Caseman::vol2sname{$par} . "-disk";
        }

        # entry for a strings or blkls file
        #
        # strings 	data/hda1.str		volX
        elsif (/^strings\s+($::REG_VNAME)\s+($::REG_VNAME)\s+($::REG_IMG)$/o) {
            my $i   = $3;
            my $par = $2;
            my $me  = $1;

            unless ((-e "$::host_dir$i")
                || ((-l "$::host_dir$i") && (-e readlink "$::host_dir$i")))
            {
                Print::print_check_err("Error: strings file not found: "
                      . "$::host_dir$i\nEdit host config in $::host_dir and refresh your browser"
                );
            }

            unless (exists $Caseman::vol2cat{$par}) {
                Print::print_check_err(
"Error: Volume $par for strings $me was not found in config: $."
                      . "\nEdit the file and refresh your browser");
            }

            $Caseman::vol2ftype{$me} = "strings";
            $Caseman::vol2cat{$me}   = "mod";
            $Caseman::vol2itype{$me} = "raw";
            $Caseman::mod2vol{$me}   = $par;
            $Caseman::vol2str{$par}  = $me;
            $Caseman::vol2par{$me}   = $par;
            $Caseman::vol2path{$me}  = "\'$::host_dir" . "$i\'";
            $Caseman::vol2start{$me} = 0;
            $Caseman::vol2end{$me}   = 0;
            $Caseman::vol2sname{$me} = $i;
            $Caseman::vol2sname{$me} = $1 if ($i =~ /\/($::REG_FILE)$/);

        }

        # entry for a strings or blkls file
        #
        # unistrings 	data/hda1.str		volX
        elsif (/^unistrings\s+($::REG_VNAME)\s+($::REG_VNAME)\s+($::REG_IMG)$/o)
        {
            my $i   = $3;
            my $par = $2;
            my $me  = $1;

            unless ((-e "$::host_dir$i")
                || ((-l "$::host_dir$i") && (-e readlink "$::host_dir$i")))
            {
                Print::print_check_err("Error: Unicode strings file not found: "
                      . "$::host_dir$i\nEdit host config in $::host_dir and refresh your browser"
                );
            }

            unless (exists $Caseman::vol2cat{$par}) {
                Print::print_check_err(
"Error: Volume $par for unistrings $me was not found in config: $."
                      . "\nEdit the file and refresh your browser");
            }

            $Caseman::vol2ftype{$me} = "strings";
            $Caseman::vol2cat{$me}   = "mod";
            $Caseman::vol2itype{$me} = "raw";
            $Caseman::mod2vol{$me}   = $par;
            $Caseman::vol2uni{$par}  = $me;
            $Caseman::vol2par{$me}   = $par;
            $Caseman::vol2path{$me}  = "\'$::host_dir" . "$i\'";
            $Caseman::vol2start{$me} = 0;
            $Caseman::vol2end{$me}   = 0;
            $Caseman::vol2sname{$me} = $i;
            $Caseman::vol2sname{$me} = $1 if ($i =~ /\/($::REG_FILE)$/);
        }

        # blkls entry
        # blkls themname	myname
        elsif ((/^blkls\s+($::REG_VNAME)\s+($::REG_VNAME)\s+($::REG_IMG)$/o) || 
          (/^dls\s+($::REG_VNAME)\s+($::REG_VNAME)\s+($::REG_IMG)$/o)) {
            my $i   = $3;
            my $par = $2;
            my $me  = $1;

            unless (
                (-e "$::host_dir$i")
                || (   (-l "$::host_dir$i")
                    && (-e readlink "$::host_dir$i"))
              )
            {
                Print::print_check_err("Error: blkls file not found: "
                      . "$::host_dir$i \nEdit host config in $::host_dir and refresh your browser"
                );
            }

            unless (exists $Caseman::vol2cat{$par}) {
                Print::print_check_err(
"Error: Volume $par for blkls $me was not found in config: $."
                      . "\nEdit the file and refresh your browser");
            }

            $Caseman::vol2ftype{$me}  = "blkls";
            $Caseman::vol2cat{$me}    = "mod";
            $Caseman::vol2itype{$me}  = "raw";
            $Caseman::vol2mnt{$me}    = "";
            $Caseman::vol2par{$me}    = $par;
            $Caseman::mod2vol{$me}    = $par;
            $Caseman::vol2blkls{$par} = $me;
            $Caseman::vol2path{$me}   = "\'$::host_dir" . "$i\'";
            $Caseman::vol2start{$me}  = 0;
            $Caseman::vol2end{$me}    = 0;
            $Caseman::vol2sname{$me}  = $i;
            $Caseman::vol2sname{$me}  = $1 if ($i =~ /\/($::REG_FILE)$/);

        }

        # body data/body.txt
        elsif (/^body\s+($::REG_VNAME)\s+($::REG_IMG)$/o) {
            my $me = $1;
            my $i  = $2;

            unless ((-e "$::host_dir$i")
                || ((-l "$::host_dir$i") && (-e readlink "$::host_dir$i")))
            {
                Print::print_check_err("Error: body file not found: "
                      . "$::host_dir$i <br>Edit host config in $::host_dir and refresh your browser"
                );
            }

            $Caseman::vol2cat{$me}   = "timeline";
            $Caseman::vol2ftype{$me} = "body";
            $Caseman::vol2itype{$me} = "raw";
            $Caseman::vol2path{$me}  = "\'$::host_dir" . "$i\'";
            $Caseman::vol2start{$me} = 0;
            $Caseman::vol2end{$me}   = 0;
            $Caseman::vol2sname{$me} = $i;
            $Caseman::vol2sname{$me} = $1 if ($i =~ /\/($::REG_FILE)$/);
        }

        # timeline data/timeline.txt
        elsif (/^timeline\s+($::REG_VNAME)\s+($::REG_IMG)$/o) {
            my $me = $1;
            my $i  = $2;

            unless ((-e "$::host_dir$i")
                || ((-l "$::host_dir$i") && (-e readlink "$::host_dir$i")))
            {
                Print::print_check_err("Error: timeline file not found: "
                      . "$::host_dir$i \nEdit host config in $::host_dir and refresh your browser"
                );
            }

            $Caseman::vol2cat{$me}   = "timeline";
            $Caseman::vol2ftype{$me} = "timeline";
            $Caseman::vol2itype{$me} = "raw";

# We do not add the quotes to the path for timeline because it is opened only by the Perl code and it doesn't like the quotes
            $Caseman::vol2path{$me}  = "$::host_dir" . "$i";
            $Caseman::vol2start{$me} = 0;
            $Caseman::vol2end{$me}   = 0;
            $Caseman::vol2sname{$me} = $i;
            $Caseman::vol2sname{$me} = $1 if ($i =~ /\/($::REG_FILE)$/);

        }

        # timezone XYZ
        elsif (/^timezone\s+($::REG_ZONE_ARGS)$/o) {
            $Caseman::tz = "\'$1\'";
        }

        # timeskew XYZ
        elsif (/^timeskew\s+($::REG_SKEW)$/o) {
            $Caseman::ts = "\'$1\'";
        }

        # desc XYZ
        elsif (/^desc\s+(.*)$/) {
            $Caseman::host_desc = Print::html_encode($1);
        }

        # hash databases
        elsif (/^alert_db\s+'($::REG_HASHDB)'$/) {
            $Caseman::alert_db = "$1";
        }
        elsif (/^exclude_db\s+'($::REG_HASHDB)'$/) {
            $Caseman::exclude_db = "$1";
        }
        else {
            my $msg = "Error: invalid entry in $cfile:$." . "\n" . "$_\n";
            Print::print_check_err($msg);
        }
    }
    close(FILE);
}

# Add a new image entry to the host config and return its id
sub add_img_host_config {
    my $type  = shift;
    my $other = shift;
    my $id    = shift;

    my $read  = host_config_fname();
    my $write = $read . "-" . rand();

    unless (open(READ, "<$read")) {
        Print::print_check_err("Error opening $read");
    }

    unless (open(WRITE, ">$write")) {
        Print::print_check_err("Error opening $write");
    }

    my $maxcnt = 0;

    while (<READ>) {
        s/^\s+//;
        s/\s+$//;
        if (/^\w+\s+img(\d+)\s+.*$/) {
            $maxcnt = $1 if ($1 > $maxcnt);
        }
        print WRITE "$_\n";
    }
    $maxcnt++;
    if ($id eq "") {
        $id = "img" . $maxcnt;
    }
    print WRITE "$type  $id  $other\n";

    Print::log_host_info("Image added: $type $id $other");

    close(READ);
    close(WRITE);
    unless (rename $write, $read) {
        print STDERR "Error renaming temp host config file\n";
    }

    return $id;
}

# Add a new volume entry to the host config and return its id
sub add_vol_host_config {
    my $type  = shift;
    my $other = shift;

    my $read  = host_config_fname();
    my $write = $read . "-" . rand();

    unless (open(READ, "<$read")) {
        Print::print_check_err("Error opening $read");
    }

    unless (open(WRITE, ">$write")) {
        Print::print_check_err("Error opening $write");
    }

    # We want to find the max count ... not just the unused
    # ones because we could end up reusing one and that could
    # mess up the notes
    my $maxcnt = 0;

    while (<READ>) {
        s/^\s+//;
        s/\s+$//;
        if (/^\w+\s+vol(\d+)\s+.*$/) {
            $maxcnt = $1 if ($1 > $maxcnt);
        }
        print WRITE "$_\n";
    }
    $maxcnt++;
    print WRITE "$type  vol" . $maxcnt . "  $other\n";
    Print::log_host_info("Volume added: $type  vol" . $maxcnt . " $other");

    close(READ);
    close(WRITE);
    unless (rename $write, $read) {
        print STDERR "Error renaming temp host config file\n";
    }

    return "vol" . $maxcnt;
}

# Delete anentry from the host config
# DOES NOT WORK RIGHT NOW
sub del_host_config_BLAH {
    return;
    my $type  = shift;
    my $other = shift;

    my $read  = host_config_fname();
    my $write = $read . "-" . rand();

    unless (open(READ, "<$read")) {
        Print::print_check_err("Error opening $read");
    }

    unless (open(WRITE, ">$write")) {
        Print::print_check_err("Error opening $write");
    }

    while (<READ>) {
        s/^\s+//;
        s/\s+$//;
        print WRITE "$_\n"
          unless ((/^(\w+)\s+($::REG_IMG)/o)
            && ($2 eq $img)
            && ($1 ne 'desc')
            && ($1 ne 'timezone'));
    }

    if ($type ne "") {
        if (defined $ref) {
            print WRITE "$type  $img    $ref\n";
        }
        else {
            print WRITE "$type  $img\n";
        }
    }

    close(READ);
    close(WRITE);
    unless (rename $write, $read) {
        print STDERR "Error renaming temp host config file\n";
    }

    return;
}

# Given the image and md5, it is saved to the MD5.txt file and any other
# references to the image are removed
#
# if $md5 is "", then nothing is written
sub update_md5 {
    my $vol = shift;
    my $md5 = shift;
    $md5 =~ tr/[a-f]/[A-F]/;

    my $read  = "$::host_dir/md5.txt";
    my $write = $read . "-" . rand();

    unless (open(WRITE, ">$write")) {
        Print::print_check_err("Error opening $write");
    }

    if (-e "$read") {
        unless (open(READ, "<$read")) {
            Print::print_check_err("Error opening $read");
        }

        while (<READ>) {
            s/^\s+//;
            s/\s+$//;
            print WRITE "$_\n"
              unless ((/^$::REG_MD5\s+($::REG_VNAME)/o) && ($1 eq $vol));
        }
        close(READ);
    }

    print WRITE "$md5   $vol\n" if ($md5 ne "");

    close(WRITE);

    unless (rename $write, $read) {
        print STDERR "Error renaming temp MD5 hash file\n";
    }
    return;
}

sub host_add {
    Print::print_html_header("Add A New Host To $Args::args{'case'}");

    print "<b>Case: </b> $Args::args{'case'}<br><br>\n";

    print "<center>"
      . "<img src=\"pict/menu_h_hnew.jpg\" alt=\"Add Host\">"
      . "<br><br><br>\n";

    print "<table width=\"600\" cellspacing=\"0\" background=\"$::YEL_PIX\" "
      . "cellpadding=\"2\" border=0>\n"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_ADD_DOIT\">\n"
      . Args::make_hidden()
      . "<tr><td colspan=\"2\">&nbsp;</td></tr>"
      .

      # Host name
"<tr><td align=\"left\" colspan=2>1.  <b>Host Name:</b>  The name of the computer being investigated.  It can contain only letters, numbers, and symbols.</td></tr>"
      . "<tr><td align=\"left\">&nbsp;&nbsp;&nbsp;</td>"
      . "<td align=\"left\"><input type=\"text\" name=\"host\" size=32 maxlength=32 value=\"host1\"></td></tr>\n"
      .

      # Description
"<tr><td align=\"left\" colspan=2>2.  <b>Description:</b>  An optional one-line description or note about this computer.</td></tr>"
      . "<tr><td align=\"left\">&nbsp;&nbsp;&nbsp;</td>"
      . "<td align=\"left\">"
      . "<input type=\"text\" name=\"desc\" size=32 maxlength=32></td></tr>\n"
      .

      # Empty line
      "<tr><td colspan=\"2\">&nbsp;</td></tr>" .

      # Timezone
"<tr><td align=\"left\" colspan=2>3.  <b>Time zone:</b> An optional timezone value (i.e. EST5EDT).  If not given, it defaults to the local setting.  A list of time zones can be found in the help files.</td></tr>"
      . "<tr><td align=\"left\">&nbsp;&nbsp;&nbsp;</td>"
      . "<td align=\"left\">"
      . "<input type=\"text\" name=\"tz\" size=16 maxlength=64></td></tr>\n"
      .

      # Timeskew
"<tr><td align=\"left\" colspan=2>4.  <b>Timeskew Adjustment:</b> An optional value to describe how many seconds this computer's clock was out of sync.  For example, if the computer was 10 seconds fast, then enter -10 to compensate.</td></tr>"
      . "<tr><td align=\"left\">&nbsp;&nbsp;&nbsp;</td>"
      . "<td align=\"left\">"
      . "<input type=\"text\" name=\"ts\" size=8 maxlength=16 value=\"0\">"
      . "</td></tr>\n"
      .

      # Spacer
      "<tr><td colspan=\"2\">&nbsp;</td></tr>" .

      # Alert
"<tr><td align=\"left\" colspan=2>5.  <b>Path of Alert Hash Database:</b> An optional hash database of known bad files.</td></tr>"
      . "<tr><td align=\"left\">&nbsp;&nbsp;&nbsp;</td>"
      . "<td align=\"left\">"
      . "<input type=\"text\" name=\"alert_db\" size=32 maxlength=512>"
      . "</td></tr>\n"
      .

      # Ignore
"<tr><td align=\"left\" colspan=2>6.  <b>Path of Ignore Hash Database:</b> An optional hash database of known good files.</td></tr>"
      . "<tr><td align=\"left\">&nbsp;&nbsp;&nbsp;</td>"
      . "<td align=\"left\">"
      . "<input type=\"text\" name=\"exclude_db\" size=32 maxlength=512>"
      . "</td></tr>\n"
      .

      # Spacer
      "<tr><td colspan=\"2\">&nbsp;</td></tr>" . "</table>\n";

    if (exists $Args::args{'inv'}) {
        print
          "<input type=\"hidden\" name=\"inv\" value=\"$Args::args{'inv'}\">\n";
    }

    # Ok Button
    print "<br><br><table width=\"600\" cellspacing=\"8\" cellpadding=\"2\">\n"
      . "<tr><td align=center>"
      . "<input type=\"image\" src=\"pict/menu_b_hnew.jpg\" "
      . "width=167 height=20 alt=\"Add Host\" border=0>\n"
      . "</form></td>\n"
      .

      # Cancel Button
      "<td align=center>"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_OPEN\">\n"
      . "<input type=\"hidden\" name=\"case\" value=\"$Args::args{'case'}\">\n"
      . "<input type=\"image\" src=\"pict/menu_b_cancel.jpg\" "
      . "alt=\"Cancel\" width=\"167\" height=20 border=0>\n"
      . "</form></td>\n"
      .

      # Help Button
      "<td align=center><a href=\"$::HELP_URL\" "
      . "target=\"_blank\">"
      . "<img src=\"pict/menu_b_help.jpg\" alt=\"Help\" "
      . "width=\"167\" height=20 border=0></a>"
      . "</td></tr>\n"
      . "</table>";

    Print::print_html_footer();

    return 0;
}

# Make the directories and config files for a host
sub host_add_doit {
    Args::check_tz()
      if ((exists $Args::args{'tz'}) && ($Args::args{'tz'} ne ""));
    Args::check_ts();
    Print::print_html_header(
        "Adding Host $Args::args{'host'} to $Args::args{'case'}");

    print "<h3>Adding host: <tt>$Args::args{'host'}</tt> to "
      . "case <tt>$Args::args{'case'}</tt></h3>\n";

    # Do some sanity checks before we start making the directories and such
    if (   (exists $Args::args{'alert_db'})
        && ($Args::args{'alert_db'} ne ""))
    {

        unless ($Args::args{'alert_db'} =~ /^$::REG_HASHDB$/o) {
            print "Invalid Alert Database path\n"
              . "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
              . "view=$Caseman::HOST_ADD&case=$Args::args{'case'}\">"
              . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
              . "width=\"43\" height=20 border=\"0\"></a>\n";
            return 1;
        }

        unless (-e "$Args::args{'alert_db'}") {
            print "Alert Database Not Found: $Args::args{'alert_db'}"
              . "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
              . "view=$Caseman::HOST_ADD&case=$Args::args{'case'}\">"
              . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
              . "width=\"43\" height=20 border=\"0\"></a>\n";
            return 1;
        }
    }

    if (   (exists $Args::args{'exclude_db'})
        && ($Args::args{'exclude_db'} ne ""))
    {
        unless ($Args::args{'exclude_db'} =~ /^$::REG_HASHDB$/o) {
            print "Invalid Exclude Database path\n"
              . "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
              . "view=$Caseman::HOST_ADD&case=$Args::args{'case'}\">"
              . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
              . "width=\"43\" height=20 border=\"0\"></a>\n";
            return 1;
        }

        unless (-e "$Args::args{'exclude_db'}") {
            print "Exclude Database Not Found: $Args::args{'exclude_db'}"
              . "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
              . "view=$Caseman::HOST_ADD&case=$Args::args{'case'}\">"
              . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
              . "width=\"43\" height=20 border=\"0\"></a>\n";
            return 1;
        }
    }

    # Make the directory
    if (-d "$::host_dir") {

        print "Error: $::host_dir already exists<br>"
          . "Please remove the directory and its contents and try again"
          . "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
          . "view=$Caseman::HOST_OPEN&case=$Args::enc_args{'case'}\">"
          . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
          . "width=\"43\" height=20 border=\"0\"></a>\n";
        Print::print_err("\n");
    }
    unless (mkdir "$::host_dir", $::MKDIR_MASK) {
        Print::print_err("Error making directory $::host_dir: $!");
    }

    print "Host Directory (<tt>$::host_dir</tt>) created<p>\n";
    Print::log_case_info("Host $Args::args{'host'} added to case");

    # Images directory
    unless (mkdir "$::host_dir" . "$::IMGDIR", $::MKDIR_MASK) {
        rmdir "$::host_dir";
        Print::print_err("Error making $::host_dir" . "$::IMGDIR");
    }

    # Output Directory
    unless (mkdir "$::host_dir" . "$::DATADIR", $::MKDIR_MASK) {
        rmdir "$::host_dir" . "$::IMGDIR";
        rmdir "$::host_dir";
        Print::print_err("Error making $::host_dir" . "$::DATADIR");
    }

    # Log Directory
    unless (mkdir "$::host_dir" . "$::LOGDIR", $::MKDIR_MASK) {
        rmdir "$::host_dir" . "$::DATADIR";
        rmdir "$::host_dir" . "$::IMGDIR";
        rmdir "$::host_dir";
        Print::print_err("Error making $::host_dir" . "$::LOGDIR");
    }

    # Reports directory
    unless (mkdir "$::host_dir" . "$::REPDIR", $::MKDIR_MASK) {
        rmdir "$::host_dir" . "$::LOGDIR";
        rmdir "$::host_dir" . "$::DATADIR";
        rmdir "$::host_dir" . "$::IMGDIR";
        rmdir "$::host_dir";
        Print::print_err("Error making $::host_dir" . "$::REPDIR");
    }

    # Make a directory for mounting the image in loopback
    unless (mkdir "$::host_dir" . "mnt", $::MKDIR_MASK) {
        rmdir "$::host_dir" . "$::REPDIR";
        rmdir "$::host_dir" . "$::LOGDIR";
        rmdir "$::host_dir" . "$::DATADIR";
        rmdir "$::host_dir" . "$::IMGDIR";
        rmdir "$::host_dir";
        Print::print_err("Error making $::host_dir" . "mnt");
    }

    Print::log_host_info(
        "Host $Args::args{'host'} added to case $Args::args{'case'}");

    # Create config file
    my $fname = Caseman::host_config_fname();
    open HOST_CONFIG, ">$fname" or die "Can't open host config: $fname";

    print HOST_CONFIG "# Autopsy host config file\n"
      . "# Case: $Args::args{'case'}		Host: $Args::args{'host'}\n"
      . "# Created: "
      . localtime() . "\n\n";

    if ((exists $Args::args{'desc'}) && ($Args::args{'desc'} ne "")) {
        Print::print_err(
            "Invalid Description\n" . "Use the browser's back button to fix")
          if ($Args::args{'desc'} =~ /\n/);

        print HOST_CONFIG "desc $Args::args{'desc'}\n";
    }

    print HOST_CONFIG "timezone  " . Args::get_tz() . "\n"
      if ((exists $Args::args{'tz'}) && ($Args::args{'tz'} ne ""));
    print HOST_CONFIG "timeskew  " . Args::get_ts() . "\n";

    if (   (exists $Args::args{'alert_db'})
        && ($Args::args{'alert_db'} ne ""))
    {

        # Index it if it is not
        unless (-e "$Args::args{'alert_db'}-md5.idx") {
            print
"Alert Database has not been indexed - it will be as an md5sum file<br>\n";

            print "<hr>\n";
            Hash::index_md5sum($Args::args{'alert_db'});
            print "<hr>\n";
        }

        # only print it if it was successful
        print HOST_CONFIG "alert_db \'$Args::args{'alert_db'}\'\n"
          if (-e "$Args::args{'alert_db'}-md5.idx");
    }

    if (   (exists $Args::args{'exclude_db'})
        && ($Args::args{'exclude_db'} ne ""))
    {

        # Index it if it is not
        unless (-e "$Args::args{'exclude_db'}-md5.idx") {
            print
"Exclude Database has not been indexed - it will be as an md5sum file<br>\n";

            print "<hr>\n";
            Hash::index_md5sum($Args::args{'exclude_db'});
            print "<hr>\n";
        }

        # only print it if it was successful
        print HOST_CONFIG "exclude_db \'$Args::args{'exclude_db'}\'\n"
          if (-e "$Args::args{'exclude_db'}-md5.idx");
    }

    close HOST_CONFIG;

    print "Configuration file (<tt>$fname</tt>) created<br><br>\n";

    print "We must now import an image file for this host\n";

    print "<br><br><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
      . "view=$Caseman::HOST_OPEN_LOG&$Args::baseargs\">"
      . "<img src=\"pict/menu_b_inew.jpg\" alt=\"Add Image\" "
      . " height=20 border=\"0\"></a>\n";

    Print::print_html_footer();
    return 0;
}

# Open a host in the given case
sub host_open {
    Print::print_html_header("Open Host In $Args::args{'case'}");

    # Create an array of directories in the case, verifying that there is
    # a config file
    my @hosts;
    opendir HOSTS, $::case_dir or die "Can't open $::case_dir directory: $!";
    foreach my $h (readdir HOSTS) {
        next if (($h eq '.') || ($h eq '..'));

        my $hfile = Caseman::host_config_fname($h);
        push @hosts, $h
          if ((-d "$::case_dir" . "$h") && (-e "$hfile"));
    }
    closedir HOSTS;

    print "<b>Case:</b> $Args::args{'case'}<br><br>\n";

    print "<center>";

    if (scalar @hosts == 0) {
        print "No hosts have been added to the case yet"
          . "<br><br>Select the Add Host button below to create one.<br>\n";
    }
    else {

        print "Select the host to open or create a new one<br>\n";

        print_menu_tabs();

        print "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\" "
          . "background=\"$::YEL_PIX\" border=0>\n";

        print "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_OPEN_LOG\">\n"
          . Args::make_hidden()
          . "<tr><th>Name</th>"
          . "<th>Description</th><th>&nbsp;</th></tr>\n";

        my $first = 0;
        foreach my $h (@hosts) {

            print "<tr><td align=\"left\">"
              . "<input type=\"radio\" name=\"host\" value=$h";
            if ($first == 0) {
                print " CHECKED";
                $first = 1;
            }
            print "> " . Print::html_encode($h) . " </td>";

            my $fname = Caseman::host_config_fname($h);
            open CONFIG, "<$fname"
              or die "Can't open host config file ($fname)";

            my $desc = "None Provided";
            while (<CONFIG>) {
                s/^\s+//;
                s/\s+$//;

                if (/^desc\s+(.*)$/) {
                    $desc = Print::html_encode($1);
                    last;
                }
            }
            close CONFIG;

            print "<td align=left>$desc</td>"
              . "<td align=center>"
              . "<a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
              . "view=$Caseman::HOST_DETAILS&$Args::baseargs&"
              . "host=$h\">details</a></td></tr>\n";
        }
        print "</table>\n";

        # Display pulldown of investigators
        my @invs = read_invest();
        if (scalar @invs == 0) {
            print "<input type=\"hidden\" name=\"inv\" value=\"unknown\">\n";
        }
        else {
            print "<br>Investigator (for reports only): ";
            my $cur_inv = "";
            $cur_inv = $Args::args{'inv'} if (exists $Args::args{'inv'});

            print "<select name=\"inv\" size=\"1\">\n";

            if (($cur_inv eq "") && (scalar @invs != 1)) {
                print "<option value=\"\" selected>Select One" . "</option>\n";
            }
            foreach my $i (@invs) {
                print "<option value=\"$i\"";
                print " selected" if ($cur_inv eq $i);
                print ">" . Print::html_encode($i) . "</option>\n";
            }
            print "</select>\n";
        }
    }
    print "<br><br><table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">\n"
      . "<tr>\n";

    # Make a table for the buttons.  The table will either be 3 or 2
    # entries wide, depending on if there is an 'Ok' button or not

    unless (scalar @hosts == 0) {
        print "<td align=center>"
          . "<input type=\"image\" src=\"pict/menu_b_ok.jpg\" "
          . "alt=\"Ok\" width=\"167\" height=20 border=0>\n"
          . "</form>\n</td>\n";
    }

    # Add Host
    print "<td align=center>"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_ADD\">\n"
      . "<input type=\"hidden\" name=\"case\" value=\"$Args::args{'case'}\">\n"
      . "<input type=\"image\" src=\"pict/menu_b_hnew.jpg\" "
      . "alt=\"Add Host\" width=\"176\" height=20 border=0>\n"
      . "</form></td>"
      .

      # Close Button
      "<td align=center>"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::CASE_OPEN\">\n"
      . "<input type=\"image\" src=\"pict/menu_b_ccls.jpg\" "
      . "alt=\"Close Case\" width=\"176\" height=20 border=0>\n"
      . "</form></td></tr></table>\n";

    print "<table width=\"600\"  cellspacing=\"0\" cellpadding=\"2\">\n"
      . "<tr><td>&nbsp;</td>"
      . "<td align=center><a href=\"$::HELP_URL\" "
      . " target=\"_blank\">"
      . "<img src=\"pict/menu_b_help.jpg\" alt=\"Help\" "
      . "width=\"167\" height=20 border=0>"
      . "</a></td><td>&nbsp;</td></tr>\n"
      . "</table>\n";

    Print::print_html_footer();
    return 0;
}

# Log that a given host was opened and then proceed to open an image
sub host_open_log {
    unless ((exists $Args::args{'inv'}) && ($Args::args{'inv'} ne "")) {
        my @invs = read_invest();
        if (scalar @invs == 0) {
            $Args::args{'inv'} = $Args::enc_args{'inv'} = 'unknown';
        }
        else {
            Print::print_html_header("Missing Investigator");
            print "<br>An investigator must be selected<p>\n"
              . "<form action=\"$::PROGNAME\" method=\"get\">\n"
              . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
              . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_OPEN_LOG\">\n"
              . Args::make_hidden();

            print "Select one of the following:";
            print "<select name=\"inv\" size=\"1\">\n";

            print "<option value=\"\" selected>Select One" . "</option>\n";

            foreach my $i (@invs) {
                print "<option value=\"$i\">$i</option>\n";
            }
            print "</select><p>\n"
              . "<input type=\"image\" src=\"pict/but_ok.jpg\" alt=\"Ok\" "
              . "width=43 height=20 border=\"0\">\n"
              . "</form>\n";

            Print::print_html_footer();
            return 0;
        }
    }

    Args::check_inv();
    Print::log_case_info(
        "Host $Args::args{'host'} opened by $Args::args{'inv'}");
    Print::log_host_info(
        "Host $Args::args{'host'} opened by $Args::args{'inv'}");
    Print::log_host_inv("Host $Args::args{'host'} opened");

    $Args::args{'view'} = $Args::enc_args{'view'} = $Caseman::VOL_OPEN;
    vol_open();
}

# Provide details about the configuration of a host.  This window is
# a link from the HOST_OPEN window
sub host_details {
    Print::print_html_header(
        "Details of $Args::args{'case'}:$Args::args{'host'}");

    print "<b>Case: </b>$Args::args{'case'}<br><br>"
      . "<center>"
      . "<img src=\"pict/menu_h_hdet.jpg\" alt=\"Host Details\">"
      . "<br><br><br>\n"
      . "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\" "
      . "background=\"$::YEL_PIX\" border=0>\n"
      .

      # Name
      "<tr><td align=\"right\" width=300><b>Name:</b></td>"
      . "<td align=\"left\" width=300><tt>$Args::args{'host'}</tt></td></tr>\n"
      .

      # Description
      "<tr><td align=\"right\"><b>Description:</b></td>"
      . "<td align=\"left\"><tt>"
      . (($Caseman::host_desc ne "") ? $Caseman::host_desc : "&nbsp;")
      . "</tt></td></tr>\n"
      .

      # Timezone
      "<tr><td align=\"right\"><b>Time zone: </b></td>"
      . "<td align=\"left\"><tt>$Caseman::tz</tt></td></tr>\n"
      .

      # Timeskew
      "<tr><td align=\"right\"><b>Timeskew:</b></td>"
      . "<td align=\"left\"><tt>$Caseman::ts</tt></td></tr>\n"
      . "<tr><td colspan=2>&nbsp;</td></tr>\n"
      .

      # Actual Directory
      "<tr><td align=\"right\"><b>Directory:</b></td>"
      . "<td align=\"left\"><tt>"
      . Print::html_encode($::host_dir)
      . "</tt></td></tr>\n"
      . "<tr><td colspan=2>&nbsp;</td></tr>\n"
      .

      # Alert Database
      "<tr><td align=\"right\"><b>Alert Hash Database:</b></td>"
      . "<td align=\"left\"><tt>"
      . (($Caseman::alert_db ne "")
        ? Print::html_encode($Caseman::alert_db)
        : "&nbsp;")
      . "</tt></td></tr>\n"
      .

      # Exclude Database
      "<tr><td align=\"right\"><b>Exclude Hash Database:</b></td>"
      . "<td align=\"left\"><tt>"
      . (($Caseman::exclude_db ne "")
        ? Print::html_encode($Caseman::exclude_db)
        : "&nbsp;")
      . "</tt></td></tr>\n"
      . "</table>\n";

    # Final Button
    print "<br><br><form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_OPEN\">\n"
      . Args::make_hidden()
      . "<input type=\"image\" src=\"pict/menu_b_ok.jpg\" "
      . "alt=\"Ok\" width=\"167\" height=20 border=\"0\">\n</form>";

    Print::print_html_footer();

    return;
}

# Read the investigators file and return a sorted list
sub read_invest {
    my $fname = investig_fname();
    open INVES, "<$fname" or return;

    my @investigs;
    while (<INVES>) {
        chomp;
        s/^\s+//;
        s/\s+$//;
        push @investigs, $1
          if (/^($::REG_INVESTIG)$/o);
    }
    close(INVES);
    sort { lc($a) cmp lc($b) } @investigs;
}

# File of investigators name in list
sub investig_fname {
    return "$::case_dir" . "investigators.txt";
}

####################################################################
# Image Functions

# Types of modes for fname (i.e. can we overwrite it if it exists)
my $FNAME_MODE_INIT = 0;
my $FNAME_MODE_OVER = 1;

my $MD5_NOTHING = 1;
my $MD5_CALC    = 2;
my $MD5_ADD     = 3;

my $IMG_ADD_SYM  = 1;
my $IMG_ADD_COPY = 2;
my $IMG_ADD_MOVE = 3;

# Open an image that has been configured
sub vol_open {
    Print::print_html_header(
        "Open Image In $Args::args{'case'}:$Args::args{'host'}");

    print "<b>Case:</b> $Args::args{'case'}<br>\n";
    print "<b>Host:</b> $Args::args{'host'}<br>\n";
    print "<center>\n";

    # the images have been loaded from reading the host config file in
    # autopsy_main
    if (scalar(keys %Caseman::vol2ftype) == 0) {
        print "No images have been added to this host yet<br><br>\n"
          . "Select the Add Image File button below to add one\n";
        goto EGRESS;
    }

    if ($::LIVE == 1) {
        print "Select a volume to analyze.<br>\n";
    }
    else {
        print "Select a volume to analyze or add a new image file.<br>\n";
    }

    print_menu_tabs();

    print "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\" "
      . "background=\"$::YEL_PIX\" border=0>\n";

    # We want to sort, so rearrange the hash
    my %mnt2vol;
    my %par2disk;

    # Cycle through each image we read from the host config
    foreach my $i (keys %Caseman::vol2cat) {
        if ($Caseman::vol2cat{$i} eq "disk") {
            $mnt2vol{"1disk--AUTOPSY--$i"} = $i;
        }
        elsif ($Caseman::vol2cat{$i} eq "part") {
            if (   ($Caseman::vol2ftype{$i} eq "raw")
                || ($Caseman::vol2ftype{$i} eq "swap"))
            {
                $mnt2vol{"2$Caseman::vol2ftype{$i}--AUTOPSY--$i"} = $i;
            }
            else {
                $mnt2vol{"2$Caseman::vol2mnt{$i}--AUTOPSY--$i"} = $i;
            }
        }
    }

    # sort via parent volume, then starting location,
    # and then mount point (which includes the name)
    my @mnt = sort {
        ($Caseman::vol2par{$mnt2vol{$a}} cmp $Caseman::vol2par{$mnt2vol{$b}})
          or ($Caseman::vol2start{$mnt2vol{$a}} <=>
            $Caseman::vol2start{$mnt2vol{$b}})
          or (lc($a) cmp lc($b))
    } keys %mnt2vol;

    # It is possible to have only the blkls image and not the original
    # We need to search for those now because they will not be in the
    # list that we just made (which are arranged via mount point)
    my @orphan_blkls;

    # cycle through each image and check its type and original
    foreach my $k (keys %Caseman::vol2ftype) {
        if (   ($Caseman::vol2ftype{$k} eq "blkls")
            && (!exists $Caseman::mod2vol{$k}))
        {
            push @orphan_blkls, $k;
        }
    }

    print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_OPEN_LOG\">\n"
      . Args::make_hidden()

      . "<tr><th>&nbsp;</th>"
      . "<th align=left>mount</th>"
      . "<th align=left>name</th>"
      .    # vol name
      "<th align=left>fs type</th></tr>\n";

    my $prev_par = "";

    for (my $i = 0; $i <= $#mnt; $i++) {
        my $vol = $mnt2vol{$mnt[$i]};

        if ($Caseman::vol2par{$vol} ne $prev_par) {
            print "<tr><td colspan=5><hr></td></tr>\n" if ($i != 0);
            $prev_par = $Caseman::vol2par{$vol};
        }

        # Mount Point
        # If we have the dummy string at the end of the duplicate
        # entry, then take it off and print the original
        $mnt[$i] = $1 if ($mnt[$i] =~ /^\d(.*?)--AUTOPSY--$::REG_VNAME$/o);
        print "<tr>" . "<td><input type=\"radio\" name=\"vol\" value=$vol";
        print " CHECKED" if ($i == 0);
        print "></td>"
          . "<td><tt>"
          . Print::html_encode($mnt[$i])
          . "</tt></td>";

        # image name and ftype
        print
"<td><tt>$Caseman::vol2sname{$vol}</tt></td><td>$Caseman::vol2ftype{$vol}</td>";
        if ($::LIVE == 0) {
            print "<td align=center><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
              . "view=$Caseman::VOL_DETAILS&$Args::baseargs&"
              . "vol=$vol\">details</a></td>"
              . "</tr>\n";
        }
        else {
            print "<td>&nbsp;</td></tr>\n";
        }
    }

    # If we are done with the regular images and have some orphan
    # blkls images, print them
    my @sort = sort @orphan_blkls;
    for (my $i = 0; $i <= $#sort; $i++) {
        print
"<tr><td>&nbsp;</td><td>&nbsp;</td><td>(<input type=\"radio\" name=\"vol\" "
          . "value=$sort[$i]";
        print " CHECKED" if ($#mnt == 0);
        print "> unalloc)</td><td><tt>"
          . Print::html_encode($Caseman::vol2sname{$sort[$i]})
          . "</tt></td><td>"
          . Print::html_encode($Caseman::vol2ftype{$sort[$i]})
          . "</td></tr>\n";
    }

    # Begin Button
    print "</table>\n";

  EGRESS:

    print "<br><br>"
      . "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">\n";

    # Ok Button
    if (scalar(keys %Caseman::vol2ftype) == 0) {
        print "<tr><td width=200>&nbsp;</td>\n";
    }
    else {
        print "<tr><td align=center width=200>"
          . "<input type=\"image\" src=\"pict/menu_b_analyze.jpg\" "
          . "alt=\"Analyze\" width=\"167\" height=20 border=0>\n"
          . "</form></td>\n";
    }

    # Image Add Button
    if ($::LIVE == 0) {
        print "<td align=center width=200>"
          . "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::IMG_ADD\">\n"
          . Args::make_hidden()
          . "<input type=\"image\" src=\"pict/menu_b_ifnew.jpg\" "
          . "alt=\"Add Image\" width=167 height=20 border=0></form></td>\n"
          .

          # Cancel Button
          "<td align=center width=200>"
          . "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::HOST_OPEN\">\n"
          . "<input type=\"hidden\" name=\"case\" value=\"$Args::args{'case'}\">\n"
          . "<input type=\"image\" src=\"pict/menu_b_hcls.jpg\" "
          . "width=167 height=20 alt=\"Close Host\" border=0>\n"
          . "</form></td></tr>";
    }
    else {
        print "<td width=200>&nbsp;</td><td width=200>&nbsp;</td></tr>\n";
    }

    # Help Button
    print
"<td width=200>&nbsp;</td><td align=center width=200><a href=\"$::HELP_URL\" "
      . " target=\"_blank\">"
      . "<img src=\"pict/menu_b_help.jpg\" alt=\"Help\" "
      . "width=\"167\" height=20 border=0>"
      . "</a></td><td width=200>&nbsp;</td></tr>\n"
      . "</table>\n";

    # Other features that can be done on a host

    if ($::LIVE == 0) {
        print "<hr><p>"
          . "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">\n"
          . "<tr>\n";

        # Timeline of file activity
        print "<td align=\"center\" width=200>"
          . "<a href=\"$::PROGNAME?${Args::baseargs_novol}&"
          . "mod=$::MOD_TL&view=$Timeline::FRAME\">"
          . "<img border=0 "
          . "src=\"pict/menu_b_tl.jpg\" "
          . "width=\"167\" height=20 "
          . "alt=\"File Activity Timelines\"></a></td>\n";

        # verify the integrity of the images
        print "<td align=\"center\" width=200>"
          . "<a href=\"$::PROGNAME?${Args::baseargs_novol}&"
          . "mod=$::MOD_HASH&view=$Hash::IMG_LIST_FR\">"
          . "<img border=0 "
          . "src=\"pict/menu_b_int.jpg\" "
          . "width=\"167\" height=20 "
          . "alt=\"Image Integrity\"></a></td>\n"
          .

          # Hashdatabases
          "<td align=\"center\" width=200>"
          . "<a href=\"$::PROGNAME?${Args::baseargs_novol}&"
          . "mod=$::MOD_HASH&view=$Hash::DB_MANAGER\">"
          . "<img border=0 "
          . "src=\"pict/menu_b_hashdb.jpg\" "
          . "width=\"167\" height=20 "
          . "alt=\"Hash Databases\"></a></td>\n"
          . "</tr></table>\n";

        # Notes
        if ($::USE_NOTES == 1) {
            print "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">\n"
              . "<tr>\n"
              . "<td align=\"center\" width=300>"
              . "<a href=\"$::PROGNAME?${Args::baseargs_novol}&mod=$::MOD_NOTES&view=$Notes::READ_NORM\">"
              . "<img border=0 "
              . "src=\"pict/menu_b_note.jpg\" "
              . "width=\"167\" height=20 "
              . "alt=\"View Notes\"></a></td>\n"
              .

              "<td width=300 align=\"center\">"
              . "<a href=\"$::PROGNAME?${Args::baseargs_novol}&mod=$::MOD_NOTES&view=$Notes::READ_SEQ\">"
              . "<img border=0 "
              . "src=\"pict/menu_b_seq.jpg\" "
              . "width=\"167\" height=20 "
              . "alt=\"Event Sequencer\"></a></td>\n"
              .

              "</tr>\n" . "</table>\n";
        }

        # If LIVE
    }
    else {
        print "<a href=\"./about\"><br>\n"
          . "<img src=\"pict/logo.jpg\" border=0 alt=\"Logo\"></a><br>\n";

    }

    Print::print_html_footer();

    return 0;
}

# Log in the host log that a given image was opened by what user
# then open the main window
sub vol_open_log {

    # These will be stopped in the func during  LIVE
    Print::log_host_info(
        "Image $Args::args{'vol'} opened by $Args::args{'inv'}");
    Print::log_host_inv("$Args::args{'vol'}: volume opened");

    $Args::args{'mod'}  = $Args::enc_args{'mod'}  = $::MOD_FRAME;
    $Args::args{'view'} = $Args::enc_args{'view'} = $Frame::IMG_FRAME;
    Frame::main();
}

# Menu to add a new image to the host
#
# The list of new images is determined by looking in the images directory
# and seeing which is not yet configured
#
sub img_add {
    Print::print_html_header(
        "Add Image To $Args::args{'case'}:$Args::args{'host'}");

    print "<b>Case:</b> $Args::args{'case'}<br>\n"
      . "<b>Host:</b> $Args::args{'host'}<br>\n";

    print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::IMG_ADD_PREP\">\n"
      . Args::make_hidden();

    print <<EOF1;
<center>
<img src=\"pict/menu_h_inew.jpg\" alt=\"Add Image\">
<br><br><br>

<table width=\"600\" cellpadding=\"2\" cellspacing=\"0\" background=\"$::YEL_PIX\" border=0>
<tr>
  <td colspan=4>&nbsp;</td>
</tr>

<tr>
  <td align=left colspan=4>
    1. <b>Location</b><br>Enter the full path (starting with <tt>/</tt>) to the image file.<br>
    If the image is split (either raw or EnCase), then enter '*' for the extension.
  </td>
</tr>
<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=3>
    <input type=\"text\" name=\"img_path\" size=36 maxlength=256>
  </td>
</tr>
<tr><td colspan=4>&nbsp;</td><tr>
<tr>
  <td align=left colspan=4>2. <b>Type</b><br>Please select if this image file is for a disk or a single partition.</td>
</tr>
<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left>
    <input type=\"radio\" name=\"imgtype\" value="disk" CHECKED>
      Disk
  </td>
  <td align=left>
    <input type=\"radio\" name=\"imgtype\" value="volume">
      Partition 
  </td>
  <td align=left>&nbsp;
  </td>
</tr>

<tr><td colspan=4>&nbsp;</td><tr>
<tr>
  <td align=left colspan=4>3. <b>Import Method</b><br>To analyze the image file, it must be located in the evidence locker. It can be imported from its current location using a symbolic link, by copying it, or by moving it.  Note that if a system failure occurs during the move, then the image could become corrupt.</td>
</tr>
<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left>
    <input type=\"radio\" name=\"sort\" value=$IMG_ADD_SYM CHECKED>
      Symlink
  </td>
  <td align=left>
    <input type=\"radio\" name=\"sort\" value=$IMG_ADD_COPY>
      Copy 
  </td>
  <td align=left>
    <input type=\"radio\" name=\"sort\" value=$IMG_ADD_MOVE>
	  Move 
  </td>
</tr>


<tr>
  <td colspan=4>&nbsp;</td>
</tr>

</table>

<br>
<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">
<tr>
  <td align=center colspan=2>
    <input type=\"image\" src=\"pict/menu_b_next.jpg\" alt=\"Next Step\" width=\"167\" height=20 border=0>
  </td>
</tr>

</form>

EOF1

    print "<tr><td colspan=2>&nbsp;</td></tr>\n"
      . "<td align=center>\n"
      . "    <form action=\"$::PROGNAME\" method=\"get\">\n"
      . "    <input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "    <input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_OPEN\">\n"
      .

      Args::make_hidden()
      . "    <input type=\"image\" src=\"pict/menu_b_cancel.jpg\" "
      . "alt=\"Cancel\" width=\"167\" height=20 border=0></form>\n"
      . "  </td>\n"
      .

      # HELP
      "  <td align=center>\n"
      . "    <a href=\"$::HELP_URL\" target=\"_blank\">\n"
      . "    <img src=\"pict/menu_b_help.jpg\" alt=\"Help\" "
      . "width=\"167\" height=20 border=0></a>\n"
      . "  </td>\n"
      . "</tr>\n"
      . "</table>\n";

    Print::print_html_footer();

    return 0;
}

# List the images from the glob - called from img_add_prep if spl_conf is not set
sub img_add_split_conf {
    my $img_path = Args::get_img_path_wild();
    my $img_type = $Args::args{'imgtype'};

    print "<center><br><br><b>Split Image Confirmation</b><br><br>\n";

    my @spl_img = glob($img_path);
    if (scalar(@spl_img) == 0) {
        print "No images were found at this location ($img_path)<br>\n"
          . "Use the back button and fix the path<br>\n";
        return;
    }

    print <<EOF1;
The following images will be added to the case.<br>
If this is not the correct order, then you should change the naming convention.<br>
Press the Next button at the bottom of the page if this is correct.<br><br>

<table width=\"600\" cellpadding=\"2\" cellspacing=\"0\" background=\"$::YEL_PIX\" border=0>
<tr>
  <td colspan=2>&nbsp;</td>
</tr>
EOF1

    my $a = 0;
    foreach $i (@spl_img) {

       # We need to do this when we analyze the image, so do it here too to show
       # what will be analyzed
        $i = $1 if ($i =~ /^($::REG_IMG_PATH)$/);
        print
"<tr><td align=\"center\">$a</td><td align=\"left\"><tt>$i</tt></td></tr>\n";
        $a++;
    }

    my $vs = "";
    $vs = "&vstype=$Args::args{'vstype'}"
      if (exists $Args::args{'vstype'});

    # Print the Ok Button
    print "</table><br><br>\n"
      . "<table width=\"600\" cellpadding=\"2\" cellspacing=\"0\" border=0>"
      . "<tr><td width=\"300\" align=\"center\"><a href=\"$::PROGNAME?${Args::baseargs_novol}&"
      . "mod=$::MOD_CASEMAN&view=$Caseman::IMG_ADD_PREP&spl_conf=1&sort=$Args::args{'sort'}&"
      . "img_path=$img_path&imgtype=$Args::args{'imgtype'}$vs\">"
      . "<img src=\"pict/menu_b_next.jpg\" alt=\"Next\" "
      . "width=\"167\" height=20 border=0></a></td>\n"
      . "<td width=\"300\" align=\"center\"><a href=\"$::PROGNAME?${Args::baseargs_novol}&"
      . "mod=$::MOD_CASEMAN&view=$Caseman::IMG_ADD\">"
      . "<img src=\"pict/menu_b_cancel.jpg\" alt=\"cancel\" width=\"167\" height=\"20\" border=\"0\">"
      . "</a></td></tr></table>\n";

    return 0;
}

# Run the autodetect stuff and get confirmation from the user
sub img_add_prep {
    Args::check_img_path_wild();
    Args::check_sort();
    unless ((exists $Args::args{'imgtype'})
        && ($Args::args{'imgtype'} =~ /^\w+$/))
    {
        Print::print_check_err("Invalid image type");
    }

    Print::print_html_header("Collecting details on new image file");

    my $img_path = Args::get_img_path_wild();

    my $img_type = $Args::args{'imgtype'};
    my $spl_conf = 0;
    $spl_conf = 1
      if ((exists $Args::args{'spl_conf'}) && ($Args::args{'spl_conf'} == 1));

    # If we have a wildcard then it is a split image, so we verify it first and
    # then make a string of the images so we can test it.
    if ($img_path =~ /[\*\?]/) {
        if ($spl_conf == 0) {
            return img_add_split_conf();
        }
        else {
            $img_tmp = "";
            foreach my $i (glob($img_path)) {
                if ($i =~ /^($::REG_IMG_PATH)$/) {
                    $img_tmp .= "\"$1\" ";
                }
            }
            $img_path = $img_tmp;
        }
    }
    else {
        unless ((-f $img_path)
            || (-d $img_path)
            || (-l $img_path)
            || (-b $img_path)
            || (-c $img_path))
        {
            Print::print_err("Image file not found ($img_path)");
        }
        $img_path = "\"$img_path\"";
    }

    # Get the image type
    local *OUT;
    Exec::exec_pipe(*OUT, "'$::TSKDIR/img_stat' -t $img_path");
    my $itype = Exec::read_pipe_line(*OUT);
    if (defined $itype) {
        chomp $itype;
        $itype = $1 if ($itype =~ /^(\w+)$/);
    }
    else {
        print
"The image format type could not be determined for this image file<br>\n";
        return;
    }
    close(OUT);

    # The plan here is to collect the needed info and then we print it

    my $conflict = 0;
    my $cnt      = 0;
    $start[0]  = "";
    $end[0]    = "";
    $type[0]   = "";
    $desc[0]   = "";
    $active[0] = "";

    my $vstype = "";

    my $vstype_flag = "";
    my $mmls_out    = "";    # Will contain output of mmls (if disk image)
    if ($img_type eq "disk") {
        my $out;

        if (   (exists $Args::args{'vstype'})
            && ($Args::args{'vstype'} =~ /^(\w+)$/))
        {
            $vstype      = $Args::args{'vstype'};
            $vstype_flag = "-t $vstype";
        }

        # Get the type
        else {

            Exec::exec_pipe(*OUT, "'$::TSKDIR/mmstat' -i $itype $img_path");

            $vstype = Exec::read_pipe_line(*OUT);
            close(OUT);

            chomp $vstype if (defined $vstype);

            if (   (!defined $vstype)
                || ($vstype =~ /^Error determining/)
                || ($vstype eq "")
                || (!exists $Vs::type{$vstype})
                || ($vstype !~ /^\w+$/))
            {
                print
"<table><tr><td colspan=2><font color=\"$::DEL_COLOR[0]\">Warning:</font> Autopsy could not determine the volume system type for the disk image (i.e. the type of partition table).<br>\n"
                  . "Please select the type from the list below or reclassify the image as a volume image instead of as a disk image.</td></tr>\n"
                  . "<tr><td colspan=2>&nbsp;</td></tr>\n"
                  . "<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">\n"
                  . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
                  . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::IMG_ADD_PREP\">\n"
                  . "<input type=\"hidden\" name=\"spl_conf\" value=\"1\">\n"
                  . "<input type=\"hidden\" name=\"img_path\" value=\"$Args::args{'img_path'}\">\n"
                  . "<input type=\"hidden\" name=\"sort\" value=\"$Args::enc_args{'sort'}\">\n"
                  . Args::make_hidden()
                  . "<tr><td>Disk Image <input type=\"radio\" name=\"imgtype\" value=\"disk\" CHECKED></td>\n"
                  . "<td>Volume Image<input type=\"radio\" name=\"imgtype\" value=\"volume\"></td></tr>\n"
                  . "<tr><td>Volume System Type (disk image only): <select name=\"vstype\">\n";

                foreach my $vs (sort keys %Vs::type) {
                    print "<option value=\"$vs\"";
                    print " selected" if ($vs eq 'dos');
                    print ">${vs}</option>\n";
                }

                print "</select></td>"
                  . "<td>&nbsp;</td></tr>"
                  . "<tr><td colspan=2>&nbsp;</td></tr>"
                  . "</table><br><br>\n"
                  . "<input type=\"image\" src=\"pict/menu_b_ok.jpg\" alt=\"Ok\" width=\"176\" height=20 border=0>"
                  . "</form>\n";
                return;
            }
            $vstype = $1 if ($vstype =~ /^(\w+)$/);
            $vstype_flag = "-t $vstype";
        }

        # Run 'mmls' on the image
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/mmls' -a -i $itype -aM $vstype_flag -r $img_path");

        # cycle through results and add each to table with file system type
        my $part_type = "";

        while ($_ = Exec::read_pipe_line(*OUT)) {
            $mmls_out .= "$_";    # Save the line
            last if (/^Error determining partition/);

            if (/^\d+:\s+[\d:]+\s+(\d+)\s+(\d+)\s+\d+\s+(\S.*)$/) {
                $start[$cnt]  = $1;
                $end[$cnt]    = $2;
                $desc[$cnt]   = $3;
                $active[$cnt] = 1;
            }
            elsif ((/^DOS Partition/)
                || (/^BSD Disk/)
                || (/^Sun VTOC/)
                || (/^MAC Partition/)
                || (/^GUID Partition/))
            {
                $part_type = $_;

                #print "<tr><td colspan=7>&nbsp;</td></tr>\n";
                #print "<tr><td colspan=7>$_</td></tr>\n";
                next;
            }
            elsif (/^Sector:/) {

                #print "<tr><td colspan=7>$_</td></tr>\n";
                next;
            }
            else {
                next;
            }

            # Skip the BSD partition for the full disk
            next
              if ( ($part_type =~ /^BSD Disk/)
                && ($start[$cnt] == 0)
                && ($desc[$cnt] =~ /^Unused/));

            # Skip if this is an extended DOS partition
            next
              if ( ($part_type =~ /^DOS Partition/)
                && ($desc[$cnt] =~ /Extended \(/));

            # Get rid of the leading 0s
            $start[$cnt] = $1
              if ($start[$cnt] =~ /^0+([1-9]\d*)$/);
            $end[$cnt] = $1
              if ($end[$cnt] =~ /^0+([1-9]\d*)$/);

            # Do we already have this partition?
            my $i;
            for ($i = 0; $i < $cnt; $i++) {
                next if ($active[$i] == 0);

                if ($start[$i] == $start[$cnt]) {
                    $conflict = 1;

                    if ($end[$i] == $end[$cnt]) {
                        last;
                    }

                    #The previous was the BSD partition - skip it */
                    if (   ($desc[$i] =~ /^FreeBSD \(0xA5\)/)
                        || ($desc[$i] =~ /^OpenBSD \(0xA6\)/)
                        || ($desc[$i] =~ /^NetBSD \(0xA9\)/))
                    {
                        $active[$i] = 0;

                        # if the current one is the BSD partition for
                        # the full partition/disk then skip it
                        if ($desc[$cnt] =~ /^Unused /) {
                            $active[$cnt] = 0;
                        }
                    }
                }

                # Do we start inside of another?
                if (($start[$i] > $start[$cnt]) && ($end[$i] < $start[$cnt])) {
                    $conflict = 1;
                }

                # Do we end inside of another?
                elsif (($start[$i] < $end[$cnt]) && ($end[$i] > $end[$cnt])) {
                    $conflict = 1;
                }
            }
            if (($end[$i] == $end[$cnt]) && ($i != $cnt)) {
                next;
            }

            local *OUT2;
            my $out2;

            # Run 'fstat -t' on the image
            Exec::exec_pipe(*OUT2,
                "'$::TSKDIR/fsstat' -o $start[$cnt] -i $itype -t $img_path");

            $type[$cnt] = Exec::read_pipe_line(*OUT2);
            close(OUT2);

            if (   (!exists $type[$cnt])
                || (!defined $type[$cnt])
                || ($type[$cnt] =~ /^Cannot determine/)
                || ($type[$cnt] eq ""))
            {
                $type[$cnt] = "Unknown";
            }
            chomp $type[$cnt];

            $cnt++;
        }
        close(OUT);

        if ($conflict == 1) {
            print
"<tr><td colspan=2><font color=\"$::DEL_COLOR[0]\">Warning:</font> Conflicts in the partitions were detected.<br>The full <tt>mmls</tt> output is given at the bottom of the page</td></tr>\n"
              . "<tr><td colspan=2>&nbsp;</td></tr>\n";
        }
    }

    # If a volume, then run fsstat on it
    elsif ($img_type eq "volume") {

        # Run 'fstat -t' on the image
        Exec::exec_pipe(*OUT, "'$::TSKDIR/fsstat' -t -i $itype $img_path");

        $type[0] = Exec::read_pipe_line(*OUT);
        close(OUT);

        if (   (!defined $type[0])
            || ($type[0] =~ /^Cannot determine/)
            || ($type[0] eq ""))
        {
            $type[0] = "Unknown";
            print
"<font color=\"$::DEL_COLOR[0]\">Warning:</font> The file system type of the volume image file could not be determined.<br>\n"
              . "If this is a disk image file, return to the previous page and change the type.<br><br>\n";
        }
        chomp $type[0];
        $start[0]  = 0;
        $end[0]    = 0;
        $active[0] = 1;
        $desc[0]   = $type[0];
        $cnt++;
        close(OUT);
    }
    else {
        Print::print_err("Unknown image type: $img_type");
    }

    my $sname = $img_path;
    $sname = "$::IMGDIR/" . "$1" if ($sname =~ /\/($::REG_FILE)\"$/);

# Now that we have the information about the partitions and disks, print the fields
    print <<EOF1;
		
<form action=\"$::PROGNAME\" method=\"get\" target=\"_top\">
<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">
<input type=\"hidden\" name=\"view\" value=\"$Caseman::IMG_ADD_DOIT\">
<input type=\"hidden\" name=\"img_path\" value=\"$Args::args{'img_path'}\">
<input type=\"hidden\" name=\"num_img\" value=\"$cnt\">
<input type=\"hidden\" name=\"sort\" value=\"$Args::enc_args{'sort'}\">


<center>
<h3>Image File Details</h3>
<table width=\"600\" cellpadding=\"2\" cellspacing=\"0\" background=\"$::YEL_PIX\" border=0>
<tr>
  <td align=left colspan=4>
	<b>Local Name: </b> $sname
  </td>
</tr>

EOF1

    # We do not currently offer integrity options for non-raw files
    if (($itype eq "raw") || ($itype eq "split")) {

        print <<EOF1b;
<tr>
  <td align=left colspan=4>
	<b>Data Integrity: </b> An MD5 hash can be used to verify the 
	integrity of the image.  (With split images, this hash is for the full image file)
  </td>
</tr>

<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=3>
    <input type=\"radio\" name=\"do_md5\" value=\"$MD5_NOTHING\" CHECKED>
	<u>Ignore</u> the hash value for this image.
  </td>
</tr>

<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=3>
    <input type=\"radio\" name=\"do_md5\" value=\"$MD5_CALC\">
	<u>Calculate</u> the hash value for this image.
  </td>
</tr>

<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=3>
    <input type=\"radio\" name=\"do_md5\" value=\"$MD5_ADD\">
	<u>Add</u> the following MD5 hash value for this image:
  </td>
</tr>

<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=3>&nbsp;&nbsp;&nbsp;&nbsp;
	<input type=\"text\" name=\"md5\" size=36 maxlength=32>
  </td>
</tr>

<tr>
  <td>&nbsp;&nbsp;</td>
  <td align=left colspan=3>&nbsp;&nbsp;&nbsp;&nbsp;
	<input type=\"checkbox\" name=\"ver_md5\" value=\"1\">
	&nbsp;Verify hash after importing?
  </td>
</tr>
EOF1b
    }
    else {
        print
          "<input type=\"hidden\" name=\"do_md5\" value=\"$MD5_NOTHING\">\n";
    }

    print <<EOF1c;
</table>

<h3>File System Details</h3>
<table width=\"600\" cellpadding=\"2\" cellspacing=\"0\" background=\"$::YEL_PIX\" border=0>

<tr>
  <td colspan=2 align=left>Analysis of the image file shows the following partitions:</td>
</tr>
<tr>
  <td colspan=2>&nbsp;</td>
</tr>

EOF1c

    print Args::make_hidden();

    print "<input type=\"hidden\" name=\"vstype\" value=\"$vstype\">\n"
      if ($vstype ne "");

    my $idx     = 1;
    my $ms_cnt  = 0;
    my @ms_name = ("C:", "D:", "E:", "F:", "G:", "H:", "I:", "J:");
    for (my $i = 0; $i < $cnt; $i++) {
        next if ($active[$i] == 0);
        print
"<tr><td colspan=2><u>Partition $idx</u> (Type: $desc[$i])</td><tr>\n";

        if ($cnt > 1) {
            print "<tr><td colspan=2>&nbsp;&nbsp;Add to case? "
              . "<input type=\"checkbox\" name=\"yes-${idx}\" value=1 CHECKED></td></tr>\n";
        }
        else {
            print "<input type=\"hidden\" name=\"yes-${idx}\" value=1>\n";
        }

        unless (($start[$i] == 0) && ($end[$i] == 0)) {
            print "<tr><td colspan=2>&nbsp;&nbsp;Sector Range: "
              . "$start[$i] to $end[$i]"
              . "</td></tr>\n";
        }

        print
          "<input type=\"hidden\" name=\"start-${idx}\" value=\"$start[$i]\">"
          . "<input type=\"hidden\" name=\"end-${idx}\" value=\"$end[$i]\">\n"
          . "<tr><td>&nbsp;&nbsp;Mount Point: <input type=\"text\" name=\"mnt-${idx}\" size=\"6\"";
        if (($type[$i] =~ /^ntfs/) || ($type[$i] =~ /^fat/)) {
            print " value=\"$ms_name[$ms_cnt]\""
              if ($ms_cnt < 8);
            $ms_cnt++;
        }
        elsif (($type[$i] =~ /^raw/)
            || ($type[$i] =~ /^swap/))
        {
            print " value=\"N/A\"";
        }
        else {
            print " value=\"/$idx/\"";
        }
        print "></td>\n"
          . "<td>File System Type: <select name=\"ftype-${idx}\">\n";

        foreach my $fs (@Fs::types) {
            print "<option value=\"$fs\"";
            print " selected" if ($fs eq $type[$i]);
            print ">${fs}</option>\n";
        }

        # The following do not have 'metas' but should be in the list
        print "<option value=\"\">======</option>\n";
        if ($type[$i] eq "Unknown") {
            print "<option value=\"raw\" selected>raw</option>\n";
        }
        else {
            print "<option value=\"raw\">raw</option>\n";
        }

        print "<option value=\"swap\">swap</option>\n"
          . "</select></td></tr>\n"
          . "<tr><td colspan=2>&nbsp;</td></tr>\n";

        $idx++;
    }

    print "</table>\n";

    print <<EOF2;
<br><br>
<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\">
<tr>
  <td align=center>
    <input type=\"image\" src=\"pict/menu_b_add.jpg\" 
      alt=\"Add\" width=\"176\" height=20 border=0>
  </td>
</form>
  <td align=center>
    <form action=\"$::PROGNAME\" method=\"get\">
EOF2
    print Args::make_hidden();
    print <<EOF3;
    <input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">
    <input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_OPEN\">
    <input type=\"image\" src=\"pict/menu_b_cancel.jpg\" 
    alt=\"Cancel\" width=\"167\" height=20 border=0>
    </form>
  </td>
  <td align=center><a href=\"$::HELP_URL\" 
    target=\"_blank\">
    <img src=\"pict/menu_b_help.jpg\" alt=\"Help\" 
    width=\"167\" height=20 border=0></a>
  </td>
</tr>
</table>
EOF3

    if ($img_type eq "disk") {
        print
"</center><p>For your reference, the <tt>mmls</tt> output was the following:<br><pre>$mmls_out</pre>\n";
    }

    Print::print_html_footer();

    return 0;
}

# Add the image to the configuration by adding it to the host config file
# and the md5.txt file if that data was provided
sub img_add_doit {

    Args::check_num_img();
    Args::check_img_path_wild();
    Args::check_sort();
    Args::check_do_md5();

    my $num_img     = Args::get_num_img();
    my $img_path    = Args::get_img_path_wild();
    my $import_type = Args::get_sort();

    Print::print_html_header("Add a new image to an Autopsy Case");

    my $err     = 0;
    my $add_num = 0;
    $start[0] = 0;
    $end[0]   = 0;
    $ftype[0] = "";
    $mnt[0]   = "";

 # We need a string with all images in it for the hashes and file system testing
    my $img_path_full;
    if ($img_path =~ /[\*\?]/) {
        $img_path_full = "";
        foreach my $i (glob($img_path)) {
            if ($i =~ /^($::REG_IMG_PATH)$/) {
                $img_path_full .= "\"$1\" ";
            }
        }
    }
    else {
        $img_path_full = "\"$img_path\"";
    }

    # Get the image type
    local *OUT;
    Exec::exec_pipe(*OUT, "'$::TSKDIR/img_stat' -t $img_path_full");
    my $itype = Exec::read_pipe_line(*OUT);
    if (defined $itype) {
        chomp $itype;
        $itype = $1 if ($itype =~ /^(\w+)$/);
    }
    else {
        print
"The image format type could not be determined for this image file<br>\n";
        return;
    }
    close(OUT);

    # Check the hash of the image if that is the plan
    my $do_md5  = Args::get_do_md5();
    my $act_md5 = "";
    unless ($do_md5 == $MD5_NOTHING) {

        # Do we need to calculate an MD5?
        if (
            ($do_md5 == $MD5_CALC)
            || (   ($do_md5 == $MD5_ADD)
                && (exists $Args::args{'ver_md5'})
                && ($Args::args{'ver_md5'} == 1))
          )
        {

            print "<p>Calculating MD5 (this could take a while)<br>\n";
            $act_md5 = Hash::calc_md5_split($img_path_full);
            unless ($act_md5 =~ /^$::REG_MD5$/o) {
                print "Error calculating MD5: $act_md5<br>\n";
                return 1;
            }
            print "Current MD5: <tt>$act_md5</tt><br>\n";
        }

        # And md5 value was given so we can add it to the md5.txt file
        if (($do_md5 == $MD5_ADD) && (exists $Args::args{'md5'})) {

            my $md5 = $Args::args{'md5'};
            unless ($md5 =~ /^$::REG_MD5$/o) {
                if ($md5 eq "") {
                    print "MD5 value missing<br>\n";
                }
                else {
                    print "Invalid MD5 value (32 numbers or letters a-f)<br>\n";
                }
                print "<p><a href=\"$::PROGNAME?"
                  . "mod=$::MOD_CASEMAN&view=$Caseman::IMG_ADD&"
                  . "$Args::baseargs\">"
                  . "<img src=\"pict/menu_b_back.jpg\" border=\"0\" "
                  . "width=\"167\" height=20 alt=\"Back\"></a>\n";
                return 1;
            }
            $md5 =~ tr/[a-f]/[A-F]/;

            # They also want us to validate the MD5
            if (   (exists $Args::args{'ver_md5'})
                && ($Args::args{'ver_md5'} == 1))
            {

                if ($act_md5 eq $md5) {
                    print "Integrity Check Passed<br>\n";
                    Print::log_host_info("Integrity check passed on new image");
                }
                else {
                    print "<font color=\"$::DEL_COLOR[0]\">"
                      . "Integrity Check Failed<br></font><br>\n"
                      . "Provided: <tt>$md5</tt><br>\n"
                      . "Image not added to case<br>\n";

                    Print::log_host_info("Integrity check failed on new image");
                    return 1;
                }
            }

            # set the act_md5 value to what was given and verified
            $act_md5 = $md5;
        }

        # We will add the MD5 to the config file after we get its ID
    }

    # Proces the image arguments to make sure they are all there and test the
    # file system type
    print "Testing partitions<br>\n";
    for (my $i = 0; $i <= $num_img; $i++) {

        next
          unless ((exists $Args::args{"yes-" . $i})
            && ($Args::args{"yes-" . $i} == 1));

        if (   (exists $Args::args{"start-" . $i})
            && ($Args::args{"start-" . $i} =~ /^(\d+)$/))
        {
            $start[$add_num] = $1;
        }
        else {
            print "Missing starting address for partition $i<br>\n";
            $err = 1;
            last;
        }

        if (   (exists $Args::args{"end-" . $i})
            && ($Args::args{"end-" . $i} =~ /^(\d+)$/))
        {
            $end[$add_num] = $1;
        }
        else {
            print "Missing ending address for partition $i<br>\n";
            $err = 1;
            last;
        }

        if (   (exists $Args::args{"mnt-" . $i})
            && ($Args::args{"mnt-" . $i} =~ /^($::REG_MNT)$/))
        {
            $mnt[$add_num] = $1;
        }
        else {
            print "Missing mount point for partition $i<br>\n";
            $err = 1;
            last;
        }

        if (   (exists $Args::args{"ftype-" . $i})
            && ($Args::args{"ftype-" . $i} =~ /^($::REG_FTYPE)$/))
        {
            $ftype[$add_num] = $1;
        }
        else {
            print "Missing file system type for partition $i<br>\n";
            $err = 1;
            last;
        }

        # Test the File System
        if (($ftype[$add_num] ne 'swap') && ($ftype[$add_num] ne 'raw')) {

            local *OUT;
            my $out;

            # Run 'fsstat' and see if there is any output - else there was
            # an error and the data went to STDERR
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/fsstat' -o $start[$add_num] -i $itype -f $ftype[$add_num] $img_path_full"
            );
            unless (read(OUT, $out, 1)) {
                print
"<p>Partition $i is not a <tt>$ftype[$add_num]</tt> file system<br>\n";
                $err = 1;
                last;
            }
            close(OUT);
        }
        $add_num++;
    }

    # Go back if we got an error
    if ($err == 1) {
        print "Use the browser's back button to fix the data<br>\n";
        return 1;
    }

    ##################################################
    # Copy the images and add them to the config file

    if ($import_type == $IMG_ADD_SYM) {
        Print::print_err("ERROR: /bin/ln missing")
          unless (-x '/bin/ln');

        print "Linking image(s) into evidence locker<br>\n";
    }
    elsif ($import_type == $IMG_ADD_COPY) {
        Print::print_err("ERROR: /bin/cp missing")
          unless (-x '/bin/cp');

        print
"Copying image(s) into evidence locker (this could take a little while)<br>\n";
    }
    elsif ($import_type == $IMG_ADD_MOVE) {
        Print::print_err("ERROR: /bin/mv missing")
          unless (-x '/bin/mv');

        print "Moving image(s) into evidence locker<br>\n";
    }
    else {
        Print::print_err("Invalid Import Type: $import_type\n");
    }

    my $imgid = "";
    foreach my $i (glob($img_path)) {

        # remove the tainting
        $i = $1 if ($i =~ /^($::REG_IMG_PATH)$/);

        # Deterine the local (target) name
        my $img = "";
        if ($i =~ /\/($::REG_FILE)$/) {
            $img = "$::IMGDIR/$1";
        }
        else {
            Print::print_err("Error Parsing Image Path ($i)\n");
        }

        # Get the full path of the destination
        my $img_dst = "$::host_dir" . "$img";
        if ((-e "$img_dst") || (-l "$img_dst")) {
            Print::print_err(
"An image by the same name already exists in the Host directory ($img)\n"
                  . "Use the browser's back button to fix the name or delete the existing file."
            );
        }

        my $orig_size = (stat("$i"))[7];

        # Copy, Move, or link it
        if ($import_type == $IMG_ADD_SYM) {

            Print::log_host_info(
"Sym Linking image $img_path into $Args::args{'case'}:$Args::args{'host'}"
            );

            Exec::exec_sys("/bin/ln -s '$i' '$img_dst'");
        }
        elsif ($import_type == $IMG_ADD_COPY) {
            Print::log_host_info(
"Copying image $img_path into $Args::args{'case'}:$Args::args{'host'}"
            );

            Exec::exec_sys("/bin/cp '$i' '$img_dst'");
        }
        elsif ($import_type == $IMG_ADD_MOVE) {
            Print::log_host_info(
"Moving image $img_path into $Args::args{'case'}:$Args::args{'host'}"
            );

            Exec::exec_sys("/bin/mv '$i' '$img_dst'");
        }

        my $new_size = (stat("$img_dst"))[7];

        if ($new_size != $orig_size) {
            Print::print_err(
"Original image size ($orig_size) is not the same as the destination size ($new_size)"
            );
        }

        # Add the disk and partition images to the config file
        $imgid = Caseman::add_img_host_config("image", "$itype  $img", $imgid);
    }
    print "Image file added with ID <tt>$imgid</tt><br>\n";

    # AFM files have raw files that we also need to copy
    # This approach is not the best, since it may copy more than
    # is needed
    if ($itype eq "afm") {
        my $afm_base_path = "";

        if ($img_path =~ /^(.*?)\.afm/i) {
            $afm_base_path = $1;
            $afm_base_path .= ".[0-9][0-9][0-9]";
        }
        else {
            Print::print_err(
                "Error parsing out base name of AFM file $img_path");
        }

        print "BASE: $afm_base_path<br>\n";

        my $copied = 0;

        foreach my $i (glob($afm_base_path)) {
            $copied++;

            # remove the tainting
            $i = $1 if ($i =~ /^($::REG_IMG_PATH)$/);

            # Deterine the local (target) name
            my $img = "";
            if ($i =~ /\/($::REG_FILE)$/) {
                $img = "$::IMGDIR/$1";
            }
            else {
                Print::print_err("Error Parsing Image Path ($i)\n");
            }

            # Get the full path of the destination
            my $img_dst = "$::host_dir" . "$img";
            if ((-e "$img_dst") || (-l "$img_dst")) {
                Print::print_err(
"An image by the same name already exists in the Host directory ($img) (AFM import)\n"
                      . "Use the browser's back button to fix the name or delete the existing file."
                );
            }

            my $orig_size = (stat("$i"))[7];

            # Copy, Move, or link it
            if ($import_type == $IMG_ADD_SYM) {

                Print::log_host_info(
"Sym Linking image $img_path into $Args::args{'case'}:$Args::args{'host'}"
                );

                Exec::exec_sys("/bin/ln -s '$i' '$img_dst'");
            }
            elsif ($import_type == $IMG_ADD_COPY) {
                Print::log_host_info(
"Copying image $img_path into $Args::args{'case'}:$Args::args{'host'}"
                );

                Exec::exec_sys("/bin/cp '$i' '$img_dst'");
            }
            elsif ($import_type == $IMG_ADD_MOVE) {
                Print::log_host_info(
"Moving image $img_path into $Args::args{'case'}:$Args::args{'host'}"
                );

                Exec::exec_sys("/bin/mv '$i' '$img_dst'");
            }

            my $new_size = (stat("$img_dst"))[7];

            if ($new_size != $orig_size) {
                Print::print_err(
"Original image size ($orig_size) is not the same as the destination size ($new_size) after AFM import"
                );
            }
        }
        if ($copied == 0) {
            Print::print_err(
"No AFM raw files were found with the same base name and a numeric extension"
            );
        }
        else {
            print "$copied AFM raw files imported<br>\n";
        }
    }

    Caseman::update_md5("$imgid", "$act_md5")
      unless (($do_md5 == $MD5_NOTHING) || ($imgid eq ""));

    # Add a disk entry if the image is of a disk
    unless (($add_num == 1) && ($end[0] == 0) && ($start[0] == 0)) {
        unless ((exists $Args::args{'vstype'})
            && ($Args::args{'vstype'} =~ /^(\w+)$/))
        {
            Print::print_err("Missing Volume System Type");
        }
        my $vstype = $Args::args{'vstype'};

        my $diskid = Caseman::add_vol_host_config("disk", "$imgid    $vstype");
        print "<p>Disk image (type $vstype) added with ID <tt>$diskid</tt>\n";
    }

    # Add the file system / partition entries
    for (my $i = 0; $i < $add_num; $i++) {
        my $volid =
          Caseman::add_vol_host_config("part",
            "$imgid	$start[$i]  $end[$i]    $ftype[$i]  $mnt[$i]");
        print
"<p>Volume image ($start[$i] to $end[$i] - $ftype[$i] - $mnt[$i]) added with ID <tt>$volid</tt>\n";
    }

    print <<EOF;
<p>
<center>
<table width=600>
<tr>
  <td width=300 align=center>
    <a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&view=$Caseman::VOL_OPEN&${Args::baseargs_novol}\">
    <img src=\"pict/menu_b_ok.jpg\" alt=\"Ok\" width=\"167\" height=20 border=\"0\">
    </a>
  </td>
  <td width=300 align=center>
    <a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&view=$Caseman::IMG_ADD&${Args::baseargs_novol}\">
    <img src=\"pict/menu_b_inew.jpg\" alt=\"Ok\" width=\"167\" height=20 border=\"0\">
    </a>
  </td>
</tr>
</table>
</center>

EOF
    Print::print_html_footer();

    return 0;
}

# Display details of image based on config values
# provides links to remove the config of the image and to get the file
# system details

sub vol_details {
    Print::print_html_header("Details of $Args::args{'vol'}");

    Args::get_unitsize();

    my $vol = Args::get_vol('vol');

    my $mnt   = $Caseman::vol2mnt{$vol};
    my $ftype = $Caseman::vol2ftype{$vol};

    print "<center>"
      . "<img src=\"pict/menu_h_idet.jpg\" alt=\"Image Details\">"
      . "<br><br><br>\n"
      . "<table width=\"600\" cellspacing=\"0\" cellpadding=\"2\" "
      . "background=\"$::YEL_PIX\" border=0>\n"
      . "  <tr><td colspan=\"2\">&nbsp;</td></tr>\n"
      .

      # Name
      "  <tr><td align=\"right\" width=\"300\"><b>Name:</b></td>"
      . "<td align=\"left\"><tt>$Caseman::vol2sname{$vol}</tt></td></tr>\n"
      . "<tr><td align=\"right\" width=\"300\"><b>Volume Id:</b></td>"
      . "<td align=\"left\"><tt>$vol</tt></td></tr>\n"
      . "<tr><td align=\"right\" width=\"300\"><b>Parent Volume Id:</b></td>"
      . "<td align=\"left\"><tt>$Caseman::vol2par{$vol}</tt></td></tr>\n"
      . "<tr><td align=\"right\" width=\"300\"><b>Image File Format:</b></td>"
      . "<td align=\"left\"><tt>$Caseman::vol2itype{$vol}</tt></td></tr>\n"

      # Mount
      . "  <tr><td align=\"right\"><b>Mounting Point:</b></td>"
      . "<td align=\"left\"><tt>$mnt</tt></td></tr>\n"
      .

      # Type
      "  <tr><td align=\"right\"><b>File System Type:</b></td>"
      . "<td align=\"left\"><tt>$ftype</tt></td></tr>\n";

    # Host Directory
    print "  <tr><td colspan=\"2\">&nbsp;</td></tr>\n"

      # Strings File
      . "  <tr><td colspan=2 align=\"center\"><b>External Files</b></td></tr>\n"
      . "  <tr><td align=\"right\"><b>ASCII Strings:</b></td>"
      . "<td align=\"left\"><tt>"
      . (
        (exists $Caseman::vol2str{$vol})
        ? $Caseman::vol2sname{$Caseman::vol2str{$vol}}
        : "&nbsp;"
      )
      . "</tt></td></tr>\n"
      .

      # Unicode Strings File
      "  <tr><td align=\"right\"><b>Unicode Strings:</b></td>"
      . "<td align=\"left\"><tt>"
      . (
        (exists $Caseman::vol2uni{$vol})
        ? $Caseman::vol2sname{$Caseman::vol2uni{$vol}}
        : "&nbsp;"
      )
      . "</tt></td></tr>\n";

    if (($ftype ne "raw") && ($ftype ne "swap")) {

        # blkls file
        print
"  <tr><td align=\"right\"><b>Unallocated $Fs::addr_unit{$ftype}s:</b></td>"
          . "<td align=\"left\"><tt>"
          . (
            (exists $Caseman::vol2blkls{$vol})
            ? $Caseman::vol2sname{$Caseman::vol2blkls{$vol}}
            : "&nbsp;"
          )
          . "</tt></td></tr>\n";

        # Strings of blkls
        print
          "  <tr><td align=\"right\"><b>ASCII Strings of Unallocated:</b></td>"
          . "<td align=\"left\"><tt>"
          . (
            (
                     (exists $Caseman::vol2blkls{$vol})
                  && (exists $Caseman::vol2str{$Caseman::vol2blkls{$vol}})
            )
            ? $Caseman::vol2sname{$Caseman::vol2str{$Caseman::vol2blkls{$vol}}}
            : "&nbsp;"
          )
          . "</tt></td></tr>\n";

        # Unicodde Strings of blkls
        print
"  <tr><td align=\"right\"><b>Unicode Strings of Unallocated:</b></td>"
          . "<td align=\"left\"><tt>"
          . (
            (
                     (exists $Caseman::vol2blkls{$vol})
                  && (exists $Caseman::vol2uni{$Caseman::vol2blkls{$vol}})
            )
            ? $Caseman::vol2sname{$Caseman::vol2uni{$Caseman::vol2blkls{$vol}}}
            : "&nbsp;"
          )
          . "</tt></td></tr>\n";
    }

    print "  <tr><td colspan=\"2\">&nbsp;</td></tr>\n"
      . "</table>\n<a name=\"extract\"\n";

    # Section for Strings file and 'blkls' file

    if (
           (!(exists $Caseman::vol2str{$vol}))
        || (!(exists $Caseman::vol2uni{$vol}))
        || (!(exists $Caseman::vol2blkls{$vol}))
        || (
            (exists $Caseman::vol2blkls{$vol})
            && (   (!(exists $Caseman::vol2str{$Caseman::vol2blkls{$vol}}))
                || (!(exists $Caseman::vol2uni{$Caseman::vol2blkls{$vol}})))
        )
      )
    {
        print "<hr><table width=600>\n<tr>";
    }

    # Strings File
    if (   (!(exists $Caseman::vol2str{$vol}))
        || (!(exists $Caseman::vol2uni{$vol})))
    {

        print
"<td align=\"center\" width=280><h3>Extract Strings of<br>Entire Volume</h3>"
          . "Extracting the ASCII and Unicode strings from a file system will "
          . "make keyword searching faster.<br><br>\n"
          . "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "Generate MD5? "
          . "<input type=\"checkbox\" name=\"md5\" value=\"1\" CHECKED><br><br>"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_MAKESTR\">\n"
          . "<input type=\"hidden\" name=\"vol\" value=\"$Args::args{'vol'}\">\n"
          . Args::make_hidden();

        if (!(exists $Caseman::vol2str{$vol})) {
            print
"ASCII: <input type=\"checkbox\" name=\"str\" value=\"1\" CHECKED>  \n";
        }
        if (!(exists $Caseman::vol2uni{$vol})) {
            print
"  Unicode: <input type=\"checkbox\" name=\"uni\" value=\"1\" CHECKED>\n";
        }

        print "<br><br><input type=\"image\" src=\"pict/srch_b_str.jpg\" "
          . "alt=\"Extract Strings\" border=\"0\">\n</form></td>\n"
          . "<td width=40>&nbsp;</td>\n";
    }

    if (($ftype eq 'blkls') || ($ftype eq 'swap') || ($ftype eq 'raw')) {

        # Place holder for types that have no notion of unallocated
    }

    # Unallocated Space File
    elsif (!(exists $Caseman::vol2blkls{$vol})) {

        print
"<td align=\"center\" width=280><h3>Extract Unallocated $Fs::addr_unit{$ftype}s</h3>"
          . "Extracting the unallocated data in a file system allows "
          . "more focused keyword searches and data recovery.<br><br>\n"
          . "(Note: This Does Not Include Slack Space)<br>\n"
          . "<form action=\"$::PROGNAME\" method=\"get\">\n";

        print "Generate MD5? "
          . "<input type=\"checkbox\" name=\"md5\" value=\"1\" CHECKED><br><br>"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_MAKEBLKLS\">\n"
          .

          "<input type=\"hidden\" name=\"vol\" value=\"$Args::args{'vol'}\">\n"
          . Args::make_hidden()
          . "<input type=\"image\" src=\"pict/srch_b_un.jpg\" "
          . "alt=\"Extract Unallocated Data\" border=\"0\">\n<br></form>\n";
    }

    # strings of 'blkls'
    elsif ((!(exists $Caseman::vol2str{$Caseman::vol2blkls{$vol}}))
        || (!(exists $Caseman::vol2uni{$Caseman::vol2blkls{$vol}})))
    {

        print
"<td align=\"center\" width=280><h3>Extract Strings of<br>Unallocated $Fs::addr_unit{$ftype}s</h3>"
          . "Extracting the ASCII strings from the unallocated data will make "
          . "keyword searching faster.<br><br>\n"
          . "<form action=\"$::PROGNAME\" method=\"get\">\n"
          . "Generate MD5? "
          . "<input type=\"checkbox\" name=\"md5\" value=\"1\" CHECKED><br><br>"
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
          . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_MAKESTR\">\n"
          .

"<input type=\"hidden\" name=\"vol\" value=\"$Caseman::vol2blkls{$vol}\">\n"
          . "<input type=\"hidden\" name=\"fname_mode\" value=\"$FNAME_MODE_INIT\">\n"
          . Args::make_hidden();

        if (!(exists $Caseman::vol2str{$Caseman::vol2blkls{$vol}})) {
            print
"ASCII: <input type=\"checkbox\" name=\"str\" value=\"1\" CHECKED>  \n";
        }
        if (!(exists $Caseman::vol2uni{$Caseman::vol2blkls{$vol}})) {
            print
"  Unicode: <input type=\"checkbox\" name=\"uni\" value=\"1\" CHECKED>\n";
        }
        print "<br><br><input type=\"image\" src=\"pict/srch_b_str.jpg\" "
          . "alt=\"Extract Strings\" border=\"0\">\n</form></td>\n";
    }
    if (
           (!(exists $Caseman::vol2str{$vol}))
        || (!(exists $Caseman::vol2uni{$vol}))
        || (!(exists $Caseman::vol2blkls{$vol}))
        || (
            (exists $Caseman::vol2blkls{$vol})
            && (   (!(exists $Caseman::vol2str{$Caseman::vol2blkls{$vol}}))
                || (!(exists $Caseman::vol2uni{$Caseman::vol2blkls{$vol}})))
        )
      )
    {
        print "</tr></table><hr>\n";
    }

    print "<p>"
      . "<table width=\"400\" cellspacing=\"0\" cellpadding=\"2\">\n"
      .

      # Ok
      "<tr><td align=center width=200>"
      . "<form action=\"$::PROGNAME\" method=\"get\">\n"
      . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n"
      . "<input type=\"hidden\" name=\"view\" value=\"$Caseman::VOL_OPEN\">\n"
      . Args::make_hidden()
      . "<input type=\"image\" src=\"pict/menu_b_close.jpg\" "
      . "alt=\"Close\" width=\"167\" height=20 border=0></form></td>\n";

    print "<td align=center width=200>";
    if (($ftype ne "raw") && ($ftype ne "swap")) {

        # File System Details
        print "<form action=\"$::PROGNAME\" method=\"get\" target=\"_blank\">\n"
          . Args::make_hidden()
          . "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_FRAME\">\n"
          . "<input type=\"hidden\" name=\"submod\" value=\"$::MOD_FS\">\n"
          . "<input type=\"hidden\" name=\"vol\" value=\"$vol\">\n"
          . "<input type=\"image\" src=\"pict/menu_b_fs.jpg\" "
          . "width=167 height=20 "
          . "alt=\"File System\" border=0></form></td>\n";
    }
    else {
        print "&nbsp;</td>\n";
    }

# Remove Image
# THis was removed 12/03 because it causes problems because the image still
# exists and config entries and ... it becomes a mess
#	print
#	  "<td align=center width=200>".
#	  "<form action=\"$::PROGNAME\" method=\"get\">\n".
#          "<input type=\"hidden\" name=\"mod\" value=\"$::MOD_CASEMAN\">\n".
#          "<input type=\"hidden\" name=\"view\" value=\"$Caseman::IMG_DEL\">\n".
#	  Args::make_hidden().
#          "<input type=\"hidden\" name=\"vol\" value=\"$Args::args{'vol'}\">\n".
#          "<input type=\"hidden\" name=\"mnt\" value=\"$Args::args{'mnt'}\">\n".
#	  "<input type=\"image\" src=\"pict/menu_b_rem.jpg\" ".
#	  "width=167 height=20 alt=\"Remove\" border=0></form>".
#          "</td>\n".
#	  "</tr></table>\n";

    Print::print_html_footer();
    return 0;
}

# remove the config files
sub img_del {
    Args::check_vol('vol');

    # Args::check_ftype();
    Print::print_html_header(
        "Removing Configuration Settings for $Args::args{'vol'}");

    Caseman::del_host_config("", $Args::args{'vol'}, "");
    Caseman::update_md5($Args::args{'vol'}, "");

    print "Settings for <tt>$Args::args{'vol'}</tt> removed from "
      . "<tt>$Args::args{'case'}:$Args::args{'host'}</tt>.\n"
      . "<p>NOTE: The actual file still exists in the host directory.\n";

    print "<p><a href=\"$::PROGNAME?mod=$::MOD_CASEMAN&"
      . "view=$Caseman::VOL_OPEN&${Args::baseargs_novol}\">"
      . "<img src=\"pict/but_ok.jpg\" alt=\"Ok\" "
      . "width=\"43\" height=20 border=\"0\"></a>\n";

    Print::print_html_footer();

    return 0;
}

# Make a strings -t d file for the image to decrease the search time
# Can make both ASCII and Unicode strings files
sub vol_makestr {
    Print::print_html_header("Extracting Strings");

    my $ascii = 0;
    my $uni   = 0;

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    if ((exists $Args::args{'str'}) && ($Args::args{'str'} == 1)) {
        if (exists $Caseman::vol2str{$vol}) {
            Print::print_err(
"Image already has an ASCII strings file: $Caseman::vol2sname{$vol}"
            );
        }
        $ascii = 1;
    }

    if ((exists $Args::args{'uni'}) && ($Args::args{'uni'} == 1)) {
        if (exists $Caseman::vol2uni{$vol}) {
            Print::print_err(
"Image already has a Unicode strings file: $Caseman::vol2sname{$vol}"
            );
        }

        $uni = 1;
    }
    if (($uni == 0) && ($ascii == 0)) {
        goto str_egress;
    }

    my $base_name = $Caseman::vol2sname{$vol};

    if ($ascii == 1) {
        my $fname_rel = "$::DATADIR/${base_name}-$ftype.asc";
        my $fname     = "$::host_dir" . "$fname_rel";

        if (-e "$fname") {
            my $i = 1;
            $i++ while (-e "$::host_dir"
                . "$::DATADIR/"
                . "${base_name}-$ftype-$i.asc");

            $fname_rel = "$::DATADIR/${base_name}-$ftype-$i.asc";
            $fname     = "$::host_dir" . "$fname_rel";
        }

        print
"Extracting ASCII strings from <tt>$Caseman::vol2sname{$vol}</tt><br>\n";

        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Saving ASCII strings to $fname_rel");

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

        if ($ftype eq "blkls") {
            Exec::exec_pipe(*OUT,
                "'$::TSKDIR/srch_strings' -a -t d $img > '$fname'");
        }
        elsif ((($ftype eq "raw") || ($ftype eq "swap"))
            && ($Caseman::vol2end{$vol} != 0))
        {
            Exec::exec_pipe(*OUT,
                    "'$::TSKDIR/blkls' -e -f $ftype -i $imgtype $img "
                  . $Caseman::vol2start{$vol} . "-"
                  . $Caseman::vol2end{$vol}
                  . " | '$::TSKDIR/srch_strings' -a -t d > '$fname'");
        }
        else {
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkls' -e -f $ftype -o $offset -i $imgtype $img | '$::TSKDIR/srch_strings' -a -t d > '$fname'"
            );
        }
        alarm(0);
        $SIG{ALRM} = 'DEFAULT';

        print $_ while ($_ = Exec::read_pipe_line(*OUT));
        close(OUT);

        print "<br>\n" if ($hit_cnt != 0);

        # Verify that it worked
        unless (open(STR, "$fname")) {
            print(  "Error opening $fname<br>\n"
                  . "Either an error occurred while generating the file or "
                  . "no ASCII strings exist<br>");
            goto str_uni;
        }

        # append to config
        my $strvol =
          Caseman::add_vol_host_config("strings", "$vol   $fname_rel");
        print "Host configuration file updated<br>";

        $Caseman::vol2ftype{$strvol} = "strings";
        $Caseman::mod2vol{$strvol}   = $vol;
        $Caseman::vol2str{$vol}      = $strvol;
        $Caseman::vol2cat{$strvol}   = "mod";
        $Caseman::vol2itype{$strvol} = "raw";

        $Caseman::vol2par{$strvol}   = $vol;
        $Caseman::vol2path{$strvol}  = "$::host_dir" . "$fname_rel";
        $Caseman::vol2start{$strvol} = 0;
        $Caseman::vol2end{$strvol}   = 0;
        $Caseman::vol2sname{$strvol} = $fname_rel;

        # Calculate MD5
        if ((exists $Args::args{'md5'}) && ($Args::args{'md5'} == 1)) {
            print "Calculating MD5 Value<br><br>\n";
            my $m = Hash::int_create_wrap($strvol);
            print "MD5 Value: <tt>$m</tt><br><br>\n";
        }
    }

  str_uni:

    if ($uni == 1) {

        my $fname_rel = "$::DATADIR/${base_name}-$ftype.uni";
        my $fname     = "$::host_dir" . "$fname_rel";

        if (-e "$fname") {
            my $i = 1;
            $i++ while (-e "$::host_dir"
                . "$::DATADIR/"
                . "${base_name}-$ftype-$i.uni");

            $fname_rel = "$::DATADIR/${base_name}-$ftype-$i.uni";
            $fname     = "$::host_dir" . "$fname_rel";
        }

        print "<hr>\n" if ($ascii == 1);

        print
"Extracting Unicode strings from <tt>$Caseman::vol2sname{$vol}</tt><br>\n";

        Print::log_host_inv(
            "$Caseman::vol2sname{$vol}: Saving Unicode strings to $fname_rel");

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
        if ($ftype eq "blkls") {
            Exec::exec_pipe(*OUT,
                "'$::TSKDIR/srch_strings' -a -t d -e l $img > '$fname'");
        }
        elsif ((($ftype eq "raw") || ($ftype eq "swap"))
            && ($Caseman::vol2end{$vol} != 0))
        {
            Exec::exec_pipe(*OUT,
                    "'$::TSKDIR/blkls' -e -f $ftype -i $imgtype $img "
                  . $Caseman::vol2start{$vol} . "-"
                  . $Caseman::vol2end{$vol}
                  . " | '$::TSKDIR/srch_strings' -a -t d -e l > '$fname'");
        }

        else {
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkls' -e -f $ftype -o $offset -i $imgtype $img | '$::TSKDIR/srch_strings' -a -t d -e l  > '$fname'"
            );
        }

        alarm(0);
        $SIG{ALRM} = 'DEFAULT';

        print $_ while ($_ = Exec::read_pipe_line(*OUT));
        close(OUT);

        print "<br>\n" if ($hit_cnt != 0);

        # Verify that it worked
        unless (open(STR, "$fname")) {
            print "Error opening $fname<br>\n"
              . "Either an error occurred while generating the file or "
              . "no Unicode strings exist";
            goto str_egress;
        }

        # append to config
        my $strvol =
          Caseman::add_vol_host_config("unistrings", "$vol    $fname_rel");
        print "Host configuration file updated<br>";

        $Caseman::vol2ftype{$strvol} = "strings";
        $Caseman::mod2vol{$strvol}   = $vol;
        $Caseman::vol2uni{$vol}      = $strvol;
        $Caseman::vol2cat{$strvol}   = "mod";
        $Caseman::vol2itype{$strvol} = "raw";

        $Caseman::vol2par{$strvol}   = $vol;
        $Caseman::vol2path{$strvol}  = "$::host_dir" . "$fname_rel";
        $Caseman::vol2start{$strvol} = 0;
        $Caseman::vol2end{$strvol}   = 0;
        $Caseman::vol2sname{$strvol} = $fname_rel;

        # Calculate MD5
        if ((exists $Args::args{'md5'}) && ($Args::args{'md5'} == 1)) {
            print "Calculating MD5 Value<br><br>\n";
            $m = Hash::int_create_wrap($strvol);
            print "MD5 Value: <tt>$m</tt><br><br>\n";
        }
    }

  str_egress:

    my $dest_vol = $vol;

    # We need to return with a real image to VOL_DETAILS so check the mod
    $dest_vol = $Caseman::mod2vol{$vol}
      if (exists $Caseman::mod2vol{$vol});

    print "<hr><a href=\"$::PROGNAME?$Args::baseargs_novol&mod=$::MOD_CASEMAN&"
      . "view=$Caseman::VOL_DETAILS&vol=$dest_vol\" target=_top>Image Details</a><p>\n";

    print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_KWSRCH&$Args::baseargs\""
      . " target=\"_top\">Keyword Search</a>\n";

    Print::print_html_footer();

    return 0;
}

sub vol_makeblkls {
    Print::print_html_header("Extracting Unallocated Space");

    my $vol     = Args::get_vol('vol');
    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $base_name = $Caseman::vol2sname{$vol};
    $base_name = $1 if ($base_name =~ /^(.*?)\.dd$/);
    my $fname_rel = "$::DATADIR/${base_name}-$ftype.unalloc";
    my $fname     = "$::host_dir" . "$fname_rel";

    if (-e "$::host_dir" . "$fname_rel") {
        my $i = 1;
        $i++ while (-e "$::host_dir"
            . "$::DATADIR/"
            . "${base_name}-$ftype-$i.unalloc");

        $fname_rel = "$::DATADIR/${base_name}-$ftype-$i.unalloc";
        $fname     = "$::host_dir" . "$fname_rel";
    }

    Print::log_host_inv(
        "$Args::args{'vol'}: Saving unallocated data to $fname_rel");

    print
"Extracting unallocated data from <tt>$Caseman::vol2sname{$vol}</tt><br>\n";

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

    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/blkls' -f $ftype  -o $offset -i $imgtype $img > '$fname'");

    alarm(0);
    $SIG{ALRM} = 'DEFAULT';

    print "$_" while ($_ = Exec::read_pipe_line(*OUT));
    close(OUT);

    print "<br>\n"
      if ($hit_cnt != 0);

    # append to config
    my $blklsvol = Caseman::add_vol_host_config("blkls", "$vol    $fname_rel");
    print "Host configuration file updated<br>";

    $Caseman::vol2ftype{$blklsvol} = "blkls";
    $Caseman::mod2vol{$blklsvol}   = $vol;
    $Caseman::vol2blkls{$vol}      = $blklsrvol;
    $Caseman::vol2cat{$blklsvol}   = "mod";
    $Caseman::vol2itype{$blklsvol} = "raw";

    $Caseman::vol2par{$blklsvol}   = $vol;
    $Caseman::vol2path{$blklsvol}  = "$::host_dir" . "$fname_rel";
    $Caseman::vol2start{$blklsvol} = 0;
    $Caseman::vol2end{$blklsvol}   = 0;
    $Caseman::vol2sname{$blklsvol} = $fname_rel;

    # Calculate MD5
    if ((exists $Args::args{'md5'}) && ($Args::args{'md5'} == 1)) {
        print "Calculating MD5 Value<br>\n";
        my $m = Hash::int_create_wrap($blklsvol);
        print "MD5 Value: <tt>$m</tt><br><br>\n";
    }

    print "<a href=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_CASEMAN&"
      . "view=$Caseman::VOL_DETAILS\" target=_top>Image Details</a><p>\n";

    print
"<a href=\"$::PROGNAME?mod=$::MOD_FRAME&submod=$::MOD_KWSRCH&$Args::baseargs_novol&"
      . "vol=$fname_rel\" target=\"_top\">Keyword Search</a>\n";

    Print::print_html_footer();
    return 0;
}

1;
