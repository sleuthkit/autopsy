#-----------------------------------------------------------
# pendinggpos.pl
#  
#
# Change history
#  20200922 - MITRE update
#  20200427 - updated output date format
#  20191020 - created
#
# References
#  https://forums.juniper.net/t5/Threat-Research/New-Gootkit-Banking-Trojan-variant-pushes-the-limits-on-evasive/ba-p/319055
# 
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package pendinggpos;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "persistence",
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1547",
              version       => 20200922);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's PendingGPOs key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching pendinggpos v.".$VERSION);
	::rptMsg("pendinggpos v.".$VERSION); 
	::rptMsg(getHive()." - ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\IEAK\\GroupPolicy\\PendingGPOs';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) { 
				::rptMsg(sprintf "%-30s %-10s",$v->get_name(),$v->get_data());
			}
			::rptMsg("");
			::rptMsg("Analysis Tip: This is a persistence technique observed with Gootkit; look for suspicious values.");
			::rptMsg("https://blogs.juniper.net/en-us/threat-research/new-gootkit-banking-trojan-variant-pushes-the-limits-on-evasive-behavior");
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;