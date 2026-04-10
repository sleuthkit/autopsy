#-----------------------------------------------------------
# appmodel
# 
# 
# References
#	https://docs.microsoft.com/en-us/windows/apps/get-started/developer-mode-features-and-debugging
#   https://twitter.com/malmoeb/status/1560536646696796161
#   https://www.sentinelone.com/labs/inside-malicious-windows-apps-for-malware-deployment/
#   https://twitter.com/wdormann/status/1466039420684021761
#   https://twitter.com/0gtweet/status/1675583251161792512
#
# History:
#  20230703 - updates to MITRE, references
#  20220819 - created
#
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appmodel;
use strict;

my %config = (hive          => "software",
              MITRE         => "T1548\.002",
              category      => "privilege escalation",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20230703);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets AppModelUnlock values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching appmodel v.".$VERSION);
	::rptMsg("appmodel v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Microsoft\\Windows\\CurrentVersion\\AppModelUnlock";
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		eval {
			my $l = $key->get_value("AllowAllTrustedApps")->get_data();
			::rptMsg(sprintf "%-35s %-2d","AllowAllTrustedApps",$l);
		};
		if ($@) {
			::rptMsg("AllowAllTrustedApps value not found.");
			::rptMsg("");
		}
		
		eval {
			my $l = $key->get_value("AllowDevelopmentWithoutDevLicense")->get_data();
			::rptMsg(sprintf "%-35s %-2d","AllowDevelopmentWithoutDevLicense",$l);
		};
		if ($@) {
			::rptMsg("AllowDevelopmentWithoutDevLicense value not found.");
		}
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Misuse of MS Apps can be an infection vector (see ref). ");
	::rptMsg("AllowAllTrustedApps = 1 allows loading of Apps not from the Windows Store (must have valid cert chain)");
	::rptMsg("(Enables sideloading)");
	::rptMsg("");
	::rptMsg("AllowDevelopmentWithoutDevLicense = 1 enables dev mode, allowing install of Apps from IDE, and allows users");
	::rptMsg("without SeCreateSymbolicLinkPrivilege to create symlinks.");
	::rptMsg("");
	::rptMsg("Ref: https://www.sentinelone.com/labs/inside-malicious-windows-apps-for-malware-deployment/");
	::rptMsg("Ref: https://twitter.com/0gtweet/status/1675583251161792512");
}
1;