#-----------------------------------------------------------
# disablesr.pl
# 	Gets the value that turns System Restore either on or off
#
# Change History
#   20200515 - updated date output format
#   20120914 - created
#
# References
# 	Registry Keys and Values for the System Restore Utility http://support.microsoft.com/kb/295659
#
#	https://attack.mitre.org/techniques/T1562/001/
#
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package disablesr;
use strict;

my %config = (hive          => "Software",
              MITRE         => "T1562\.001",
              category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20200911);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets the value that turns System Restore either on or off";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disablesr v.".$VERSION);
	::rptMsg("disablesr v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\SystemRestore";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my $disable;
		eval {
			$disable = $key->get_value("DisableSR")->get_data();
		};
		if ($@) {
			::rptMsg("DisableSR value not found.");
		}
		else {
			::rptMsg("DisableSR = ".$disable);
			::rptMsg("");
			::rptMsg("1 means System Restore is turned off");
		}
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;