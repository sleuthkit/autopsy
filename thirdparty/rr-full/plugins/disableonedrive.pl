#-----------------------------------------------------------
# disableonedrive.pl
#
# Change history:
#   20220614 - created
#
# References:
#   https://support.microsoft.com/en-us/office/onedrive-won-t-start-0c158fa6-0cd8-4373-98c8-9179e24f10f2
#   
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package disableonedrive;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1562\.001",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20220614);

sub getConfig{return %config}

sub getShortDescr {
	return "Check DisableFileSyncNGSC value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disableonedrive v.".$VERSION);
	::rptMsg("disableonedrive v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
    ::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	 
	my $key; 
	my $key_path = "Policies\\Microsoft\\Windows\\OneDrive";
 	if ($key = $root_key->get_subkey($key_path)) {
 		eval {
 			my $c = $key->get_value("DisableFileSyncNGSC")->get_data();
			::rptMsg("DisableFileSyncNGSC value: ".$c);
 			::rptMsg("");
			::rptMsg("Analysis Tip: The DisableFileSyncNGSC set to \"1\" will disable OneDrive.");
 		};
 		::rptMsg($key_path."\\DisableFileSyncNGSC value not found.") if ($@);
 
 	}
 	else {
 		::rptMsg($key_path." not found.");
 	}
}
1;