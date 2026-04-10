#-----------------------------------------------------------
# elevatedinstall.pl
# If the AlwaysInstallElevated value is not set to "1" under both of 
# the preceding registry keys, the installer uses elevated privileges to 
# install managed applications and uses the current user's privilege level 
# for unmanaged applications.
#
#
# Change history:
#   20220831 - created
#
# References:
#   https://twitter.com/malmoeb/status/1564629592723361794
#   https://docs.microsoft.com/en-us/windows/win32/msi/alwaysinstallelevated
#   
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package elevatedinstall;
use strict;

my %config = (hive          => "software,ntuser\.dat",
			  category      => "privilege escalation",
			  MITRE         => "T1548",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20220831);

sub getConfig{return %config}

sub getShortDescr {
	return "Check AlwaysInstallElevated value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching elevatedinstall v.".$VERSION);
	::rptMsg("elevatedinstall v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	} 
	my $key; 
	my $key_path = ();
	
 	if ($hive_guess eq "software") {
 		$key_path = "Policies\\Microsoft\\Windows\\Installer";
 		if ($key = $root_key->get_subkey($key_path)) {
 			eval {
 				my $c = $key->get_value("AlwaysInstallElevated")->get_data();
				::rptMsg($key_path);
				::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
				::rptMsg("");
 				::rptMsg("AlwaysInstallElevated value: ".$c);
 				::rptMsg("");
				::rptMsg("Analysis Tip: If the AlwaysInstallElevated value is set to \"1\", the Installer uses elevated ");
				::rptMsg("privileges to install managed applications\.");
 			};
 			::rptMsg($key_path."\\AlwaysInstallElevated value not found.") if ($@);
 		}
 		else {
 			::rptMsg($key_path." not found.");
 		}
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = "Software\\Policies\\Microsoft\\Windows\\Installer";
 		if ($key = $root_key->get_subkey($key_path)) {
 			eval {
 				my $c = $key->get_value("AlwaysInstallElevated")->get_data();
				::rptMsg($key_path);
				::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
				::rptMsg("");
 				::rptMsg("AlwaysInstallElevated value: ".$c);
 				::rptMsg("");
				::rptMsg("Analysis Tip: If the AlwaysInstallElevated value is set to \"1\", the Installer uses elevated ");
				::rptMsg("privileges to install managed applications\.");
 			};
 			::rptMsg($key_path."\\AlwaysInstallElevated value not found.") if ($@);
 		}
 		else {
 			::rptMsg($key_path." not found.");
 		}
 	}
 	else {}
	
	
	
}
1;