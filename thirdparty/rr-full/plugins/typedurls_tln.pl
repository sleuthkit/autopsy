#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# typedurls_tln.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# TypedURLs values 
#
# Change history
#   20120827 - TLN version created
#   20080324 - created
#
# References
#   http://support.microsoft.com/kb/157729
#   http://msdn2.microsoft.com/en-us/library/aa908115.aspx
# 
# Notes:  Reportedly, only the last 20 entries are maintained;
#         Also, new entries aren't added to the key until the current
#         instance of IE is terminated.
# 
# copyright 2012
# Author: H. Carvey
#-----------------------------------------------------------
package typedurls_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20120827);

sub getConfig{return %config}
sub getShortDescr {
	return "Returns MRU for user's TypedURLs key (TLN)";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("IESample Registry Settings" => 
	            "http://msdn2.microsoft.com/en-us/library/aa908115.aspx",
	            "How to clear History entries in IE" =>
	            "http://support.microsoft.com/kb/157729");
	return %refs;	
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching typedurls v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Internet Explorer\\TypedURLs';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("TypedURLs");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my $url1;
			eval {
				$url1 = $key->get_value("url1")->get_data();
				::rptMsg($lw."|REG|||TypedURLs - url1: ".$url1);
			};
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}
1;