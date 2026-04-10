#-----------------------------------------------------------
# deviceguard.pl
#
# History:
#  20201025 - created
#
# References:
#   https://docs.microsoft.com/en-us/windows/security/identity-protection/credential-guard/credential-guard-manage
#   https://docs.microsoft.com/en-us/windows/security/threat-protection/device-guard/enable-virtualization-based-protection-of-code-integrity
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package deviceguard;
use strict;

my %config = (hive          => "System, Software",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1562\.001",  
              version       => 20201025);

sub getConfig{return %config}
sub getShortDescr {
	return "Check Device Guard settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching deviceguard v.".$VERSION);
	::rptMsg("deviceguard v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
  
  my %g = ::guessHive($hive);
  my $guess = (keys %g)[0];
  
  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# hive
	if ($guess eq "system") {
		my $ccs = ::getCCS($root_key);
		my $key_path = $ccs."\\Control\\DeviceGuard";
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "%-40s %-20s",$v->get_name(),$v->get_data());
				}
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
		
		$key_path = $ccs."\\Control\\DeviceGuard\\Scenarios";
		if ($key = $root_key->get_subkey($key_path)) {
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				::rptMsg("");
				::rptMsg("Scenarios");
				foreach my $s (@subkeys) {
					::rptMsg("  ".$s->get_name());
					::rptMsg("  LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
					my @vals = $s->get_list_of_values();
					if (scalar @vals > 0) {
						foreach my $v (@vals) {
							::rptMsg(sprintf "    %-25s %-10s",$v->get_name(),$v->get_data());
						}
					}
					::rptMsg("");
				}
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}

	}
	elsif ($guess eq "software") {
		my $key_path = "Policies\\Microsoft\\Windows\\DeviceGuard";
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "%-40s %-20s",$v->get_name(),$v->get_data());
				}
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}

}

1;