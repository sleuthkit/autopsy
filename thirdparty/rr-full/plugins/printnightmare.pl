#-----------------------------------------------------------
# printnightmare.pl
#
# History:
#  20230319 - added reference
#  20220306 - added ParaFlare documentation
#  20210705 - created
#
# References:
#  20230319: https://techcommunity.microsoft.com/t5/ask-the-directory-services-team/a-print-nightmare-artifact-krbtgt-nt-authority/ba-p/3757962
#  https://vuldb.com/?id.177880
#  https://paraflare.com/luci-spools-the-fun-with-phobos-ransomware/
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package printnightmare;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "privilege escalation",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1068",  
              version       => 20230319);

sub getConfig{return %config}
sub getShortDescr {
	return "Get settings, re: PrintNightmare exploit, CVE-2021-34527";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $root_key = ();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching printnightmare v.".$VERSION);
	::rptMsg("printnightmare v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	$root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\Print\\Environments";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @sk1 = $key->get_list_of_subkeys();
		if (scalar @sk1 > 0) {
			foreach my $s1 (@sk1) {
				my $path = $key_path."\\".$s1->get_name()."\\Drivers";
				if ($root_key->get_subkey($path)) {
					processDrivers($path);
				}
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("Analysis Tip: POCs for the PrintNightmare exploit have been shown to be missing value data for several values,");
	::rptMsg("including InfPath and Manufacturer. However, these values missing data does not explicitly mean that you've been");
	::rptMsg("compromised via the exploit.");
	::rptMsg("");
	::rptMsg("Also be sure to review the Microsoft-Windows-PrintService/Admin Event Log for Event ID 808, with message \"The");
	::rptMsg("print spooler failed to load a plug-in module\" for exploitation attempts. Be sure to check for Security-Auditing");
	::rptMsg("event ID 4624 events, with type 3 logins, prior to the PrintServce/Admin event(s).");
	::rptMsg("");
	::rptMsg("Ref: https://paraflare.com/luci-spools-the-fun-with-phobos-ransomware/");
}

sub processDrivers {
	my $path = shift;
	my $key = ();
	if ($key = $root_key->get_subkey($path)) {
		my @sk = $key->get_list_of_subkeys();
		if (scalar @sk > 0) {
			foreach my $s (@sk) {
				processVersions($path."\\".$s->get_name());
			}
		}
	}
}

sub processVersions {
	my $path = shift;
	my $key = ();
	if ($key = $root_key->get_subkey($path)) {
		my @sk = $key->get_list_of_subkeys();
		if (scalar @sk > 0) {
			foreach my $s (@sk) {
				processPrinter($path."\\".$s->get_name());
			}
		}
	}
}

sub processPrinter {
	my $path = shift;
	my $key = ();
	my @vals = ("Configuration File","Data File","Driver","InfPath","Manufacturer");
	
	if ($key = $root_key->get_subkey($path)) {
		::rptMsg($path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		foreach my $v (@vals) {
			eval {
				my $i = $key->get_value($v)->get_data();
				::rptMsg(sprintf "%-20s %-40s",$v,$i);
			};
		}
		::rptMsg("");
	}
}

1;