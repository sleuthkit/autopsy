#-----------------------------------------------------------
# tgt.pl
# 
# Change history
#   20201116 - created
# 
# Reference: 
#   https://twitter.com/CyberRaiju/status/1243536444309807105
#
#  https://attack.mitre.org/techniques/T1558/003/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package tgt;

my %config = (hive          => "System",
              hasShortDescr => 1,
              category      => "credential access",
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1558\.003",
			  output		=> "report",
              version       => 20201116);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists allowtgtsessionkey value data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching tgt v.".$VERSION);
	::rptMsg("tgt v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key();
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		
		$key_path = $ccs.'\\Control\\LSA\\Kerberos\\Parameters';
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
				
			eval {
				my $admin = $key->get_value("allowtgtsessionkey")->get_data();
				::rptMsg("allowtgtsessionkey value = ".$admin);
			};
			::rptMsg("allowtgtsessionkey value not found.") if ($@);

			::rptMsg("");
			::rptMsg("Analysis Tip:");
			::rptMsg("- 0: The KerbRetrieveEncodedTicket will not include a session key that that allows this TGT to be used for login.");
			::rptMsg("- 1: Indicates that a session key should be returned with the TGT according to current behavior.");
			::rptMsg("Note: This approach is disabled with Windows 10 and Credential Guard.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;