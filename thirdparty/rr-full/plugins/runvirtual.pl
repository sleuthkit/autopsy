#-----------------------------------------------------------
# runvirtual.pl
#   
#
# Change history
#   20220425 - updated code, added Analysis Tip
#   20201005 - MITRE update
#   20200427 - updated output date format
#   20191211 - created
#
# References
#   https://docs.microsoft.com/en-us/microsoft-desktop-optimization-pack/appv-v5/running-a-locally-installed-application-inside-a-virtual-environment-with-virtualized-applications
#	https://virtualvibes.algiz-technology.com/runvirtual-end-to-end/
#
# Copyright 2022 QAR, LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package runvirtual;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1610", 
              category      => "execution",
			  output		=> "report",
              version       => 20220425);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Gets RunVirtual entries";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching runvirtual v.".$VERSION);
	::rptMsg("runvirtual v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my $key_path = ();
 	if ($hive_guess eq "software") {
 		$key_path = ("Microsoft\\AppV\\Client\\RunVirtual");
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = ("Software\\Microsoft\\AppV\\Client\\RunVirtual");
 	}
 	else {}
	
	if ($key = $root_key->get_subkey($key_path)) {

		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
			
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lw   = $s->get_timestamp();
				::rptMsg("RunVirtual subkey: ".$name."  LastWrite: ".::format8601Date($lw)."Z");
				eval {
					my $def = $s->get_value("")->get_data();
					::rptMsg("  Default value = ".$def);
					::rptMsg("");
				};
			}
		}
		else {
			::rptMsg($key_path." has no subkeys\.");
		}
	}
	else {
		::rptMsg($key_path." not found\.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Subkeys can be added to the RunVirtual key, allowing locally installed applications to be run in");
	::rptMsg("virtual environments.");
	::rptMsg("");
	::rptMsg("Ref: https://docs.microsoft.com/en-us/windows/application-management/app-v/appv-running-locally-installed-applications-inside-a-virtual-environment");
#	::rptMsg("");
}

1;
