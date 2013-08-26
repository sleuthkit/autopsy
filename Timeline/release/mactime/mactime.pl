#!/usr/bin/perl -w
my $VER="4.1.0";
#
# This program is based on the 'mactime' program by Dan Farmer and
# and the 'mac_daddy' program by Rob Lee.
#
# It takes as input data from either 'ils -m' or 'fls -m' (from The Sleuth
# Kit) or 'mac-robber'.
# Based on the dates as arguments given, the data is sorted by and
# printed.
#
# The Sleuth Kit
# Brian Carrier [carrier <at> sleuthkit [dot] org]
# Copyright (c) 2003-2012 Brian Carrier.  All rights reserved
#
# TASK
# Copyright (c) 2002 Brian Carrier, @stake Inc.  All rights reserved
#
#
# The modifications to the original mactime are distributed under
# the Common Public License 1.0
#
#
# Copyright 1999 by Dan Farmer.  All rights reserved.  Some individual
# files may be covered by other copyrights (this will be noted in the
# file itself.)
#
# Redistribution and use in source and binary forms are permitted
# provided that this entire copyright notice is duplicated in all such
# copies.
#
# THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
# WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR ANY PARTICULAR PURPOSE.
#
# IN NO EVENT SHALL THE AUTHORS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, LOSS OF USE, DATA, OR PROFITS OR
# BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
# OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
# ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

use POSIX;
use strict;

my $debug = 0;

# %month_to_digit = ("Jan", 1, "Feb", 2, "Mar", 3, "Apr", 4, "May", 5, "Jun", 6,
#                    "Jul", 7, "Aug", 8, "Sep", 9, "Oct", 10, "Nov", 11, "Dec", 12);
my %digit_to_month = (
    "01", "Jan", "02", "Feb", "03", "Mar", "04", "Apr",
    "05", "May", "06", "Jun", "07", "Jul", "08", "Aug",
    "09", "Sep", "10", "Oct", "11", "Nov", "12", "Dec"
);
my %digit_to_day = (
    "0", "Sun", "1", "Mon", "2", "Tue", "3", "Wed",
    "4", "Thu", "5", "Fri", "6", "Sat"
);

sub usage {
    print <<EOF;
mactime [-b body_file] [-p password_file] [-g group_file] [-i day|hour idx_file] [-d] [-h] [-V] [-y] [-z TIME_ZONE] [DATE]
		-b: Specifies the body file location, else STDIN is used
		-d: Output in comma delimited format
		-h: Display a header with session information
		-i [day | hour] file: Specifies the index file with a summary of results
		-y: Dates are displayed in ISO 8601 format
		-m: Dates have month as number instead of word (does not work with -y)
		-z: Specify the timezone the data came from (in the local system format) (does not work with -y)
		-g: Specifies the group file location, else GIDs are used
		-p: Specifies the password file location, else UIDs are used
		-V: Prints the version to STDOUT
		[DATE]: starting date (yyyy-mm-dd) or range (yyyy-mm-dd..yyyy-mm-dd)
EOF
    exit(1);
}

sub version {
    print "The Sleuth Kit ver $VER\n";
}

my $BODY       = "";
my $GROUP      = "";
my $PASSWD     = "";
my $TIME       = "";
my $INDEX      = "";            # File name of index
my $INDEX_DAY  = 1;             # Daily index (for $INDEX_TYPE)
my $INDEX_HOUR = 2;
my $INDEX_TYPE = $INDEX_DAY;    # Saved to type of index
my $COMMA      = 0;             # Comma delimited output

my $iso8601 = 0;
my $month_num  = 0;
my $header     = 0;

my $in_seconds  = 0;
my $out_seconds = 0;
my %timestr2macstr;
my %file2other;

my %gid2names = ();
my %uid2names = ();

my $_HAS_DATETIME_TIMEZONE = 0;

eval "use DateTime::TimeZone";
if ($@) {
    $_HAS_DATETIME_TIMEZONE = 0;
} else {
    $_HAS_DATETIME_TIMEZONE = 1;
}

