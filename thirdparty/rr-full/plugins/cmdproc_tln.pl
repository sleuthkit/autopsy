#-----------------------------------------------------------
# cmdproc_tln.pl
# Checks key for files to autostart from cmd.exe
#
# Change History
#   20130425 - created
#
# References:
#   
# Category: autostart,malware,programexecution 
#
# copyright 2013 Quantum Analytics Research,
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cmdproc_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130425);

sub getConfig{return %config}

sub getShortDescr {
	return "Autostart - get Command Processor\\AutoRun value from NTUSER\.DAT hive (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching cmdproc_tln v.".$VERSION);
#	::rptMsg("cmdproc v.".$VERSION); # banner
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Software\\Microsoft\\Command Processor";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
	  my $lw = $key->get_timestamp();
		my $auto;
		eval {
			$auto = $key->get_value("AutoRun")->get_data();
#			::rptMsg("AutoRun = ".$auto);
#			::alertMsg("ALERT: cmdproc: ".$key_path." AutoRun value found: ".$auto);
			::alertMsg($lw."|ALERT|||HKCU\\".$key_path." AutoRun value found: ".$auto);
		};
		if ($@) {
#			::rptMsg("AutoRun value not found.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}
1;