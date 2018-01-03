#-----------------------------------------------------------
# trappoll.pl
#   There are indications that the contents of this value may be associated
#   with a number of different malware variants.
#
# History
#   20120305 - created
#   
# References
#   http://home.mcafee.com/VirusInfo/VirusProfile.aspx?key=903224#none
#
# copyright 2012, Quantum Analytics Research, LLC
#-----------------------------------------------------------
package trappoll;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20120305);

sub getConfig{return %config}

sub getShortDescr {
	return "Get TrapPollTimeMilliSecs value, if found";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %clsid;
	::logMsg("Launching trappoll v.".$VERSION);
	::rptMsg("Launching trappoll v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\RFC1156Agent\\CurrentVersion\\Parameters";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		if ($key->get_value("TrapPollTimeMilliSecs")) {
			my $val = $key->get_value("TrapPollTimeMilliSecs")->get_data();
			::rptMsg(sprintf "TrapPollTimeMilliSecs = 0x%x (".$val.")", $val);
		}
		else {
			::rptMsg("Value not found.");
		}
	}
	else {
		::rptMsg($key_path." key not found.");
	}
}
1;