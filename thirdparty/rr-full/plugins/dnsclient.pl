#-----------------------------------------------------------
# dnsclient.pl
#
# Change history:
#  20210504 - created
# 
# Ref:
#  https://tcm-sec.com/the-dangers-of-llmnr-nbt-ns/
#
# copyright 2021 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package dnsclient;
use strict;

my %config = (hive          => "software",
			  category      => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output        => "report",
              version       => 202010504);

sub getConfig{return %config}
sub getShortDescr {
	return "Check if LLMNR/NBT-NS is disabled";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching dnsclient v.".$VERSION);
	::rptMsg("dnsclient v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
#	::rptMsg("MITRE ATT&CK sub-technique T1546\.010");
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\Policies\\Microsoft\\Windows NT\\DNSClient";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			
		eval {
			my $m = $key->get_value("EnableMulticast")->get_value();
			::rptMsg("EnableMulticast value: ".$m);
		};	
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: An \"EnableMulticast\" value of 0 disables LLMNR/NBT-NS, which are alternate methods of host ID");
	::rptMsg("if DNS resolution fails, and can be used to collect password hashes, or relay credentials.");
}
1;