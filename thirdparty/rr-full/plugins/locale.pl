#-----------------------------------------------------------
# locale.pl
#   Extracts locale settings from NTUSER.DAT and System hives
# 
# Change history
#   20220225 - created
#
# References
# 
#
#	https://attack.mitre.org/techniques/T1614/001/
#
# Copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package locale;
use strict;

my %config = (hive          => "System, NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "discovery",
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1614\.001",
              version       => 20220225);

my $VERSION = getVersion();

sub getDescr {}
sub getRefs {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Get locale settings from NTUSER\.DAT & System hives";
}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching locale v.".$VERSION);
    ::rptMsg("locale v.".$VERSION); 
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
	
	my $key = ();
	my $key_path = ();
	
	if ($hive_guess eq "system") {
		my $ccs = ::getCCS($root_key);
		$key_path = $ccs."\\Control\\Nls\\Language";
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			
			eval {
				my $id = $key->get_value("InstallLanguage")->get_data();
				::rptMsg(sprintf "InstallLanguage = ".$id." (".hex($id).")");
			};
			eval {
				my $id = $key->get_value("Default")->get_data();
				::rptMsg(sprintf "Default         = ".$id." (".hex($id).")");
			};
		
		}
	}
	elsif ($hive_guess eq "ntuser") {
		$key_path = "Control Panel\\International";
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			
			eval {
				my $l = $key->get_value("Locale")->get_data();
				::rptMsg("Locale     = ".$l." (".hex($l).")");
			};
			
			eval {
				my $l = $key->get_value("LocaleName")->get_data();
				::rptMsg("LocaleName = ".$l);
			};
			
		}
	}
	else {
#		
	}
	
	::rptMsg("");
	::rptMsg("Analysis Tip: Malware, in particular ransomware, has been observed checking for execution based on the");
	::rptMsg("locale of the system. This information can be used to determine execution flow, in EXEs, scripts, etc.");
	
}

1;
