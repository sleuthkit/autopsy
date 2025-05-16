#-----------------------------------------------------------
# watp
#
# Change history:
#  20200916 - MITRE updates
#  20200427 - updated output date format
#  20190506 - created
# 
# Ref:
#  
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package watp;
use strict;

my %config = (hive          => "Software",
		      category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of Windows Advanced Threat Protection key";	
}
sub getDescr{}
sub getRefs {
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching watp v.".$VERSION);
	::rptMsg("watp v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $key_path = ('Microsoft\\Windows Advanced Protection');
	         
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		
		my @vals = $key->get_list_of_values();
		foreach my $v (@vals) {
			::rptMsg($v->get_name()."  ".$v->get_data());
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;