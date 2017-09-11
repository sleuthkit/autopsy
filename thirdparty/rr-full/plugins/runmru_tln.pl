#-----------------------------------------------------------
# runmru_tln.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# RunMru values 
#
# Change history
#   20120828 - updated to TLN format
#   20080324 - created
#
# References
#
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package runmru_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120828);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's RunMRU key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching runmru v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\RunMRU';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("RunMru");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		my %runvals;
		my $mru;
		if (scalar(@vals) > 0) {
			my $mru;
			eval {
				my $m = $key->get_value("MRUList")->get_data();
				my $r = (split(//,$m))[0];
				$mru = $key->get_value($r)->get_data();
				::rptMsg($lw."|REG|||RunMRU: ".$mru);
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