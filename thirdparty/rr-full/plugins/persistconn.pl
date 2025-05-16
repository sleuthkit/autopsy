#-----------------------------------------------------------
# persistconn.pl
# 
#
# Change history
#   20230109 - created
#
# References
#   https://jeffpar.github.io/kbarchive/kb/168/Q168148/
#
#
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package persistconn;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              category      => "persistence",
              MITRE         => "T1547\.015",
              version       => 20230109);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Persistent Connections values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching persistconn v.".$VERSION);
	::rptMsg("persistconn v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr());
	::rptMsg("MITRE: ".$config{category}." (".$config{MITRE}.")");
	::rptMsg("");	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Network\\Persistent Connections";
	
	if (my $key = $root_key->get_subkey($key_path)) {
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-15s %-45s",$v->get_name(),$v->get_data());
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." key not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Network connections can be persisted by choosing the \"net use /persistent:yes\" command, or by choosing ");
	::rptMsg("\"Reconnect at Logon\" in the Map Network Drive dialog; both allow mapped drives to be reconnected at logon. Look for ");
	::rptMsg("suspicious or unintended connections. Note that File and Printer Sharing needs to be enabled, as well.");
	::rptMsg("");
	::rptMsg("Ref: https://gegeek.com/networking/mapped-drives/");
}

1;