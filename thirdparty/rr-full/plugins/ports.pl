#-----------------------------------------------------------
# ports.pl
#
# History:
#  20210309 - created
#
# References:
#   https://techcommunity.microsoft.com/t5/microsoft-defender-for-endpoint/investigating-the-print-spooler-eop-exploitation/ba-p/2166463
#   https://msrc.microsoft.com/update-guide/en-US/vulnerability/CVE-2020-1048
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ports;
use strict;

my %config = (hive          => "Software",
			  category      => "privilege escalation",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1068",  
			  output		=> "report",
              version       => 20210309);

sub getConfig{return %config}
sub getShortDescr {
	return "Check port assignments";	
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
	::logMsg("Launching ports v.".$VERSION);
	::rptMsg("ports v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");my $key;
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Ports";
	if ($key = $root_key->get_subkey($key_path)) {
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-15s %-20s",$v->get_name(),$v->get_data());
			}
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Printer ports can be exploited to elevate privileges; look for unusual/suspicious ports.");
	::rptMsg("Ref: https://msrc.microsoft.com/update-guide/en-US/vulnerability/CVE-2020-1048");
}

1;