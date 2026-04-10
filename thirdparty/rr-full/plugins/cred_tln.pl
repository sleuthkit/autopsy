#-----------------------------------------------------------
# cred.pl
# This plugin checks for the existence (and setting) of the UseLogonCredential value in
# the system hive.  Because there is very little need to modify values beneath this key 
# under normal circumstances, the key LastWrite time is assumed to be associated with the
# value being created and set (or changed to another value, as the case may be)
# 
#
# References:
#  <included inline>
#
# Change history:
#  20200730 - MITRE ATT&CK Updates
#  20200402 - created
#
#  https://attack.mitre.org/techniques/T1112/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cred_tln;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "tln",
              MITRE         => "T1112",
              category      => "malware",
              version       => 20200730);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks UseLogonCredential value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
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
			my $lw = $key->get_timestamp();
			::rptMsg($lw."|REG|||[T1112] WDigest UseLogonCredential value = ".$ulc);
#			::rptMsg("The UseLogonCredential value set to \'1\' indicates that credentials are stored in memory in plain text.")
		}
	};
#	::rptMsg("UseLogonCredential value not found.") if ($@);
}
1;