sub get_timezone_list() {
    my @t_list;
    if ( ! $_HAS_DATETIME_TIMEZONE ) {
       return @t_list;
    }

    foreach ( DateTime::TimeZone->all_names() ) {
        push( @t_list, $_ );
    }
    foreach( keys( %{DateTime::TimeZone->links()})  ) {
        push( @t_list, $_ );
    }

    return sort { $a cmp $b } @t_list;
}

usage() if (scalar(@ARGV) == 0);

while ((scalar(@ARGV) > 0) && (($_ = $ARGV[0]) =~ /^-(.)(.*)/)) {

    # Body File
    if (/^-b$/) {
        shift(@ARGV);
        if (defined $ARGV[0]) {
            $BODY = $ARGV[0];
        }
        else {
            print "-b requires body file argument\n";
        }
    }
    elsif (/^-d$/) {
        $COMMA = 1;
    }

    # Group File
    elsif (/^-g$/) {
        shift(@ARGV);
        if (defined $ARGV[0]) {
            &'load_group_info($ARGV[0]);
            $GROUP = $ARGV[0];
        }
        else {
            print "-g requires group file argument\n";
            usage();
        }
    }

    # Password File
    elsif (/^-p$/) {
        shift(@ARGV);
        if (defined $ARGV[0]) {
            &'load_passwd_info($ARGV[0]);
            $PASSWD = $ARGV[0];
        }
        else {
            print "-p requires password file argument\n";
            usage();
        }
    }
    elsif (/^-h$/) {
        $header = 1;
    }

    # Index File
    elsif (/^-i$/) {
        shift(@ARGV);

        if (defined $ARGV[0]) {

            # Find out what type
            if ($ARGV[0] eq "day") {
                $INDEX_TYPE = $INDEX_DAY;
            }
            elsif ($ARGV[0] eq "hour") {
                $INDEX_TYPE = $INDEX_HOUR;
            }
            shift(@ARGV);
            unless (defined $ARGV[0]) {
                print "-i requires index file argument\n";
                usage();
            }
            $INDEX = $ARGV[0];
        }
        else {
            print "-i requires index file argument and type\n";
            usage();
        }
        open(INDEX, ">$INDEX") or die "Can not open $INDEX";
    }
    elsif (/^-V$/) {
        version();
        exit(0);
    }
    elsif (/^-m$/) {
        $month_num = 1;
    }
    elsif (/^-y$/) {
        $iso8601 = 1;
    }
    elsif (/^-z$/) {
        shift(@ARGV);
        if (defined $ARGV[0]) {
            my $tz = "$ARGV[0]";

            if ($tz =~ m/^list$/i) {
                if ($_HAS_DATETIME_TIMEZONE) {
                    my $txt  = "
-----------------------------------
        TIMEZONE LIST
-----------------------------------\n";
                    foreach ( get_timezone_list() ) {
                        $txt .= $_ . "\n";
                    }
                    print( $txt );
                }
                else {
                    print "DateTime module not loaded -- cannot list timezones\n";
                }
                exit(0);
            }
            # validate the string if we have DateTime module
            elsif ($_HAS_DATETIME_TIMEZONE) {
                my $realtz = 0;
                foreach ( get_timezone_list() ) {
                    if ($tz =~ m/^$_$/i) {
                        $realtz = $_;
                        last;
                    }
                }
                if ($realtz) {
                    $ENV{TZ} = $realtz;
                }
                else {
                    print "invalid timezone provided. Use -z to list valid timezones.\n";
                    usage();
                }
            }
            # blindly take it otherwise
            else {
                $ENV{TZ} = $tz;
            }      
        }
        else {
            print "-z requires the time zone argument\n";
            usage();
        }
    }
    else {
        print "Unknown option: $_\n";
        usage();
    }
    shift(@ARGV);
}

