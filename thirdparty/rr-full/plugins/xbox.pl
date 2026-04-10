#-----------------------------------------------------------
# xbox.pl
# Check for existence of TreatDeviceAsXbox value
#
#
# Change history:
#  20200909 - created
# 
# Ref:
#  https://twitter.com/Hexacorn/status/1303293650835828736
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package xbox;
use strict;

my %config = (hive          => "Software",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
			  output		=> "report",
              version       => 20200909);

sub getConfig{return %config}
sub getShortDescr {
	return "Check for existence of TreatDeviceAsXbox value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching xbox v.".$VERSION);
	::rptMsg("xbox v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  

	my @paths = ('Microsoft\\Windows\\CurrentVersion\\Diagnostics\\DiagTrack\\TestHooks',
	             'Microsoft\\Windows\\CurrentVersion\\Diagnostics\\DiagTrack\\TestHooks\\Volatile');
	my $key_path;
	foreach $key_path (@paths) {
		my $reg = Parse::Win32Registry->new($hive);
		my $root_key = $reg->get_root_key;
	
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			eval {
				my $x = $key->get_value("TreatDeviceAsXbox")->get_data();
				::rptMsg("TreatDeviceAsXbox value = ".$x);
				::rptMsg("");
				::rptMsg("Analysis Tip: This value is queried via svchost.exe when several DLLs are loaded; if it exists,");
				::rptMsg("the behavior of the operating system or applications may be impacted.");
			};
			::rptMsg($key_path."\\TreatDeviceAsXbox value not found.") if ($@);
		}
		else {
			::rptMsg($key_path." key not found.");
		}
	}
}
1;