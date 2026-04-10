#-----------------------------------------------------------
# identities.pl
#   
# 
# Change history
#   20200911 - MITRE updates
#   20200525 - updated date output format
#   20151211 - created
#
# References
#  https://www.fireeye.com/blog/threat-research/2015/12/fin1-targets-boot-record.html
#  	- file content saved to Registry values
#
#	https://attack.mitre.org/techniques/T1078/ - Valid Accounts
#
# Copyright 2020 QAR LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package identities;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1078",
              category      => "persistence",
			  output		=> "report",
              version       => 20200911);

my $VERSION = getVersion();

sub getDescr {}
sub getRefs {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts values from Identities key; NTUSER\.DAT";
}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching identities v.".$VERSION);
	::rptMsg("identities v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Identities";

	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				::rptMsg(sprintf "%-40s %-30s",$name,$v->get_data());
			}
		} 
		else {
			::rptMsg($key_path." has no values.");
		}
	} else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
}
1;
