#-----------------------------------------------------------
# guestauth.pl
#
# History:
#  20201105 - created
#
# References:
#  https://twitter.com/NerdPyle/status/1060618344661827584
#  https://docs.microsoft.com/en-us/troubleshoot/windows-server/networking/guest-access-in-smb2-is-disabled-by-default
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package guestauth;
use strict;

my %config = (hive          => "system",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1112",
			  output		=> "report",
              version       => 20201105);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets AllowInsecureGuestAuth value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching guestauth v.".$VERSION);
	::rptMsg("guestauth v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\LanmanWorkstation\\Parameters";
	my $key = ();
	
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $g = $key->get_value("AllowInsecureGuestAuth")->get_data();
			::rptMsg("AllowInsecureGuestAuth value = ".$g);
			::rptMsg("");
			::rptMsg("Analsyis Tip: If the value is set to \"0\", insecure guest access is disabled. If the value is set to \"1\", insecure guest access is enabled.");
		};
		::rptMsg("AllowInsecureGuestAuth value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;