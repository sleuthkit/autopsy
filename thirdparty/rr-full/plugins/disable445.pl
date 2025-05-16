#-----------------------------------------------------------
# disable445.pl
#
# History:
#  20220921 - created
#
# References:
#  https://answers.microsoft.com/en-us/windows/forum/all/windows-10-0x800704cf-error/cb4c3390-9fe9-4a4d-9f1c-c4651007c9b9
#  https://help.adobe.com/en_US/AEMForms/InstallWebSphere/WS1a95df6a070ac5e3-61aa016812fb665f150-7ff8.2.html
#  https://social.technet.microsoft.com/Forums/windows/en-US/84084cc8-52f9-40ce-b0b2-539ba2d7eb21/close-port-445-via-registry?forum=w7itprosecurity
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package disable445;
use strict;

my %config = (hive          => "System",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1562\.001",
              version       => 20220921);

sub getConfig{return %config}
sub getShortDescr {
	return "Determine if SMB over NetBIOS is disabled";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();


sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disable445 v.".$VERSION);
	::rptMsg("disable445 v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key;
	my $key_path = $ccs."\\Services\\NetBT\\Parameters";
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $d = $key->get_value("SMBDeviceEnabled")->get_data();
			::rptMsg("SMBDeviceEnabled value: ".$d);
		
		};
		::rptMsg("SMBDeviceEnabled value not found.") if ($@);
		::rptMsg("");
		::rptMsg("Analysis Tip: The \"SMBDeviceEnabled\" value controls whether port 445 is open. If the value does not");
		::rptMsg("exist, or is set to 1, it's enabled. If the value is set to 0, it's disabled. ");
		::rptMsg("");
		::rptMsg("Ref: https://superuser.com/questions/629648/how-to-disable-feature-that-opened-port-445-on-windows-server");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;