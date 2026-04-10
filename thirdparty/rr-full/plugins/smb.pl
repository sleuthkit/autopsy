#-----------------------------------------------------------
# smb.pl
# Checks status of SMBv1, v2, and V3 on the server
#
# History:
#  20220101 - created
#
# References:
#  https://docs.microsoft.com/en-us/windows-server/storage/file-server/troubleshoot/detect-enable-and-disable-smbv1-v2-v3
#  https://docs.microsoft.com/en-us/security-updates/securitybulletins/2017/ms17-010
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package smb;
use strict;

my %config = (hive          => "system",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562",  
			  output		=> "report",
              version       => 20220101);

sub getConfig{return %config}
sub getShortDescr {
	return "Get SMB server settings (v1, v2, v3)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching smb v.".$VERSION);
	::rptMsg("smb v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	
	my $key_path = $ccs."\\Services\\LanmanServer\\Parameters";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Keypath: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
# SMBv1 disabled on SMB Server		
		eval {
			my $v1 = $key->get_value("SMB1")->get_data();
			::rptMsg("SMB1 value: ".$v1);
			::rptMsg("");
			::rptMsg("SMB1 value: ".$v1);
			::rptMsg("0 - disabled");
			::rptMsg("1 - enabled (default)");
		};
		::rptMsg("SMB1 value not found\. SMBv1 may be enabled\.");

# SMBv2/v3 disabled on SMB Server		
		eval {
			my $v2 = $key->get_value("SMB2")->get_data();
			::rptMsg("");
			::rptMsg("SMB2 value: ".$v2);
			::rptMsg("0 - disabled");
			::rptMsg("1 - enabled (default)");
		};
		::rptMsg("SMB2 value not found\. SMBv2/v3 may be enabled\.");
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: SMBv1 has significant vulnerabilities, and MS encourages adminst to disable it\. That said, threat ");
	::rptMsg("actors can enable it, exposing the server to those vulnerabilities, potentially as a means of persistence\.");
	::rptMsg("SMBv1 is vulnerable to the MS17-010 vulnerability, known as \"Eternal Blue\"\.");
	::rptMsg("");
	::rptMsg("Ref: https://docs.microsoft.com/en-us/windows-server/storage/file-server/troubleshoot/detect-enable-and-disable-smbv1-v2-v3");
}

1;