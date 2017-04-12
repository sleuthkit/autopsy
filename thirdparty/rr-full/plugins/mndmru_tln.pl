#-----------------------------------------------------------
# mndmru_tln.pl
# Plugin for Registry Ripper,
# Map Network Drive MRU parser
#
# Change history
#   20120829 - updated to TLN
#   20080324 - mndmru.pl created
#
# References
#
# 
# copyright 2012
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mndmru_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120829);

sub getConfig{return %config}
sub getShortDescr {
	return "Get user's Map Network Drive MRU (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching mndmru v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Map Network Drive MRU';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("Map Network Drive MRU");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
    my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			eval {
				my $list = $key->get_value("MRUList")->get_data();
				my $l    = (split(//,$list))[0];
				my $mru  = $key->get_value($l)->get_data();
				::rptMsg($lw."|REG|||Map Network Drive MRU - ".$mru);
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