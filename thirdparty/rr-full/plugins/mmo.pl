#-----------------------------------------------------------
# mmo.pl
# checks contents of Multimedia\Other key
# Category: AutoStart, Malware
# 
# History
#   20200922 - MITRE update
#   20200517 - updated date output format
#   20130217 - updated with Trojan.Swaylib detection
#   20130214 created
#
# Trojan.Swaylib - http://www.symantec.com/security_response/writeup.jsp?docid
#                        =2013-021418-2701-99&tabid=2
#
# References
#   http://blog.fireeye.com/research/2013/02/the-number-of-the-beast.html
#   http://www.joesecurity.org/reports/report-f3b9663a01a73c5eca9d6b2a0519049e.html
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mmo;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              MITRE         => "T1546",
              hasShortDescr => 1,
              category      => "persistence",
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              version       => 20200922);

sub getConfig{return %config}

sub getShortDescr {
	return "Checks NTUSER for Multimedia\\Other values [malware]";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	::logMsg("Launching mmo v.".$VERSION);
	::rptMsg("mmo v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Multimedia\\Other";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
		 	::rptMsg("Values:");
		 	foreach my $v (@vals) {
		 		::rptMsg("  Name: ".$v->get_name());
		 	}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
# Section added 17 Feb 2013, to address Trojan.Swaylib
#
	my $key_path = "Software\\Microsoft\\CTF\\LangBarAddIn";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			::rptMsg("");
			::rptMsg($key_path);
			foreach my $s (@subkeys) {
				::rptMsg("  ".$s->get_name());
				::rptMsg("  LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
				::rptMsg("");
				
				my $path;
				eval {
					$path = $s->get_value("FilePath")->get_data();
					::rptMsg("  FilePath: ".$path);
				};
				
				my $e;
				eval {
					$e = $s->get_value("Enable")->get_data();
					::rptMsg("  Enable: ".$path);
				};
			}
			::rptMsg("");
		}
		else {
			::rptMsg($key_path." has no subkeys\.");
		}
	}
	else {
		::rptMsg($key_path." not found\.");
	}
}
1;