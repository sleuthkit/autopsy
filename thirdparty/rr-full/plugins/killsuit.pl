#-----------------------------------------------------------
# killsuit
#
# Change history:
#  20201005 - MITRE update
#  20200427 - updated output date format
#  20200414 - created
# 
# Ref:
#  https://img.en25.com/Web/FSecure/%7B1d240f2a-dcbb-4b0c-9da9-e27a283aed02%7D_2019-07-23-FSecure-Whitepaper-Killsuit-01.pdf
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package killsuit;
use strict;

my %config = (hive          => "Software",
			        category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output 		=> "report",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Check for indications of Danderspritz Killsuit installation";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching killsuit v.".$VERSION);
	::rptMsg("killsuit v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $key_path = ('Microsoft\\Windows\\CurrentVersion\\OemMgmt');
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		::rptMsg("Analysis Tip: Danderspitz KillSuit is known to store hashes beneath this key.  This may be an indicator that KillSuit is");
		::rptMsg("installed on the system.");
		::rptMsg("Ref: https://img.en25.com/Web/FSecure/%7B1d240f2a-dcbb-4b0c-9da9-e27a283aed02%7D_2019-07-23-FSecure-Whitepaper-Killsuit-01\.pdf");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;