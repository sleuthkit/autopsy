#-----------------------------------------------------------
# lsass_auditlevel
# Check AuditLevel for LSASS.exe
#
# Change history:
#  20220119 - created
#
# Ref:
#  https://docs.microsoft.com/en-us/windows-server/security/credentials-protection-and-management/configuring-additional-lsa-protection
#
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package lsass_auditlevel;
use strict;

my %config = (hive          => "software",
			  output		=> "report",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1003\.001",
              category      => "credential access",
              version       => 20220119);

sub getConfig{return %config}
sub getShortDescr {
	return "Check AuditLevel value for LSASS";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching lsass_auditlevel v.".$VERSION);
	::rptMsg("lsass_auditlevel v.".$VERSION); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options\\LSASS\.exe";
  
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		eval {
			my $a = $key->get_value("AuditLevel")->get_data();
			::rptMsg("AuditLevel value: ".$a);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: An \"AuditLevel\" value of 0x08 will result in event ID 3065 and 3066 records being generated to the");
	::rptMsg("Microsoft-Windows-CodeIntegrity Event Log file, indicating attempts to access the lsass process without meeting");
	::rptMsg("shared section security or code signing requirements, respectively\. Per the reference, use this plugin in ");
	::rptMsg("combination with the \"lsa\.pl\" plugin\.");
	::rptMsg("");
	::rptMsg("Ref: https://docs.microsoft.com/en-us/windows-server/security/credentials-protection-and-management/configuring-additional-lsa-protection");
}
1;