#-----------------------------------------------------------
# sevenzip.pl
# 
# 
#
# Change history
#   20130315 - minor updates added
#   20100218 - created
#
# References
#
# 
#
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sevenzip;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130315);

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

	my @keys = ('Software\\7-Zip',
	            'Software\\Wow6432Node\\7-Zip');

	foreach my $key_path (@keys) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
		
			eval {
				::rptMsg("PanelPath0: ".$key->get_subkey("FM")->get_value("PanelPath0")->get_data());
				::rptMsg("");
			};

			eval {
				::rptMsg("ArcHistory:");
				my $copy = $key->get_subkey("Compression")->get_value("ArcHistory")->get_data();
				my @c = split(/\x00\x00/,$copy);
				foreach my $hist (@c) {
					$hist =~ s/\x00//g;
					::rptMsg("  ".$hist);
				}
			};
		
			eval {
				::rptMsg("PathHistory:");
				my $copy = $key->get_subkey("Extraction")->get_value("PathHistory")->get_data();
				my @c = split(/\x00\x00/,$copy);
				foreach my $hist (@c) {
					$hist =~ s/\x00//g;
					::rptMsg("  ".$hist);
				}
				::rptMsg("");
			};
			
			eval {
				::rptMsg("CopyHistory:");
				my $copy = $key->get_subkey("FM")->get_value("CopyHistory")->get_data();
				my @c = split(/\x00\x00/,$copy);
				foreach my $hist (@c) {
					$hist =~ s/\x00//g;
					::rptMsg("  ".$hist);
				}
				::rptMsg("");
			};
			
			eval {
				::rptMsg("FolderHistory:");
				my $copy = $key->get_subkey("FM")->get_value("FolderHistory")->get_data();
				my @c = split(/\x00\x00/,$copy);
				foreach my $hist (@c) {
					$hist =~ s/\x00//g;
					::rptMsg("  ".$hist);
				}
			};

		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}
1;