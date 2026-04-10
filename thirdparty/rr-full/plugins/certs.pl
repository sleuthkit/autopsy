#-----------------------------------------------------------
# certs.pl
# 
#
# Change history
#  20220926 - created
#
# References
#  https://attack.mitre.org/techniques/T1553/
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package certs;
use strict;

my %config = (hive          => "software, ntuser\.dat",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              MITRE         => "T1553\.005",
              category      => "defense evasion",
              version       => 20220926);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for MOTW bypasses via certificates";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	::logMsg("Launching certs v.".$VERSION);
	::rptMsg("certs v.".$VERSION);
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
# Set paths
 	my @paths = ();
 	if ($hive_guess eq "software") {
 		@paths = ('Microsoft\\SystemCertificates\\Root\\Certificates',
				  'Policies\\SystemCertificates\\Root\\Certificates',
				  'Microsoft\\EnterpriseCertificates\\Root\\Certificates');
 	}
 	elsif ($hive_guess eq "ntuser") {
 		@paths = ('Software\\Microsoft\\SystemCertificates\\Root\\Certificates',
		          'Software\\Policies\\Microsoft\\SystemCertificates\\Root\\Certificates');
 	}
 	else {}
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) { 
					my $name = $v->get_name();
					my $data = $v->get_data();
					::rptMsg($name." - ".$data);
				}
			}
			else {
				::rptMsg($key_path." has no values.");
			}
			
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
#	::rptMsg("");
	::rptMsg("Analysis Tip: Trust relationships can be subverted by modifying/adding certificates. MS has a subset of root");
	::rptMsg("certificates that are consistent across systems. Check the reference for a list of those certificates, and ");
	::rptMsg("monitor systems for changes (per the reference).");
	::rptMsg("");
	::rptMsg("Ref: https://attack.mitre.org/techniques/T1553/");
}

1;