#-----------------------------------------------------------
# sevenzip.pl
# 
# Change history
#   20220704 - updated to include MOTW prop. value
#   20200803 - updates
#   20200515 - minor updates
#   20130315 - minor updates added
#   20100218 - created
#
# References
#   https://isc.sans.edu/forums/diary/7Zip+MoW/28810/
# 
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sevenzip;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "file access",
              MITRE         => "T1074",
			  output		=> "report",
              version       => 20220704);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets 7-Zip histories & settings";	
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
	::logMsg("Launching sevenzip v.".$VERSION);
	::rptMsg("sevenzip v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my @keys = ('Software\\7-Zip',
	            'Software\\Wow6432Node\\7-Zip');

	foreach my $key_path (@keys) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
		
			eval {
				my $p = $key->get_subkey("FM")->get_value("PanelPath0")->get_data();
				::rptMsg("PanelPath0: ".$p);
				::rptMsg("");
			};

			eval {
				my $copy = $key->get_subkey("Compression")->get_value("ArcHistory")->get_data();
				my @c = split(/\00\00/,$copy);
				::rptMsg("ArcHistory:");
				foreach my $hist (@c) {
					$hist =~ s/\00//g;
					::rptMsg("  ".$hist);
				}
			};
		
			eval {
				my $copy = $key->get_subkey("Extraction")->get_value("PathHistory")->get_data();
				my @c = split(/\00\00/,$copy);
				::rptMsg("PathHistory:");
				foreach my $hist (@c) {
					$hist =~ s/\00//g;
					::rptMsg("  ".$hist);
				}
#				::rptMsg("");
			};
			
			eval {
				my $copy = $key->get_subkey("FM")->get_value("CopyHistory")->get_data();
				my @c = split(/\00\00/,$copy);
				::rptMsg("CopyHistory:");
				foreach my $hist (@c) {
					$hist =~ s/\00//g;
					::rptMsg("  ".$hist);
				}
#				::rptMsg("");
			};
			
			eval {
				my $copy = $key->get_subkey("FM")->get_value("FolderHistory")->get_data();
				my @c = split(/\00\00/,$copy);
				::rptMsg("FolderHistory:");
				foreach my $hist (@c) {
					$hist =~ s/\00//g;
					::rptMsg("  ".$hist);
				}
			};
# added 20220704			
			if (my $o = $key->get_subkey("Options")) {
				
				eval {
					my $m = $key->get_value("WriteZoneIdExtract")->get_data();
					::rptMsg("WriteZoneIdExtract = ".$m);
				};
				::rptMsg("WriteZoneIdExtract value not found.") if ($@);
				::rptMsg("");
				::rptMsg("Analysis Tip: If the WriteZoneIdExtract value doesn't exist, or is set to 0, MOTW is not propagated.");
				::rptMsg("If WriteZoneIdExtract = 1, MOTW is propagated.");
				::rptMsg("If WriteZoneIdExtract = 2, MOTW is propagated, for Office files only.");	
				::rptMsg("");
				::rptMsg("Ref: https://isc.sans.edu/forums/diary/7Zip+MoW/28810/");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
}
1;