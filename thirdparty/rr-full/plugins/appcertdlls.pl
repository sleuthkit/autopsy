#-----------------------------------------------------------
# appcertdlls.pl
#
# History:
#  20200813 - minor updates
#  20200427 - updated output date format
#  20120912 - created
#
# References:
#  https://attack.mitre.org/techniques/T1546/009/
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appcertdlls;
use strict;

my %config = (hive          => "System",
			  output        => "report",
			  category      => "privilege escalation",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1546\.009",  
              version       => 20200813);

sub getConfig{return %config}
sub getShortDescr {
	return "Get entries from AppCertDlls key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching appcertdlls v.".$VERSION);
	::rptMsg("Launching appcertdlls v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $appcert_path = $ccs."\\Control\\Session Manager\\AppCertDlls";
		my $appcert;
		if ($appcert = $root_key->get_subkey($appcert_path)) {
			::rptMsg($appcert_path);
			::rptMsg("LastWrite Time: ".::format8601Date($appcert->get_timestamp())."Z");
			my @vals = $appcert->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					::rptMsg($name." - ".$data);
				}
			}
			else {
				::rptMsg($appcert_path."has no values.");
			}
		}
		else {
			::rptMsg($appcert_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;