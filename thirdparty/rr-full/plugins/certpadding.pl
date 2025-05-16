#-----------------------------------------------------------
# certpadding.pl
# Check EnableCertPaddingCheck value
#
# Change history:
#   20220110 - created
#
# References:
#   https://research.checkpoint.com/2022/can-you-trust-a-files-digital-signature-new-zloader-campaign-exploits-microsofts-signature-verification-putting-users-at-risk/
#   https://docs.microsoft.com/en-us/security-updates/SecurityAdvisories/2014/2915720?redirectedfrom=MSDN
#   
#        
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, 2022
#-----------------------------------------------------------
package certpadding;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1562",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              version       => 20220110);

sub getConfig{return %config}

sub getShortDescr {
	return "Check EnableCertPaddingCheck value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching certpadding v.".$VERSION);
	::rptMsg("certpadding v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Cryptography\\WintrustConfig",
	             "Wow6432Node\\Microsoft\\Cryptography\\WintrustConfig");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path      : ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			
			eval {
				my $cert = $key->get_value("EnableCertPaddingCheck")->get_data();
				::rptMsg("EnableCertPaddingCheck value: ".$cert);
				::rptMsg("0 - disabled (default)");
				::rptMsg("1 - enabled");
			};
			::rptMsg("EnableCertPaddingCheck value not found\. Functionality not enabled\.") if ($@);
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: MS13-098 provided checks for certificate padding, but that functionality was shifted to an opt-in");
	::rptMsg("approach based on the impact to business functionality\. The Checkpoint ZLoader article from 5 Jan 2022 illustrates");
	::rptMsg("that this functionality can be exploited if the capability is not fully enabled, via the Registry value.");
	::rptMsg("");
	::rptMsg("Ref: https://research.checkpoint.com/2022/can-you-trust-a-files-digital-signature-new-zloader-campaign-exploits-microsofts-signature-verification-putting-users-at-risk/");
}
1;