#-----------------------------------------------------------
# staginginfo.pl
# Plugin to get info regarding CD burning
#  
#
# Change history
#  20210407 - created
#
# References
#		https://secureartisan.wordpress.com/2012/06/04/windows-7-cddvd-burning/
#   https://attack.mitre.org/techniques/T1074/001/
#
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package staginginfo;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "collection",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1074\.001",
			  output		=> "report",
              version       => 20210407);

sub getConfig{return %config}
sub getShortDescr {
	return "Get info regarding CD burning";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching staginginfo v.".$VERSION);
	::rptMsg("staginginfo v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\CD Burning\\StagingInfo';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("Drive         : ".$s->get_name());
				::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
				
				eval {
					::rptMsg("StagingPath   : ".$s->get_value("StagingPath")->get_data());
				};
				
				eval {
					::rptMsg("Active        : ".$s->get_value("Active")->get_data());
				};
				
				eval {
					::rptMsg("DriveNumber   : ".$s->get_value("DriveNumber")->get_data());
				};
				
				::rptMsg("");
			}
		}
		::rptMsg("Analysis Tip: Information from this plugin provides insight into the use of Windows Explorer to burn CDs, and");
		::rptMsg("should be correlated with other host-based data to develop greater context.");
	}
	else {
		::rptMsg($key_path." key not found.");
	}
}

1;