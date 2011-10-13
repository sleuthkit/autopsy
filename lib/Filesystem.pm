#
# File system layer functions
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

package Filesystem;

$Filesystem::STATS = 0;

sub main {

    # By default, show the main window
    $Args::args{'view'} = $Args::enc_args{'view'} = $Filesystem::STATS
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    # Check Basic Args
    Args::check_vol('vol');

    # These windows don't need the meta data address
    if ($view == $Filesystem::STATS) {
        return stats();
    }
    else {
        Print::print_check_err("Invalid File System View");
    }
}

sub stats_disk {
    Print::print_html_header("Disk Status");

    my $vol     = Args::get_vol('vol');
    my $img     = $Caseman::vol2path{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $dtype   = $Caseman::vol2dtype{$vol};

    # Run 'mmls' on the image
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/mmls' -o $offset -i $imgtype -t $dtype -r $img");

    # cycle through results and add each to table with file system type
    print "<center><h3>Disk Image Details</h3></center>\n";
    print "<b>PARTITION INFORMATION</b><p>\n";

    while ($_ = Exec::read_pipe_line(*OUT)) {
        print "<tt>$_</tt><br>\n";
    }
    return 0;

}

############ FILE SYSTEM ##################
sub stats {

    my $vol = Args::get_vol('vol');

    return stats_disk() if ($Caseman::vol2cat{$vol} eq "disk");

    Print::print_html_header("File System Status");

    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    Print::log_host_inv(
        "$Caseman::vol2sname{$vol}: Displaying file system details");
    print "<center><h3>General File System Details</h3></center><p>\n";

    my $fat = 0;
    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/fsstat' -f $ftype -o $offset -i $imgtype $img");
    while ($_ = Exec::read_pipe_line(*OUT)) {

        if (/\-\-\-\-\-\-\-\-\-\-/) {

            # Ignore these and print them ahead of the headers
        }

        # need the space to prevent NTFS STD_INFORMATION from triggering it
        elsif (/ INFORMATION/) {
            print "<hr><b>$_</b><p>\n";
        }
        elsif (($ftype =~ /fat/) && ($_ =~ /FAT CONTENTS/)) {
            print "<hr><b>$_</b><p>\n";

            # Set the flag if we reach the FAT
            $fat = 1;
        }

        # Special case for FAT
        # We will be giving hyperlinks in the FAT table dump
        elsif ($fat == 1) {

            # Ignore the divider
            if (/\-\-\-\-\-\-\-\-\-\-/) {
                print "$_<br>";
                next;
            }

            if (/^((\d+)\-\d+\s+\((\d+)\)) \-\> ([\w]+)$/) {
                my $full = $1;
                my $blk  = $2;
                my $len  = $3;
                my $next = $4;

                # Print the tag so that other FAT entries can link to it
                print "<a name=\"$blk\">\n";

                print
"<a href=\"$::PROGNAME?$Args::baseargs&mod=$::MOD_FRAME&submod=$::MOD_DATA&"
                  . "block=$blk&len=$len\" target=\"_top\">$full</a> -> ";

                if ($next eq 'EOF') {
                    print "EOF<br>\n";
                }
                else {
                    print "<a href=\"#$next\">$next</a><br>\n";
                }
            }
            else {
                $fat = 0;
                print "$_<br>";
            }
        }
        else {
            print "$_<br>";
        }
    }
    close(OUT);

    Print::print_html_footer();
    return 0;
}

1;
