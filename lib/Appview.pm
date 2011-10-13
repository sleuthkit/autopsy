#
# View the application layer (HTML, picutures etc.)
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

package Appview;

$Appview::CELL_FRAME = 1;
$Appview::CELL_MENU  = 2;
$Appview::CELL_CONT  = 3;

sub main {

    # By default, show the main frame
    $Args::args{'view'} = $Args::enc_args{'view'} = $Appview::CELL_FRAME
      unless (exists $Args::args{'view'});

    Args::check_view();
    my $view = Args::get_view();

    # Check Basic Args
    Args::check_vol('vol');
    Args::check_meta('meta');
    Args::check_dir();
    Args::check_recmode();

    if ($view == $Appview::CELL_FRAME) {
        return cell_frame();
    }
    elsif ($view == $Appview::CELL_CONT) {
        return cell_content();
    }
    elsif ($view == $Appview::CELL_MENU) {
        return cell_menu();
    }
    else {
        Print::print_check_err("Invalid Application Viewing View");
    }
}

#########################################################################
#
# CELL - Sanitized Environment
#

my $CELL_MODE_SANIT = 1;
my $CELL_MODE_NORM  = 2;

sub cell_frame {
    Print::print_html_header_frameset("Autopsy Cell");
    my $vol = Args::get_vol('vol');
    my $mnt = $Caseman::vol2mnt{$vol};

    my $fname = "$mnt$Args::args{'dir'}";

    print "<frameset rows=\"15%,85%\">\n";

    # if a mode was not given, then choose the Sanitized by default
    $Args::args{'cell_mode'} = $CELL_MODE_SANIT
      unless ((exists $Args::args{'cell_mode'})
        && ($Args::args{'cell_mode'} =~ /^\d$/));

    my $url =
        "&$Args::baseargs&meta=$Args::enc_args{'meta'}"
      . "&dir=$Args::enc_args{'dir'}&"
      . "cell_mode=$Args::args{'cell_mode'}&recmode=$Args::args{'recmode'}";

    print
"<frame src=\"$::PROGNAME?mod=$::MOD_APPVIEW&view=$Appview::CELL_MENU${url}\">\n"
      . "<frame src=\"$::PROGNAME?mod=$::MOD_APPVIEW&view=$Appview::CELL_CONT${url}\">\n"
      . "</frameset>\n";

    Print::print_html_footer_frameset();
    return 0;
}

# Print the menu on top.  This allows one to export the file and change modes
sub cell_menu {
    Args::check_cell_mode();

    Print::print_html_header("Cell Header");

    my $cell_mode = $Args::args{'cell_mode'};

    my $url =
        "&$Args::baseargs&meta=$Args::enc_args{'meta'}&"
      . "dir=$Args::enc_args{'dir'}&recmode=$Args::enc_args{'recmode'}";

    if ($cell_mode == $CELL_MODE_SANIT) {

        print <<EOF1;
<center>
This file is currently being viewed in a <b>sanitized environment</b><br>
HTML files have been edited to disable scripts and links. 
The script contents will be shown as text.<br>
Pictures have been replaced by place holders<br>

<table width=300 cellspacing=\"0\" cellpadding=\"2\">
<tr>
  <td align=center>
    <a href=\"$::PROGNAME?mod=$::MOD_APPVIEW&view=$Appview::CELL_FRAME$url&cell_mode=$CELL_MODE_NORM\" 
      target=\"_top\">
	  <img src=\"pict/sanit_b_norm.jpg\" alt=\"Normal\" border=\"0\">
	</a>
  </td>
EOF1

    }

    elsif ($cell_mode == $CELL_MODE_NORM) {
        print <<EOF2;
<center>
This file is currently being viewed in a <b>normal environment</b><br>
HTML files are being viewed without modification.<br>

<table width=300 cellspacing=\"0\" cellpadding=\"2\">
<tr>
  <td align=center>
    <a href=\"$::PROGNAME?mod=$::MOD_APPVIEW&view=$Appview::CELL_FRAME&$url&cell_mode=$CELL_MODE_SANIT\" 
      target=\"_top\">
	  <img src=\"pict/sanit_b_san.jpg\" alt=\"Sanitized\" border=\"0\">
	</a>
  </td>
EOF2
    }

    # Export the file
    print "<td align=center>\n"
      . "<a href=\"$::PROGNAME?mod=$::MOD_FILE&view=$File::EXPORT&$url\">"
      . "<img src=\"pict/but_export.jpg\" alt=\"export\" border=\"0\" "
      . "width=123 height=20>"
      . "</a></td></tr>\n";

    print "<tr><td colspan=\"2\" align=\"center\">"
      . "Deleted File Recovery Mode</td></tr>\n"
      if ($Args::enc_args{'recmode'} == $File::REC_YES);

    print "</table>";

    Print::print_html_footer();
    return;
}

