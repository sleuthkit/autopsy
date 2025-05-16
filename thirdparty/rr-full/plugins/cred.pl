#-----------------------------------------------------------
# cred.pl
#
# 
#
# References:
#  <included inline>
#
# Change history:
#  20200730 - MITRE ATT&CK updates
#  20200427 - updated output date format
#  20200402 - created
#
#  https://attack.mitre.org/techniques/T1112/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cred;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1112",
              category      => "defense evasion",
              version       => 20200730);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for UseLogonCredential value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching cred v.".$VERSION);
	::rptMsg("cred v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path;
	my $key;

# System Hive
# First, need to get the value for the CurrentControlSet
	my $ccs;
	my $current;
	if ($key = $root_key->get_subkey("Select")) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
	}
# https://www.praetorian.com/blog/mitigating-mimikatz-wdigest-cleartext-credential-theft?edition=2019
	$key_path = $ccs."\\Control\\SecurityProviders\\WDigest";
	eval {
		if ($key = $root_key->get_subkey($key_path)){
			my $ulc = $key->get_value("UseLogonCredential")->get_data();
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("  UseLogonCredential value = ".$ulc);
			::rptMsg("The UseLogonCredential value set to \'1\' indicates that credentials are stored in memory in plain text.")
		}
	};
	::rptMsg("UseLogonCredential value not found.") if ($@);
}
1;