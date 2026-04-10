#-----------------------------------------------------------
# kdc.pl
#
# History:
#  20210312 - created
#
# References:
#  https://twitter.com/PyroTek3/status/1336720280316760066
#  https://support.microsoft.com/en-us/topic/kb4598347-managing-deployment-of-kerberos-s4u-changes-for-cve-2020-17049-569d60b7-3267-e2b0-7d9b-e46d770332ab
#  https://msrc.microsoft.com/update-guide/vulnerability/CVE-2020-17049
#
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package kdc;
use strict;

my %config = (hive          => "System",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562",  
			  output		=> "report",
              version       => 20210312);

sub getConfig{return %config}
sub getShortDescr {
	return "Get values related to \"Bronze Bit\" from KDC Service key";	
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
	::rptMsg("Launching kdc v.".$VERSION);
	::rptMsg("kdc v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $current;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\Kdc";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $n = $key->get_value("NonForwardableDelegation")->get_data();
			::rptMsg("NonForwardableDelegation value: ".$n);
			
		};
		
		eval {
			my $n = $key->get_value("PerformTicketSignature")->get_data();
			::rptMsg("PerformTicketSignature value: ".$n);
			::rptMsg("");
			::rptMsg("0: Disables Kerberos Signatures");
			::rptMsg("1: Enables Deployment Mode");
			::rptMsg("2: Enables Enforcement Mode");
			::rptMsg("Ref: https://support.microsoft.com/en-us/topic/kb4598347-managing-deployment-of-kerberos-s4u-changes-for-cve-2020-17049-569d60b7-3267-e2b0-7d9b-e46d770332ab");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;