# Was the time given
if (defined $ARGV[0]) {
    my $t_in;
    my $t_out;

    $TIME = $ARGV[0];
    if ($ARGV[0] =~ /\.\./) {
        ($t_in, $t_out) = split(/\.\./, $ARGV[0]);
    }
    else {
        $t_in  = $ARGV[0];
        $t_out = 0;
    }
    $in_seconds = parse_isodate($t_in);
    die "Invalid Date: $t_in\n" if ($in_seconds < 0);

    if ($t_out) {
        $out_seconds = parse_isodate($t_out);
        die "Invalid Date: $t_out\n" if ($out_seconds < 0);
    }
    else {
        $out_seconds = 0;
    }
}
else {
    $in_seconds  = 0;
    $out_seconds = 0;
}

# Print header info
print_header() if ($header == 1);

# Print the index header
if ($INDEX ne "") {
    my $time_str = "";
    if ($INDEX_TYPE == $INDEX_DAY) {
        $time_str = "Daily";
    }
    else {
        $time_str = "Hourly";
    }
    if ($BODY ne "") {
        print INDEX "$time_str Summary for Timeline of $BODY\n\n";
    }
    else {
        print INDEX "$time_str Summary for Timeline of STDIN\n\n";
    }
}

read_body();

print_tl();

################ SUBROUTINES ##################

#convert yyyy-mm-dd string to Unix date
sub parse_isodate {
    my $iso_date = shift;

    my $sec  = 0;
    my $min  = 0;
    my $hour = 0;
    my $wday = 0;
    my $yday = 0;
    if ($iso_date =~ /^(\d\d\d\d)\-(\d\d)\-(\d\d)$/) {
        return mktime($sec, $min, $hour, $3, $2 - 1, $1 - 1900, $wday, $yday);
    }
    else {
        return -1;
    }
}

