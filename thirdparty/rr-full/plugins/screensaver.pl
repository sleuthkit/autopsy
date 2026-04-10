#-----------------------------------------------------------
# screensaver.pl
#  
# Change history
#  20220427 - created
#
# References
#  https://cocomelonc.github.io/tutorial/2022/04/26/malware-pers-2.html
#  https://attack.mitre.org/techniques/T1546/002/
# 
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package screensaver;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "persistence", 
              MITRE         => "T1546\.002",
			  output		=> "report",
              version       => 20220427);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's screensaver settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching screensaver v.".$VERSION);
	::rptMsg("screensaver v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Control Panel\\Desktop';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			
			eval {
				my $s = $key->get_value("ScreenSaveActive")->get_data();
				if ($s == 1) {
					::rptMsg("Screensaver is active.");
				}
				elsif ($s == 0) {
					::rptMsg("Screensaver is not active.");
				}
				else {
					::rptMsg("ScreenSaveActive value: ".$s);
				}
			};
			::rptMsg("ScreenSaveActive value not found.") if ($@);
			
			eval {
				my $s = $key->get_value("ScreenSaverIsSecure")->get_data();
				::rptMsg("ScreenSaverIsSecure value: ".$s);
			};
			
			eval {
				my $s = $key->get_value("ScreenSaveTimeout")->get_data();
				::rptMsg("ScreenSaveTimeout value  : ".$s);
			};
			
			eval {
				my $s = $key->get_value("SCRNSAVE\.exe")->get_data();
				::rptMsg("SCRNSAVE\.exe value      : ".$s);
			};
			::rptMsg("SCRNSAVE\.exe value not found.") if ($@);
			
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Threat actors have been observed using the screen saver as a persistent mechanism.");
	::rptMsg("");
	::rptMsg("Ref: https://cocomelonc.github.io/tutorial/2022/04/26/malware-pers-2.html");
	::rptMsg("Ref: https://attack.mitre.org/techniques/T1546/002/");
}

1;