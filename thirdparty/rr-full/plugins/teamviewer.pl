#-----------------------------------------------------------
# teamviewer.pl
# Get TeamViewer Always_Online setting
#
# Change history:
#   20211025 - created
#
# References:
#   https://twitter.com/lkarlslund/status/1450413959106945030
#   
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package teamviewer;
use strict;

my %config = (hive          => "software",
			  category      => "persistence",
			  MITRE         => "",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20211025);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Teamviewer Always_Online setting";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching teamviewer v.".$VERSION);
	::rptMsg("teamviewer v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("TeamViewer",
	             "Wow6432Node\\TeamViewer");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			eval {
				my $a = $key->get_value("Always_Online")->get_data();
				::rptMsg("Always_Online value     : ".$a);
				::rptMsg("1 - TeamViewer is set to autostart");
			};
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: If the Always_Online value is set to 1, TeamViewer is set to autostart");
#	::rptMsg("");
#	::rptMsg("");
}
1;