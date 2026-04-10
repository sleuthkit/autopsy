#-----------------------------------------------------------
# installelevated.pl
# 
#
# Change history
#  20230703 - created
#
# References
#  https://twitter.com/malmoeb/status/1564629592723361794
#  https://learn.microsoft.com/en-us/windows/win32/msi/alwaysinstallelevated
#  https://juggernaut-sec.com/alwaysinstallelevated/
# 
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package installelevated;
use strict;

my %config = (hive          => "software, ntuser\.dat",
              category      => "privilege escalation",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546\.016",
			  output		=> "report",
              version       => 20230703);

sub getConfig{return %config}
sub getShortDescr {
	return "Check AlwaysInstallElevated value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching installelevated v.".$VERSION);
	::rptMsg("installelevated v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr()); 
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
	
	my $key_path = ();
	my $key;
	
	if ($hive_guess eq "software") {
		$key_path = 'Policies\\Microsoft\\Windows\\Installer';
	}
	elsif ($hive_guess eq "ntuser") {
		$key_path = 'Software\\Policies\\Microsoft\\Windows\\Installer';
	}
	else {}
	
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("installelevated");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $a = $key->get_value("AlwaysInstallElevated")->get_data();
			::rptMsg("AlwaysInstallElevated value: ".$a);
			if ($a == 1) {
				::rptMsg("");
				::rptMsg("Analysis Tip: If the \"AlwaysInstallElevated\" value is set to 1, an attacker can escalate privileges");
				::rptMsg("to SYSTEM.");
				::rptMsg("");
				::rptMsg("Ref: https://learn.microsoft.com/en-us/windows/win32/msi/alwaysinstallelevated");
			}
		};
	}
	else {
		::rptMsg($key_path." key not found.");
	}
}

1;