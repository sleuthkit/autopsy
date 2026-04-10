#-------------------------------------------------------------
# time.pl
# This file contains helper functions for translating time values
# into something readable.  This file is accessed by the main UI
# code via the 'require' pragma.
#
# Note: The main UI code (GUI or CLI) must 'use' the Time::Local 
#       module.
#
# Change history:
#   20220523 - added references
#   20200728 - minor updates
#   20120925 - created
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-------------------------------------------------------------

#-------------------------------------------------------------
# References
# About Time: https://docs.microsoft.com/en-us/windows/win32/sysinfo/about-time
# Shell Items: https://github.com/libyal/libfwsi/blob/main/documentation/Windows%20Shell%20Item%20format.asciidoc
# FAT time stamp resolution: https://stackoverflow.com/questions/31524478/fat-date-resolution-timestamps-on-windows
# FAT date/time values: https://forensicswiki.xyz/page/FAT#FAT_date_and_time_values
#
#
#-------------------------------------------------------------

#-------------------------------------------------------------
# getTime()
# Translate FILETIME object (2 DWORDS) to Unix time, to be passed
# to gmtime() or localtime()
#
# The code was borrowed from Andreas Schuster's excellent work
#-------------------------------------------------------------
sub getTime($$) {
	my $lo = $_[0];
	my $hi = $_[1];
	my $t;

	if ($lo == 0 && $hi == 0) {
		$t = 0;
	} else {
		$lo -= 0xd53e8000;
		$hi -= 0x019db1de;
		$t = int($hi*429.4967296 + $lo/1e7);
	};
	$t = 0 if ($t < 0);
	return $t;
}

#-----------------------------------------------------------
# convertDOSDate()
# subroutine to convert 4 bytes of binary data into a human-
# readable format.  Returns both a string and a Unix-epoch
# time.
#-----------------------------------------------------------
sub convertDOSDate {
	my $date = shift;
	my $time = shift;
	
	if ($date == 0x00 || $time == 0x00){
		return (0,0);
	}
	else {
		my $sec = ($time & 0x1f) * 2;
		$sec = "0".$sec if (length($sec) == 1);
		if ($sec == 60) {$sec = 59};
		my $min = ($time & 0x7e0) >> 5;
		$min = "0".$min if (length($min) == 1);
		my $hr  = ($time & 0xF800) >> 11;
		$hr = "0".$hr if (length($hr) == 1);
		my $day = ($date & 0x1f);
		$day = "0".$day if (length($day) == 1);
		my $mon = ($date & 0x1e0) >> 5;
		$mon = "0".$mon if (length($mon) == 1);
		my $yr  = (($date & 0xfe00) >> 9) + 1980;
		my $gmtime = timegm($sec,$min,$hr,$day,($mon - 1),$yr);
    return ("$yr-$mon-$day $hr:$min:$sec",$gmtime);
#		return gmtime(timegm($sec,$min,$hr,$day,($mon - 1),$yr));
	}
}

#-----------------------------------------------------------
# convertSystemTime()
# Converts 128-bit SYSTEMTIME object to readable format
#-----------------------------------------------------------
sub convertSystemTime {
	my $date = $_[0];
	my @months = ("Jan","Feb","Mar","Apr","May","Jun","Jul",
	              "Aug","Sep","Oct","Nov","Dec");
	my @days = ("Sun","Mon","Tue","Wed","Thu","Fri","Sat");
	my ($yr,$mon,$dow,$dom,$hr,$min,$sec,$ms) = unpack("v*",$date);
	$hr = "0".$hr if ($hr < 10);
	$min = "0".$min if ($min < 10);
	$sec = "0".$sec if ($sec < 10);
	my $str = sprintf("%04d-%02d-%02d %02d:%02d:%02d",$yr,$mon,$dom,$hr,$min,$sec);
	return $str;
}

#-----------------------------------------------------------
# getFileTimeStr()
# Converts FILETIME string (i.e., "01D3C4A7328ED3C0") to *nix epoch
#  time
#-----------------------------------------------------------
sub getFileTimeStr {
	my $str = shift;
	my @ints = split(//,$str);
  return getTime(hex(join('',@ints[8..15])),hex(join('',@ints[0..7])));
}

#-----------------------------------------------------------
# format8601Date()
# Convert Unix epoch time to ISO8601-like format
# output date format in RFC 3339 profile of ISO 8601
#-----------------------------------------------------------
sub format8601Date {
	my $epoch = shift;
	my ($sec,$min,$hour,$mday,$mon,$year) = gmtime($epoch);
  return sprintf("%04d-%02d-%02d %02d:%02d:%02d",(1900 + $year),($mon + 1),$mday,$hour,$min,$sec);
}

1;