#-----------------------------------------------------------
# office_test.pl
# 
#
# Change history:
#   20230403 - created
#
# References:
#   https://attack.mitre.org/techniques/T1137/002/
#   https://www.cyberark.com/resources/threat-research-blog/persistence-techniques-that-persist
#   2014: https://www.hexacorn.com/blog/2014/04/16/beyond-good-ol-run-key-part-10/
#   2016: https://unit42.paloaltonetworks.com/unit42-technical-walkthrough-office-test-persistence-method-used-in-recent-sofacy-attacks/
#   2019: https://pentestlab.blog/2019/12/11/persistence-office-application-startup/
#   
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package office_test;
use strict;

my %config = (hive          => "software,ntuser\.dat",
			  category      => "persistence",
			  MITRE         => "T1137\.002",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20230403);

sub getConfig{return %config}

sub getShortDescr {
	return "Check for MS Office test/debug value";	
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
	::logMsg("Launching office_test v.".$VERSION);
	::rptMsg("office_test v.".$VERSION); 
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
# Set paths
 	my $key_path = ();
 	if ($hive_guess eq "software") {
 		$key_path = "Microsoft\\Office Test\\Special\\Perf";
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = "Software\\Microsoft\\Office Test\\Special\\Perf";
 	}
 	else {}
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		eval {
			my $d = $key->get_value("")->get_data();
			::rptMsg("\"Default\" value: ".$d);
		};
		::rptMsg("\"Default\" value not found.") if ($@);
	}	
	else {
		::rptMsg($key_path." key not found.");
	}

	::rptMsg("");
	::rptMsg("Analysis Tip: When MS applications are opened, they check for the \"Default\" value beneath this key, and ");
	::rptMsg("load the DLL listed in the value.");
	::rptMsg("");
	::rptMsg("Ref: https://www.cyberark.com/resources/threat-research-blog/persistence-techniques-that-persist");
	::rptMsg("Ref: https://unit42.paloaltonetworks.com/unit42-technical-walkthrough-office-test-persistence-method-used-in-recent-sofacy-attacks/");
}
1;