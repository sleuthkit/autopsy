#-----------------------------------------------------------
# calibrator.pl
#   
# Change history
#   20200904 - MITRE updates
#   20200427 - changed output date format
#   20200416 - created
#
# Refs:
#   https://twitter.com/f0wlsec/status/1203118495699013633
#   https://attack.mitre.org/techniques/T1548/002/
#
# Copyright (c) 2020 QAR,LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package calibrator;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1548\.002",
              category      => "persistence",
              version       => 20200904);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {return "Checks DisplayCalibrator value (possible bypass assoc with LockBit ransomware)";}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching calibrator v.".$VERSION);
	::rptMsg("calibrator v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());   
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = 'Microsoft\\Windows NT\\CurrentVersion\\ICM\\Calibration';
	
	if ($key = $root_key->get_subkey($key_path)) {
		if (my $dc = $key->get_value("DisplayCalibrator")) {
			if (my $dc2 = $dc->get_data()) {
				::rptMsg($key_path);
				::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
				::rptMsg("DisplayCalibrator value: ".$dc2);
				::rptMsg("");
				::rptMsg("Analysis Tip: Most often, the DisplayCalibrator value points to system32\\DCCW\.EXE.  If the ");
				::rptMsg("current value points to something else, an investigation may be in order.");
			}
		}
	}
}
1;