#-----------------------------------------------------------
# diagnostics
# 
# Change History:
#  	20220531 - created
#  
# References
# 	https://twitter.com/gentilkiwi/status/1531384447219781634
#	https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.ScriptedDiagnostics::ScriptedDiagnosticsExecutionPolicy
#
# copyright 2022 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package diagnostics;
use strict;

my %config = (hive          => "software",
              MITRE         => "T1203",
              category      => "execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20220531);

sub getConfig{return %config}

sub getShortDescr {
	return "Get ScriptedDiagnostics settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching diagnostics v.".$VERSION);
	::rptMsg("diagnostics v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Policies\\Microsoft\\Windows\\ScriptedDiagnostics";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $e = $key->get_value("EnableDiagnostics")->get_data();
			::rptMsg("EnableDiagnostics value: ".$e);
		
		};
		
		eval {
			my $v = $key->get_value("ValidateTrust")->get_data();
			::rptMsg("ValidateTrust value    : ".$v);
		
		};
			
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: \"EnableDiagnostics\" set to \"0\" disables user access to run the troubleshooting wizard.");
	::rptMsg("This is a work-around that MS confirmed prevents the MSDT-Follina vulnerability from 27 May 2022.");
	::rptMsg("#CVE-2022-30190");
}
1;