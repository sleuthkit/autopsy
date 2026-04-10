#-----------------------------------------------------------
# credentialsdelegation.pl
# 
#
# Change history:
#   20220307 - created
#
# References:
#   https://www.ired.team/offensive-security/credential-access-and-credential-dumping/dumping-delegated-default-kerberos-and-ntlm-credentials-without-touching-lsass
#   https://www.stigviewer.com/stig/windows_paw/2017-11-21/finding/V-78161
#        
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package credentialsdelegation;
use strict;

my %config = (hive          => "software",
			  category      => "credential access",
			  MITRE         => "T1555\.004",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20220307);

sub getConfig{return %config}

sub getShortDescr {
	return "Get CredentialsDelegation values";	
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
	::logMsg("Launching credentialsdelegation v.".$VERSION);
	::rptMsg("credentialsdelegation v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Policies\\Microsoft\\Windows\\CredentialsDelegation";
	my $key = ();
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
# start by getting key values		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-35s %-5s",$v->get_name(),$v->get_data());
			}
		}
		::rptMsg("");
# process subkeys
		my @sk = ("AllowDefCredentialsWhenNTLMOnly","AllowDefaultCredentials");
		foreach my $s (@sk) {
			if (my $k = $key->get_subkey($s)) {
				::rptMsg($key_path."\\".$s);
				::rptMsg("LastWrite time: ".::format8601Date($k->get_timestamp())."Z");
				::rptMsg("");
				my @vals = $k->get_list_of_values();
				if (scalar @vals > 0) {
					foreach my $v (@vals) {
						::rptMsg(sprintf "%-10s %-50s",$v->get_name(),$v->get_data());
					}
				}
				::rptMsg("");
			}
		}
		::rptMsg("Analysis Tip: Restricted remote administration protects administrator accounts by ensuring that reusable credentials"); 
		::rptMsg("are not stored in memory on remote devices that could potentially be compromised. ");
		::rptMsg("");
		::rptMsg("Ref: https://www.ired.team/offensive-security/credential-access-and-credential-dumping/dumping-delegated-default-kerberos-and-ntlm-credentials-without-touching-lsass");
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;