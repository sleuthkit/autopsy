#-----------------------------------------------------------
# sevenzip.pl
# Google Toolbar Search History plugin
# 
#
# Change history
#   20100218 - created
#
# References
#
# 
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package sevenzip;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100218);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets records of histories from 7-Zip keys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	my %hist;
	::logMsg("Launching 7-zip v.".$VERSION);
	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\7-Zip';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			::rptMsg("");
			my @arc = $key->get_subkey("Compression")->get_subkey("ArcHistory")->get_list_of_values();
			if (scalar @arc > 0) {
				::rptMsg("Compression\\ArcHistory");
				foreach my $a (@arc) {
					::rptMsg("  ".$a->get_name()." -> ".$a->get_data());
				}
			}
		};
		::rptMsg("Error: ".$@) if ($@);
		
		eval {
			::rptMsg("");
			my @arc = $key->get_subkey("Extraction")->get_subkey("PathHistory")->get_list_of_values();
			if (scalar @arc > 0) {
				::rptMsg("Extraction\\PathHistory");
				foreach my $a (@arc) {
					::rptMsg("  ".$a->get_name()." -> ".$a->get_data());
				}
			}
		};
		::rptMsg("Error: ".$@) if ($@);
		
		
		
		
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;