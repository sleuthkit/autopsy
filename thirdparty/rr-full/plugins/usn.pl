#-----------------------------------------------------------
# usn.pl
# 
#
# History:
#  20220104 - created
#
# References:
#  https://docs.microsoft.com/en-us/windows-server/storage/fsrm/fsrm-overview
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package usn;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562",  
              version       => 20220101);

sub getConfig{return %config}
sub getShortDescr {
	return "Get USN change journal settings on Windows Server";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching usn v.".$VERSION);
	::rptMsg("usn v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	
	my $key_path = $ccs."\\Services\\SrmSvc\\Settings";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Keypath: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $v1 = $key->get_value("SkipUSNCreationForSystem")->get_data();
			::rptMsg("SkipUSNCreationForSystem value: ".$v1);
			::rptMsg("");
#			::rptMsg("0 - disabled");
			::rptMsg("1 - USN Change Journal disabled on the system");
		};
		::rptMsg("SkipUSNCreationForSystem value not found\.");

		
		eval {
			my $v2 = $key->get_value("SkipUSNCreationForVolumes")->get_data();
			::rptMsg("");
			::rptMsg("SkipUSNCreationForVolumes value: ".$v2);
			::rptMsg("USN Change Journal disabled on the listed volumes");
		};
		::rptMsg("SkipUSNCreationForVolumes value not found\.");
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: USN Change Journal creation can be disabled on Windows Server\. The USN Change Journal is ");
	::rptMsg("recognized as a valuable investigative resource, and disabling it can significantly inhibit an investigation\.");
	::rptMsg("");
	::rptMsg("Ref: https://docs.microsoft.com/en-us/windows-server/storage/fsrm/fsrm-overview");
}

1;