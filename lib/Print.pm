package Print;

#
# Utilities to print information
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

# Escape HTML entities
# Converts \n to <br>\n
sub html_encode {
    my $text = shift;
    $text =~ s/&/&amp;/gs;
    $text =~ s/</&lt;/gs;
    $text =~ s/>/&gt;/gs;
    $text =~ s/\"/&quot;/gs;
    $text =~ s/\n/<br>\n/gs;

    # @@@ LEADING SPACES and TABS
    # while ($text =~ s/^(&nbsp;)*\t/"$1&nbsp;&nbsp;&nbsp;&nbsp;"/eig) {}
    # while ($text =~ s/^(&nbsp;)* /"$1&nbsp;"/eig) {}
    return $text;
}

# remove control chars from printout
# this does not escape HTML entities, so you can pass this HTML code
sub print_output {
    my $out = shift;
    print "$out";

    while (my $out = shift) {
        foreach $_ (split(//, $out)) {
            if (   ($_ eq "\n")
                || ($_ eq "\r")
                || ($_ eq "\f")
                || ($_ eq "\t"))
            {
                print "$_";
            }
            elsif ((ord($_) < 0x20) && (ord($_) >= 0x00)) {
                print "^" . ord($_);
            }
            else {
                print "$_";
            }
        }
    }
}

# Added to provide output in hexdump format
# function gets called on a per-icat basis,
# The offset value is the byte offset that this data
# starts at, since the File.pm code calls it in 1024
# byte chunks)
sub print_hexdump {
    my $out    = shift;    # data to output
    my $offset = shift;    # starting byte offset in file
    my $buf    = "";

    foreach $i (split(//, $out)) {
        my $idx = $offset % 16;

        if ($idx == 0) {
            printf("%08X:  ", $offset);
        }

        printf("%02X", ord($i));
        if (($idx % 2) == 1) {
            printf(" ");
        }

        $buf[$idx] = $i;

        if ($idx == 15) {
            print "   ";
            for (my $j = 0; $j < 16; $j++) {
                if ($buf[$j] =~ m/[ -~]/) {
                    print $buf[$j];
                }
                else {
                    print ".";
                }
                $buf[$j] = 0;
            }
            print "\n";
        }
        $offset++;
    }

    # print out last line if < 16 bytes long
    my $l = $offset % 16;

    if ($l) {
        my $t = (16 - $l) * 2 + (16 - $l) / 2;
        for (my $j = 0; $j < $t; $j++) {
            print " ";
        }
        print "   ";
        for (my $j = 0; $j < $l; $j++) {
            if ($buf[$j] =~ m/[ -~]/) {
                print $buf[$j];
            }
            else {
                print ".";
            }
        }
        print "\n";
    }
}

############################################
#
# HTTP/HTML Headers and Footers

# The page that makes the frameset does not have a body statement
# This routine is used to make the minimum required header statements
sub print_html_header_frameset {
    my $text = shift;
    print "Content-Type: text/html; charset=utf-8$::HTTP_NL$::HTTP_NL";

    my $time = localtime();

    print <<EOF;
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<!-- Autopsy ver. $::VER Forensic Browser -->
<!-- Page created at: $time -->
<head>
  <title>$text</title>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <link rel="stylesheet" href="global.css">
</head>

EOF
}

sub print_html_footer_frameset {
    print "\n</html>\n" . "$::HTTP_NL$::HTTP_NL";
}

# Create the header information with the body tag
sub print_html_header {
    print_html_header_frameset(shift);
    print "<body bgcolor=\"$::BACK_COLOR\">\n\n";
    print "<link rel=\"SHORTCUT ICON\" href=\"pict/favicon.ico\">\n";
}

sub print_html_footer {
    print "\n</body>\n</html>\n" . "$::HTTP_NL$::HTTP_NL";
}

# Print the header with the margins set to 0 so that the tab buttons
# are flush with the edges of the frame
sub print_html_header_tabs {
    print_html_header_frameset(shift);
    print "<body marginheight=0 marginwidth=0 topmargin=0 "
      . "leftmargin=0 rightmargin=0 botmargin=0 bgcolor=\"$::BACK_COLOR\">\n\n";
    print "<link rel=\"SHORTCUT ICON\" href=\"pict/favicon.ico\">\n";
    $is_body = 1;
}

sub print_html_footer_tabs {
    print "\n</body>\n</html>\n" . "$::HTTP_NL$::HTTP_NL";
}

# Header for front page to warn about java script
sub print_html_header_javascript {
    my $text = shift;
    print "Content-Type: text/html; charset=utf-8$::HTTP_NL$::HTTP_NL";

    my $time = localtime();

    # The write line has to stay on one line
    print <<EOF;
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<!-- Autopsy ver. $::VER Forensic Browser -->
<!-- Page created at: $time -->
<head>
  <title>$text</title>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <link rel="stylesheet" href="global.css">
  <script language=\"JavaScript\">
  <!-- hide script from old browsers
  document.write(\'<center><font color=\"red\"><p>WARNING: Your browser currently has Java Script enabled.</font><p>You do not need Java Script to use Autopsy and it is recommended that it be turned off for security reasons.<hr></center>\');
  //-->
  </script>
</head>

<body bgcolor=\"$::BACK_COLOR\">
<link rel=\"SHORTCUT ICON\" href=\"pict/favicon.ico\">

EOF
}

sub print_html_footer_javascript {
    print "\n</body>\n</html>\n" . "$::HTTP_NL$::HTTP_NL";
}

# For raw text outputs (Pass the name of a file if it is being saved)
sub print_text_header {
    print "Content-Type: text/plain; charset=utf-8$::HTTP_NL";
    if (scalar @_ > 0) {
        my $fname = shift();
        print "Content-Disposition: inline; " . "filename=$fname;$::HTTP_NL";
    }
    print "$::HTTP_NL";
}

sub print_text_footer {
    print "$::HTTP_NL$::HTTP_NL";
}

# For forced save outputs
sub print_oct_header {
    print "Content-Type: application/octet-stream$::HTTP_NL";
    if (scalar @_ > 0) {
        my $fname = shift();
        print "Content-Disposition: inline; " . "filename=$fname;$::HTTP_NL";
    }
    print "$::HTTP_NL";
}

sub print_oct_footer {
}

# Error message that is used when an HTTP/HTML header is needed
# This escapes the characters that chould be HTML entities.
# it will also replace \n with <br> and other things that html_encode()
# can do. Do not send arbitrary HTML to this function.
sub print_check_err {
    print_html_header("");
    print html_encode(shift()) . "<br>\n";
    print_html_footer();
    sleep(1);
    exit 1;
}

# Error message when header already exists
# This escapes the characters that chould be HTML entities.
# it will also replace \n with <br> and other things that html_encode()
# can do. Do not send arbitrary HTML to this function.
sub print_err {
    print html_encode(shift()) . "<br>\n";
    sleep(1);
    print_html_footer();
    exit 1;
}

##################################################################
#
# Logging
#
#

sub investig_log_fname {
    return "" unless (defined $::host_dir       && $::host_dir        ne "");
    return "" unless (exists $Args::args{'inv'} && $Args::args{'inv'} ne "");

    return "$::host_dir" . "$::LOGDIR/$Args::args{'inv'}.log";
}

sub investig_exec_log_fname {
    return "" unless (defined $::host_dir       && $::host_dir        ne "");
    return "" unless (exists $Args::args{'inv'} && $Args::args{'inv'} ne "");

    return "$::host_dir" . "$::LOGDIR/$Args::args{'inv'}.exec.log";
}

sub host_log_fname {
    return "" unless (defined $::host_dir && $::host_dir ne "");

    return "$::host_dir" . "$::LOGDIR/host.log";
}

sub case_log_fname {
    return "" unless (defined $::case_dir && $::case_dir ne "");

    return "$::case_dir" . "case.log";
}

# Log data to the investigators specific log file
sub log_host_inv {
    return unless ($::USE_LOG == 1);

    my $str = shift;
    chomp $str;

    my $date  = localtime;
    my $fname = investig_log_fname();
    return if ($fname eq "");

    open HOSTLOG, ">>$fname" or die "Can't open log: $fname";
    print HOSTLOG "$date: $str\n";
    close(HOSTLOG);

    return;
}

sub log_host_inv_exec {
    return unless ($::USE_LOG == 1);
    my $str = shift;
    chomp $str;

    my $date  = localtime;
    my $fname = investig_exec_log_fname();
    return if ($fname eq "");

    open HOSTLOG, ">>$fname" or die "Can't open log: $fname";
    print HOSTLOG "$date: $str\n";
    close(HOSTLOG);

    return;
}

# log data to the general log file for the host
sub log_host_info {
    return unless ($::USE_LOG == 1);

    my $str = shift;
    chomp $str;

    my $date  = localtime;
    my $fname = host_log_fname();
    return if ($fname eq "");

    open HOSTLOG, ">>$fname" or die "Can't open log: $fname";
    print HOSTLOG "$date: $str\n";
    close(HOSTLOG);

    return;
}

sub log_case_info {
    return unless ($::USE_LOG == 1);
    my $str = shift;
    chomp $str;
    my $date  = localtime;
    my $fname = case_log_fname();
    return if ($fname eq "");

    open CASELOG, ">>$fname" or die "Can't open log: $fname";
    print CASELOG "$date: $str\n";
    close(CASELOG);

    return;
}

sub log_session_info {
    return unless ($::USE_LOG == 1);
    my $str = shift;
    chomp $str;
    my $date = localtime;

    my $lname = "autopsy.log";
    open AUTLOG, ">>$::LOCKDIR/$lname" or die "Can't open log: $lname";
    print AUTLOG "$date: $str\n";
    close(AUTLOG);

    return;
}

1;