# Read the body file from the BODY variable
sub read_body {

    # Read the body file from STDIN or the -b specified body file
    if ($BODY ne "") {
        open(BODY, "<$BODY") or die "Can't open $BODY";
    }
    else {
        open(BODY, "<&STDIN") or die "Can't dup STDIN";
    }

    while (<BODY>) {
        next if ((/^\#/) || (/^\s+$/));

        chomp;

        my (
            $tmp1,     $file,     $st_ino,    $st_ls,
            $st_uid,   $st_gid,   $st_size,   $st_atime,
            $st_mtime, $st_ctime, $st_crtime, $tmp2
          )
          = &tm_split($_);

        # Sanity check so that we ignore the header entries
        next unless ((defined $st_ino)    && ($st_ino    =~ /[\d-]+/));
        next unless ((defined $st_uid)    && ($st_uid    =~ /\d+/));
        next unless ((defined $st_gid)    && ($st_gid    =~ /\d+/));
        next unless ((defined $st_size)   && ($st_size    =~ /\d+/));
        next unless ((defined $st_mtime)  && ($st_mtime  =~ /\d+/));
        next unless ((defined $st_atime)  && ($st_atime  =~ /\d+/));
        next unless ((defined $st_ctime)  && ($st_ctime  =~ /\d+/));
        next unless ((defined $st_crtime) && ($st_crtime =~ /\d+/));

        # we need *some* value in mactimes!
        next if (!$st_atime && !$st_mtime && !$st_ctime && !$st_crtime);

        # Skip if these are all too early
        next
          if ( ($st_mtime < $in_seconds)
            && ($st_atime < $in_seconds)
            && ($st_ctime < $in_seconds)
            && ($st_crtime < $in_seconds));

        # add leading zeros to timestamps because we will later sort
        # these using a string-based comparison
        $st_mtime  = sprintf("%.10d", $st_mtime);
        $st_atime  = sprintf("%.10d", $st_atime);
        $st_ctime  = sprintf("%.10d", $st_ctime);
        $st_crtime = sprintf("%.10d", $st_crtime);

        # Put all the times in one big array along with the inode and
        # name (they are used in the final sorting)

        # If the date on the file is too old, don't put it in the array
        my $post = ",$st_ino,$file";

        if ($out_seconds) {
            $timestr2macstr{"$st_mtime$post"} .= "m"
              if (
                   ($st_mtime >= $in_seconds)
                && ($st_mtime < $out_seconds)
                && (   (!(exists $timestr2macstr{"$st_mtime$post"}))
                    || ($timestr2macstr{"$st_mtime$post"} !~ /m/))
              );

            $timestr2macstr{"$st_atime$post"} .= "a"
              if (
                   ($st_atime >= $in_seconds)
                && ($st_atime < $out_seconds)
                && (   (!(exists $timestr2macstr{"$st_atime$post"}))
                    || ($timestr2macstr{"$st_atime$post"} !~ /a/))
              );

            $timestr2macstr{"$st_ctime$post"} .= "c"
              if (
                   ($st_ctime >= $in_seconds)
                && ($st_ctime < $out_seconds)
                && (   (!(exists $timestr2macstr{"$st_ctime$post"}))
                    || ($timestr2macstr{"$st_ctime$post"} !~ /c/))
              );

            $timestr2macstr{"$st_crtime$post"} .= "b"
              if (
                   ($st_crtime >= $in_seconds)
                && ($st_crtime < $out_seconds)
                && (   (!(exists $timestr2macstr{"$st_crtime$post"}))
                    || ($timestr2macstr{"$st_crtime$post"} !~ /b/))
              );
        }
        else {
            $timestr2macstr{"$st_mtime$post"} .= "m"
              if (
                ($st_mtime >= $in_seconds)
                && (   (!(exists $timestr2macstr{"$st_mtime$post"}))
                    || ($timestr2macstr{"$st_mtime$post"} !~ /m/))
              );

            $timestr2macstr{"$st_atime$post"} .= "a"
              if (
                ($st_atime >= $in_seconds)
                && (   (!(exists $timestr2macstr{"$st_atime$post"}))
                    || ($timestr2macstr{"$st_atime$post"} !~ /a/))
              );

            $timestr2macstr{"$st_ctime$post"} .= "c"
              if (
                ($st_ctime >= $in_seconds)
                && (   (!(exists $timestr2macstr{"$st_ctime$post"}))
                    || ($timestr2macstr{"$st_ctime$post"} !~ /c/))
              );

            $timestr2macstr{"$st_crtime$post"} .= "b"
              if (
                ($st_crtime >= $in_seconds)
                && (   (!(exists $timestr2macstr{"$st_crtime$post"}))
                    || ($timestr2macstr{"$st_crtime$post"} !~ /b/))
              );
        }

        # if the UID or GID is not in the array then add it.
        # these are filled if the -p or -g options are given
        $uid2names{$st_uid} = $st_uid
          unless (defined $uid2names{$st_uid});
        $gid2names{$st_gid} = $st_gid
          unless (defined $gid2names{$st_gid});

        #
        # put /'s between multiple UID/GIDs
        #
        $uid2names{$st_uid} =~ s@\s@/@g;
        $gid2names{$st_gid} =~ s@\s@/@g;

        $file2other{$file} =
          "$st_ls:$uid2names{$st_uid}:$gid2names{$st_gid}:$st_size";
    }

    close BODY;
}    # end of read_body

sub print_header {
    return if ($header == 0);

    print "The Sleuth Kit mactime Timeline\n";

    print "Input Source: ";
    if ($BODY eq "") {
        print "STDIN\n";
    }
    else {
        print "$BODY\n";
    }

    print "Time: $TIME\t\t" if ($TIME ne "");

    if ($ENV{TZ} eq "") {
        print "\n";
    }
    else {
        print "Timezone: $ENV{TZ}\n";
    }

    print "passwd File: $PASSWD" if ($PASSWD ne "");
    if ($GROUP ne "") {
        print "\t" if ($PASSWD ne "");
        print "group File: $GROUP";
    }
    print "\n" if (($PASSWD ne "") || ($GROUP ne ""));

    print "\n";
}

#
# Print the time line
#
sub print_tl {

    my $prev_day        = "";   # has the format of 'day day_week mon year'
    my $prev_hour       = "";   # has just the hour and is used for hourly index
    my $prev_cnt        = 0;
    my $old_date_string = "";

    my $delim = ":";
    if ($COMMA != 0) {
        print "Date,Size,Type,Mode,UID,GID,Meta,File Name\n";
        $delim = ",";
    }

    # Cycle through the files and print them in sorted order.
    # Note that we sort using a string comparison because the keys
    # also contain the inode and file name
    for my $key (sort { $a cmp $b } keys %timestr2macstr) {
        my $time;
        my $inode;
        my $file;

        if ($key =~ /^(\d+),([\d-]+),(.*)$/) {
            $time  = $1;
            $inode = $2;
            $file  = $3;
        }
        else {
            next;
        }

        my ($sec, $min, $hour, $mday, $mon, $year, $wday, $yday, $isdst);
        if ($iso8601) {
            ($sec, $min, $hour, $mday, $mon, $year, $wday, $yday, $isdst) =
              gmtime($time);
        }
        else {
            ($sec, $min, $hour, $mday, $mon, $year, $wday, $yday, $isdst) =
              localtime($time);
        }

        # the month here is 0-11, not 1-12, like what we want
        $mon++;

        print
"\t($sec,$min,$hour,MDay: $mday,M: $mon,$year,$wday,$yday,$isdst) = ($time)\n"
          if $debug;

        #
        # cosmetic change to make it look like unix dates
        #
        $mon  = "0$mon"  if $mon < 10;
        $mday = "0$mday" if $mday < 10;
        $hour = "0$hour" if $hour < 10;
        $min  = "0$min"  if $min < 10;
        $sec  = "0$sec"  if $sec < 10;

        my $yeart = $year + 1900;

        #  How do we print the date?
        #
        my $date_string;
        if ($iso8601) {
            if ($time == 0) {
                $date_string = "0000-00-00T00:00:00Z";
            }
            else {
                $date_string =
"$yeart-$mon-${mday}T$hour:$min:${sec}Z";
            }
        }
        else {
            if ($time == 0) {
                $date_string = "Xxx Xxx 00 0000 00:00:00";
            }
            elsif ($month_num) {
                $date_string =
                  "$digit_to_day{$wday} $mon $mday $yeart $hour:$min:$sec";
            }
            else {
                $date_string =
"$digit_to_day{$wday} $digit_to_month{$mon} $mday $yeart $hour:$min:$sec";
            }
        }

        #
        # However, we only print the date if it's different from the one
        # above.  We need to fill the empty space with blanks, though.
        #
        if ($old_date_string eq $date_string) {
            if ($iso8601) {
                $date_string = "                    ";
            }
            else {
                $date_string = "                        ";
            }
            $prev_cnt++
              if ($INDEX ne "");
        }
        else {
            $old_date_string = $date_string;

            # Indexing code
            if ($INDEX ne "") {

                # First time it is run
                if ($prev_day eq "") {
                    $prev_day  = "$mday $wday $mon $yeart";
                    $prev_hour = $hour;
                    $prev_cnt  = 0;
                }

                # A new day, so print the results
                elsif ($prev_day ne "$mday $wday $mon $yeart") {
                    my @prev_vals = split(/ /, $prev_day);

                    my $date_str;
                    if ($month_num) {
                        $date_str =
                            "$digit_to_day{$prev_vals[1]} "
                          . "$prev_vals[2] "
                          . "$prev_vals[0] ${prev_vals[3]}";
                    }
                    else {
                        $date_str =
                            "$digit_to_day{$prev_vals[1]} "
                          . "$digit_to_month{$prev_vals[2]} "
                          . "$prev_vals[0] ${prev_vals[3]}";
                    }

                    $date_str .= " $prev_hour:00:00"
                      if ($INDEX_TYPE == $INDEX_HOUR);

                    print INDEX "${date_str}${delim} $prev_cnt\n";

                    # Reset
                    $prev_cnt  = 0;
                    $prev_day  = "$mday $wday $mon $yeart";
                    $prev_hour = $hour;

                }

                # Same day, but new hour
                elsif (($INDEX_TYPE == $INDEX_HOUR) && ($prev_hour != $hour)) {
                    my @prev_vals = split(/ /, $prev_day);

                    if ($month_num) {
                        print INDEX "$digit_to_day{$prev_vals[1]} "
                          . "$prev_vals[2] "
                          . "$prev_vals[0] ${prev_vals[3]} "
                          . "$prev_hour:00:00${delim} $prev_cnt\n";
                    }
                    else {
                        print INDEX "$digit_to_day{$prev_vals[1]} "
                          . "$digit_to_month{$prev_vals[2]} "
                          . "$prev_vals[0] ${prev_vals[3]} "
                          . "$prev_hour:00:00${delim} $prev_cnt\n";
                    }

                    # Reset
                    $prev_cnt  = 0;
                    $prev_hour = $hour;
                }
                $prev_cnt++;
            }
        }

        #
        #  Muck around with the [mac]times string to make it pretty.
        #
        my $mactime_tmp = $timestr2macstr{$key};
        my $mactime     = "";
        if ($mactime_tmp =~ /m/) {
            $mactime = "m";
        }
        else {
            $mactime = ".";
        }

        if ($mactime_tmp =~ /a/) {
            $mactime .= "a";
        }
        else {
            $mactime .= ".";
        }

        if ($mactime_tmp =~ /c/) {
            $mactime .= "c";
        }
        else {
            $mactime .= ".";
        }

        if ($mactime_tmp =~ /b/) {
            $mactime .= "b";
        }
        else {
            $mactime .= ".";
        }

        my ($ls, $uids, $groups, $size) = split(/:/, $file2other{$file});

        print "FILE: $file MODES: $ls U: $uids G: $groups S: $size\n"
          if $debug;

        if ($COMMA == 0) {
            printf("%s %8s %3s %s %-8s %-8s %-8s %s\n",
                $date_string, $size, $mactime, $ls, $uids, $groups, $inode,
                $file);
        }
        else {
            # escape any quotes in filename
            my $file_tmp = $file;
            $file_tmp =~ s/\"/\"\"/g;
            printf("%s,%s,%s,%s,%s,%s,%s,\"%s\"\n",
                $old_date_string, $size, $mactime, $ls, $uids, $groups, $inode,
                $file_tmp);
        }
    }

    # Finish the index page for the last entry
    if (($INDEX ne "") && ($prev_cnt > 0)) {
        my @prev_vals = split(/ /, $prev_day);

        my $date_str;
        if ($month_num) {
            $date_str =
                "$digit_to_day{$prev_vals[1]} "
              . "$prev_vals[2] "
              . "$prev_vals[0] ${prev_vals[3]}";
        }
        else {
            $date_str =
                "$digit_to_day{$prev_vals[1]} "
              . "$digit_to_month{$prev_vals[2]} "
              . "$prev_vals[0] ${prev_vals[3]}";
        }

        $date_str .= " $prev_hour:00:00"
          if ($INDEX_TYPE == $INDEX_HOUR);

        print INDEX "${date_str}${delim} $prev_cnt\n";
        close INDEX;
    }
}

#
#   Routines for reading and caching user and group information.  These
# are used in multiple programs... it caches the info once, then hopefully
# won't be used again.
#
#  Steve Romig, May 1991.
#
# Provides a bunch of routines and a bunch of arrays.  Routines
# (and their usage):
#
#    load_passwd_info($use_getent, $file_name)
#
#	loads user information into the %uname* and %uid* arrays
#	(see below).
#
#	If $use_getent is non-zero:
#	    get the info via repeated 'getpwent' calls.  This can be
#	    *slow* on some hosts, especially if they are running as a
#	    YP (NIS) client.
#	If $use_getent is 0:
#	    if $file_name is "", then get the info from reading the
#	    results of "ypcat passwd" and from /etc/passwd.  Otherwise,
#	    read the named file.  The file should be in passwd(5)
#	    format.
#
#    load_group_info($use_gentent, $file_name)
#
#	is similar to load_passwd_info.
#
# Information is stored in several convenient associative arrays:
#
#   %uid2names		Assoc array, indexed by uid, value is list of
#			user names with that uid, in form "name name
#			name...".
#
#   %gid2members	Assoc array, indexed by gid, value is list of
#			group members in form "name name name..."
#
#   %gname2gid		Assoc array, indexed by group name, value is
#			matching gid.
#
#   %gid2names		Assoc array, indexed by gid, value is the
#			list of group names with that gid in form
#			"name name name...".
#
# You can also use routines named the same as the arrays - pass the index
# as the arg, get back the value.  If you use this, get{gr|pw}{uid|gid|nam}
# will be used to lookup entries that aren't found in the cache.
#
# To be done:
#    probably ought to add routines to deal with full names.
#    maybe there ought to be some anal-retentive checking of password
#	and group entries.
#    probably ought to cache get{pw|gr}{nam|uid|gid} lookups also.
#    probably ought to avoid overwriting existing entries (eg, duplicate
#       names in password file would collide in the tables that are
#	indexed by name).
#
# Disclaimer:
#    If you use YP and you use netgroup entries such as
#	+@servers::::::
#	+:*:::::/usr/local/utils/messages
#    then loading the password file in with &load_passwd_info(0) will get
#    you mostly correct YP stuff *except* that it won't do the password and
#    shell substitutions as you'd expect.  You might want to use
#    &load_passwd_info(1) instead to use getpwent calls to do the lookups,
#    which would be more correct.
#
#
#  minor changes to make it fit with the TCT program, 9/25/99, - dan
# A whole lot removed to clean it up for TSK - July 2008 - Brian
#

package main;

my $passwd_loaded = 0;    # flags to use to avoid reloading everything
my $group_loaded  = 0;    # unnecessarily...

#
# Update user information for the user named $name.  We cache the password,
# uid, login group, home directory and shell.
#

sub add_pw_info {
    my ($name, $tmp, $uid) = @_;

    if ((defined $name) && ($name ne "")) {

        if ((defined $uid) && ($uid ne "")) {
            if (defined($uid2names{$uid})) {
                $uid2names{$uid} .= " $name";
            }
            else {
                $uid2names{$uid} = $name;
            }
        }
    }
}

#
# Update group information for the group named $name.  We cache the gid
# and the list of group members.
#

sub add_gr_info {
    my ($name, $tmp, $gid) = @_;

    if ((defined $name) && ($name ne "")) {

        if ((defined $gid) && ($gid ne "")) {
            if (defined($gid2names{$gid})) {
                $gid2names{$gid} .= " $name";
            }
            else {
                $gid2names{$gid} = $name;
            }
        }
    }
}

sub load_passwd_info {
    my ($file_name) = @_;
    my (@pw_info);

    if ($passwd_loaded) {
        return;
    }

    $passwd_loaded = 1;

    open(FILE, $file_name)
      || die "can't open $file_name";

    while (<FILE>) {
        chop;

        if ($_ !~ /^\+/) {
            &add_pw_info(split(/:/));
        }
    }
    close(FILE);
}

sub load_group_info {
    my ($file_name) = @_;
    my (@gr_info);

    if ($group_loaded) {
        return;
    }

    $group_loaded = 1;

    open(FILE, $file_name)
      || die "can't open $file_name";

    while (<FILE>) {
        chop;
        if ($_ !~ /^\+/) {
            &add_gr_info(split(/:/));
        }
    }
    close(FILE);
}

#
# Split a time machine record.
#
sub tm_split {
    my ($line) = @_;
    my (@fields);

    for (@fields = split(/\|/, $line)) {
        s/%([A-F0-9][A-F0-9])/pack("C", hex($1))/egis;
    }
    return @fields;
}
1;

