#-----------------------------------------------------------
# mmo.pl
# checks contents of Multimedia\Other key
# Category: AutoStart, Malware
# 
# History
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
# copyright 2013, Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mmo;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              category      => "malware",
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130217);

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
	::rptMsg("mmo v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Multimedia\\Other";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
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
	$key_path = "Software\\Microsoft\\CTF\\LangBarAddIn";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			::rptMsg("");
			::rptMsg($key_path);
			foreach my $s (@subkeys) {
				::rptMsg("  ".$s->get_name());
				::rptMsg("  LastWrite time: ".gmtime($s->get_timestamp()));
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
