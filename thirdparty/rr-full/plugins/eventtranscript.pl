#-----------------------------------------------------------
# eventtranscript.pl
# Get EventTranscript\.db settings
#
# Change history:
#   20210927 - created
#
# References:
#   https://github.com/rathbuna/EventTranscript.db-Research
#   https://www.kroll.com/en/insights/publications/cyber/forensically-unpacking-eventtranscript/enabling-eventtranscript
#   
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package eventtranscript;
use strict;

my %config = (hive          => "software",
			  category      => "config",
			  MITRE         => "",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20210927);

sub getConfig{return %config}

sub getShortDescr {
	return "Get EventTranscript\.db settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching eventtranscript v.".$VERSION);
	::rptMsg("eventtranscript v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Windows\\CurrentVersion\\Policies\\DataCollection",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Policies\\DataCollection",
				 "Policies\\Microsoft\\Windows\\DataCollection");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			eval {
				my $a = $key->get_value("AllowTelemetry")->get_data();
				::rptMsg("AllowTelemetry value     : ".$a);
				::rptMsg("1 - Basic");
				::rptMsg("3 - Full");
			};
					
			eval {
				my $m = $key->get_value("MaxTelemetryAllowed")->get_data();
				::rptMsg("MaxTelemetryAllowed value: ".$m);
				::rptMsg("1 - Basic");
				::rptMsg("3 - Full");
			};
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Values within the DataCollection key control what's logged to EventsTranscript\.db.");
#	::rptMsg("");
#	::rptMsg("");
}
1;