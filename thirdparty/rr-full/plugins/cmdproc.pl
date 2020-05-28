#-----------------------------------------------------------
# cmdproc.pl
# Checks key for files to autostart from cmd.exe
#
# Change History
#   20190223 - added reference
#   20130425 - added alertMsg() functionality
#   20130115 - created
#
# References:
#   https://unit42.paloaltonetworks.com/new-babyshark-malware-targets-u-s-national-security-think-tanks/
#
# Category: autostart,malware,programexecution 
#
# copyright 2013 Quantum Analytics Research,
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cmdproc;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20190223);

sub getConfig{return %config}

sub getShortDescr {
	return "Autostart - get Command Processor\\AutoRun value from NTUSER\.DAT hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching cmdproc v.".$VERSION);
	::rptMsg("cmdproc v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Software\\Microsoft\\Command Processor";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my $auto;
		eval {
			$auto = $key->get_value("AutoRun")->get_data();
			::rptMsg("AutoRun = ".$auto);
			::alertMsg("ALERT: cmdproc: ".$key_path." AutoRun value found: ".$auto);
		};
		if ($@) {
			::rptMsg("AutoRun value not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;