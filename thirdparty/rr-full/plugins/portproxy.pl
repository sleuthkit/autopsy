#-----------------------------------------------------------
# portproxy.pl
# Check port proxy settings, set via netsh; look for potential tunneling activity
#
# History:
#  20200929 - minor updates
#  20200909 - created
#
# References:
#		https://www.fireeye.com/blog/threat-research/2019/01/bypassing-network-restrictions-through-rdp-tunneling.html
#		http://www.dfirnotes.net/portproxy_detection/
# 
#	https://attack.mitre.org/techniques/T1572/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package portproxy;
use strict;

my %config = (hive          => "System",
			  output        => "report",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1572",  
              version       => 20200929);

sub getConfig{return %config}
sub getShortDescr {
	return "Check port proxy settings, set via netsh";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching portproxy v.".$VERSION);
	::rptMsg("portproxy v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $ccs = ::getCCS($root_key);
	my $key;
	my $key_path = $ccs."\\services\\PortProxy\\v4tov4\\tcp";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			::rptMsg(sprintf "%-25s %-25s","Listen IP/Port","Connect IP/Port");
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-25s %-25s",$v->get_name(),$v->get_data());			
			}
			::rptMsg("");
			::rptMsg("Analysis Tip: Entries may be an indication of the use of \"netsh\" to enable RDP tunneling.");
			::rptMsg("Ref: https://www.fireeye.com/blog/threat-research/2019/01/bypassing-network-restrictions-through-rdp-tunneling.html");
		}
	}
}

1;