# Display safe and common things in the browser (pictures, basic html)
sub cell_content {
    Args::check_meta('meta');
    Args::check_dir();
    Args::check_cell_mode();

    my $meta = Args::get_meta('meta');
    my $vol  = Args::get_vol('vol');
    my $mnt  = $Caseman::vol2mnt{$vol};

    my $ftype   = $Caseman::vol2ftype{$vol};
    my $img     = $Caseman::vol2path{$vol};
    my $offset  = $Caseman::vol2start{$vol};
    my $imgtype = $Caseman::vol2itype{$vol};

    my $fname = "$mnt$Args::args{'dir'}";

    my $recflag = "";

    $recflag = " -r "
      if (Args::get_recmode() == $File::REC_YES);

    # identify what type it is
    local *OUT;
    Exec::exec_pipe(*OUT,
"'$::TSKDIR/icat' -f $ftype $recflag  -o $offset -i $imgtype $img $meta | '$::FILE_EXE' -z -b -"
    );
    my $file_type = Exec::read_pipe_line(*OUT);
    close(OUT);

    $file_type = "Error getting file type"
      if ((!defined $file_type) || ($file_type eq ""));

    if ($file_type =~ /JPEG image data/) {
        Print::log_host_inv("$vol: Viewing $fname ($meta) as JPEG");
        print "Content-type: image/jpeg$::HTTP_NL$::HTTP_NL";
    }
    elsif ($file_type =~ /GIF image data/) {
        Print::log_host_inv("$vol: Viewing $fname ($meta) as GIF");
        print "Content-type: image/gif$::HTTP_NL$::HTTP_NL";
    }
    elsif ($file_type =~ /PNG image data/) {
        Print::log_host_inv("$vol: Viewing $fname ($meta) as PNG");
        print "Content-type: image/png$::HTTP_NL$::HTTP_NL";
    }
    elsif ($file_type =~ /PC bitmap data/) {
        Print::log_host_inv("$vol: Viewing $fname ($meta) as BMP");
        print "Content-type: image/bmp$::HTTP_NL$::HTTP_NL";
    }
    elsif ($file_type =~ /HTML document text/) {
        Print::log_host_inv("$vol: Viewing $fname ($meta) as HTML");
        print "Content-type: text/html$::HTTP_NL$::HTTP_NL";
    }
    else {
        Print::log_host_inv("$vol: Unknown format of meta $meta ");
        Print::print_check_err("Unknown File Type for Viewing: $file_type");
    }

    local *OUT;
    Exec::exec_pipe(*OUT,
        "'$::TSKDIR/icat' -f $ftype $recflag  -o $offset -i $imgtype $img $meta"
    );

    while ($_ = Exec::read_pipe_line(*OUT)) {

        # Parse out bad "stuff"
        if (   ($file_type =~ /HTML document text/)
            && ($Args::args{'cell_mode'} == $CELL_MODE_SANIT))
        {
            $_ =~ s/\bsrc=/src=$::SANITIZE_TAG\?/ig;
            $_ =~ s/\bhref=/href=$::SANITIZE_TAG\?/ig;
            $_ =~ s/<script/<$::SANITIZE_TAG-script/ig;
            $_ =~ s/\bbackground=/background=$::SANITIZE_TAG\?/ig;
        }
        print "$_";
    }
    print "$::HTTP_NL$::HTTP_NL";
    close(OUT);
    return 0;
}

sub sanitize_pict {
    my $url  = shift();
    my $lurl = $url;
    $lurl =~ tr/[A-Z]/[a-z]/;

    print "HTTP/1.0 200 OK$::HTTP_NL";
    if (   ($lurl =~ /.jpg/i)
        || ($lurl =~ /.jpeg/i)
        || ($lurl =~ /.gif/i)
        || ($lurl =~ /.png/i)
        || ($lurl =~ /.bmp/i))
    {

        open PICT, "<$::PICTDIR/$::SANITIZE_PICT"
          or die "can not open $::PICTDIR/$::SANITIZE_PICT";

        print "Content-type: image/jpeg$::HTTP_NL$::HTTP_NL";
        while (<PICT>) {
            print "$_";
        }
        close(PICT);
        print "$::HTTP_NL$::HTTP_NL";
    }
    else {
        $url =~ tr/\+/ /;
        $url =~ s/%([a-f0-9][a-f0-9])/chr( hex( $1 ) )/eig;

        Print::print_html_header("Denied");
        print "<h1><center>Unable to Complete Request</h1><br>\n"
          . "<tt>Autopsy</tt> will not follow links from "
          . "untrusted HTML pages:<br><tt>$url</tt><br>\n";
        Print::print_html_footer();
    }

    exit(0);
}

