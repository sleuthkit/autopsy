#-----------------------------------------------------------
# identities.pl
#   
# 
# Change history
#   20151211 - created
#
# References
#  https://www.fireeye.com/blog/threat-research/2015/12/fin1-targets-boot-record.html
#
# Copyright 2015 QAR LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package identities;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20151211);
my $VERSION = getVersion();

sub getDescr {}
sub getRefs {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts values from Identities key; NTUSER.DAT";
}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching identities v.".$VERSION);
  ::rptMsg("identities v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Identities";

	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
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
