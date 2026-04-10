#-----------------------------------------------------------
# ua_wiper.pl 
# Settings associated with wiper found targeting Ukraine
#
# Change history
#  20220301 - created
#
# References
#   https://twitter.com/0xAmit/status/1496646517205221376
#   https://renenyffenegger.ch/notes/Windows/registry/tree/HKEY_CURRENT_USER/Software/Microsoft/Windows/CurrentVersion/Explorer/Advanced/index
#	https://www.crowdstrike.com/blog/how-crowdstrike-falcon-protects-against-wiper-malware-used-in-ukraine-attacks/
#   https://blog.malwarebytes.com/threat-intelligence/2022/03/hermeticwiper-a-detailed-analysis-of-the-destructive-malware-that-targeted-ukraine/
# 
# copyright 2022 QAR,LLC
# author: H. Carvey keydet89@yahoo.com
#-----------------------------------------------------------
package ua_wiper;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "defense evasion",
              MITRE         => "T1562\.001", 
			  output		=> "report",
              version       => 20220301);

sub getConfig{return %config}
sub getShortDescr {
	return "Settings associated with wiper found in the Ukraine";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching ua_wiper v.".$VERSION);
	::rptMsg("ua_wiper v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced';
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		eval {
			my $c = $key->get_value("ShowCompColor")->get_data();
			::rptMsg($key_path." ShowCompColor value = ".$c);
		};
		
		eval {
			my $c = $key->get_value("ShowInfoTip")->get_data();
			::rptMsg($key_path." ShowInfoTip value   = ".$c);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Amit Serper found that, when analyzing a wiper deployed against Ukraine following the");
	::rptMsg("Russian invasion in 2022, the malware set these values to \"0\". Crowdstrike analysis of the malware");
	::rptMsg("indicates that there can be delays set when launching the EXE, so these settings may prevent the user ");
	::rptMsg("from seeing anything untoward had gone on.");
	::rptMsg("");
	::rptMsg("Ref: https://www.crowdstrike.com/blog/how-crowdstrike-falcon-protects-against-wiper-malware-used-in-ukraine-attacks/");
}

1;