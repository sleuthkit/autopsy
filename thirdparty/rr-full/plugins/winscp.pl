#-----------------------------------------------------------
# winscp.pl
# 
#
# Change history
#  20200916 - MITRE updates
#  20200525 - updated date output format
#  20140203 - created
#
# References
#
# 
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winscp;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "lateral movement",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1021",
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's WinSCP 2 data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching winscp v.".$VERSION);
	::rptMsg("winspc v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Martin Prikryl\\WinSCP 2';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
# CDCache
		eval {
			::rptMsg("Configuration\\CDCache");
			my @vals = $key->get_subkey("Configuration\\CDCache")->get_list_of_values();
			foreach my $v (@vals) {
				::rptMsg("Value: ".$v->get_name());
				::rptMsg("Data : ".$v->get_data());
			}
			::rptMsg("");
		};

# \Configuration\History\RemoteTarget
		eval {
			::rptMsg("Configuration\\History\\RemoteTarget");
			my @vals = $key->get_subkey("Configuration\\History\\RemoteTarget")->get_list_of_values();
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." ".$v->get_data());
			}
			::rptMsg("");
		};

#		CMI-CreateHive{D43B12B8-09B5-40DB-B4F6-F6DFEB78DAEC}\Software\Martin Prikryl\WinSCP 2\SshHostKeys
		
		eval {
			::rptMsg("SshHostKeys");
			my @vals = $key->get_subkey("SshHostKeys")->get_list_of_values();
			foreach my $v (@vals) {
				::rptMsg("Value: ".$v->get_name());
				::rptMsg("Data : ".$v->get_data());
			}
			::rptMsg("");
		};

	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;