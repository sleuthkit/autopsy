#-----------------------------------------------------------
# mndmru.pl
# Map Network Drive MRU parser
#
# Change history
#  20200922 - MITRE update
#  20200517 - updated date output format
#  20080324 - created
#
# References
#
# 
# copyright 2020 H. Carvey
#-----------------------------------------------------------
package mndmru;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1021\.002",
              category      => "lateral movement",
              version       => 20200922);

sub getConfig{return %config}
sub getShortDescr {
	return "Get contents of user's Map Network Drive MRU";	
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
	::rptMsg("mndmru v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Map Network Drive MRU';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Map Network Drive MRU");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %mnd;
# Retrieve values and load into a hash for sorting			
			foreach my $v (@vals) {
				my $val = $v->get_name();
				my $data = $v->get_data();
				$mnd{$val} = $data;
			}
# Print sorted content to report file			
			if (exists $mnd{"MRUList"}) {
				::rptMsg("  MRUList = ".$mnd{"MRUList"});
				delete $mnd{"MRUList"};
			}
			foreach my $m (sort {$a <=> $b} keys %mnd) {
				::rptMsg("  ".$m."   ".$mnd{$m});
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;