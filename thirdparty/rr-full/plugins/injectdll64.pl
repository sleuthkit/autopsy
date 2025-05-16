#-----------------------------------------------------------
# injectdll64.pl
#   Analysis provided at the SneakyMonkey site indicates that when the injectDll64 Trickbot
#   module is run, the CertificateTransparencyEnforcementDisabledForUrls key is populated in
#   order to weaken Chrome security - NOTE: this may be unique to one variant of the module
#  
# Change history
#   20200911 - MITRE updates
#   20200427 - updated output date format
#   20200410 - created
#
# References
#   https://sneakymonkey.net/2019/05/22/trickbot-analysis/
#   https://getadmx.com/HKCU/Software/Policies/Google/Chrome/CertificateTransparencyEnforcementDisabledForUrls
#   https://www.chromium.org/administrators/policy-list-3#CertificateTransparencyEnforcementDisabledForUrls
#
# Copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package injectdll64;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "malware",
			  output		=> "report",
              version       => 20200911);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Retrieve values set to weaken Chrome security";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching injectdll64 v.".$VERSION);
  ::rptMsg("injectdll64 v.".$VERSION);
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");   
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_name = "CertificateTransparencyEnforcementDisabledForUrls";
	my @paths = ("Software\\Policies\\Google\\Chrome\\".$key_name,
	             "Policies\\Google\\Chrome\\".$key_name);
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");

			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					::rptMsg("  ".$v->get_name()." : ".$v->get_data());
				}
			} else {
				::rptMsg($key_path." found, has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	} 
}
1;
