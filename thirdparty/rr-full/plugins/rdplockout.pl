#-----------------------------------------------------------
# rdplockout.pl
# Determine the RDP Port used
#
# History
#  20220809 - created
#
# References
#   https://docs.microsoft.com/en-us/troubleshoot/windows-server/networking/configure-remote-access-client-account-lockout
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package rdplockout;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1133",
              category      => "initial access",
			  output		=> "report",
              version       => 20220809);

sub getConfig{return %config}
sub getShortDescr {
	return "Queries System hive for RDP Lockout Settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;
	
	::logMsg("Launching rdplockout v.".$VERSION);
	::rptMsg("rdplockout v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\RemoteAccess\\Parameters\\AccountLockout";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("rdplockout v.".$VERSION);
		::rptMsg("");
		
		eval {
			my $max = $key->get_value("MaxDenials")->get_data();
			::rptMsg("MaxDenials = ".$max);
		};
		::rptMsg("Error getting MaxDenials value: ".$@) if ($@);
		
		
		eval {
			my $res = $key->get_value("ResetTime (mins)")->get_data();
			::rptMsg("ResetTime (mins) = ".$res);
		};
		::rptMsg("Error getting ResetTime (mins) value: ".$@) if ($@);
		::rptMsg("");
		::rptMsg("Analysis Tip: Values retrieved indicate account lockout settings for Remote Access.");
		::rptMsg("Also, look for a \"Domain Name:User Name\" value.");
		::rptMsg("");
		::rptMsg("Ref: https://docs.microsoft.com/en-us/troubleshoot/windows-server/networking/configure-remote-access-client-account-lockout");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1