#-----------------------------------------------------------
# fvestats.pl
#  Get BitLocker settings, including when it was enabled
#
# History:
#  20220704 - created
#
# References:
#  https://twitter.com/0gtweet/status/1418322629996564480
#  https://fptu-ethical-hackers-club.github.io/posts/ACSC2021-Forensics/
#  https://thedfirreport.com/2021/11/15/exchange-exploit-leads-to-domain-wide-ransomware/
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package fvestats;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "impact",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              MITRE         => "T1486",  
              version       => 20220704);

sub getConfig{return %config}
sub getShortDescr {
	return "Get BitLocker settings (when enabled, etc.)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching fvestats v.".$VERSION);
	::rptMsg("fvestats v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	
	my $key_path = $ccs."\\Control\\FVEStats";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Keypath: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my ($t0,$t1) = unpack("VV",$key->get_value("OsvEncryptInit")->get_data());
			my $t = ::getTime($t0,$t1);
			::rptMsg("OsvEncryptInit     : ".::format8601Date($t)."Z");
		};
		
		eval {
			my ($t0,$t1) = unpack("VV",$key->get_value("OsvEncryptComplete")->get_data());
			my $t = ::getTime($t0,$t1);
			::rptMsg("OsvEncryptComplete : ".::format8601Date($t)."Z");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: In July, 2021, the Hades Ransomware was reportedly observed using BitLocker to encrypt devices.");
	::rptMsg("As such, these artifacts may be useful in determining a timeline of activity, or developing pivot points for analysis.");
#	::rptMsg("");
}

1;