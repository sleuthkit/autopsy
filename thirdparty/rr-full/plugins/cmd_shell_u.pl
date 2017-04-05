#-----------------------------------------------------------
# cmd_shell_u
# Get the shell\open\command settings for various file types; gets
#  info from USRCLASS.DAT hives, where Classes data is maintained on
#  Win7
#
# Change History
#   20130405 - created
#
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cmd_shell_u;
use strict;

my %config = (hive          => "USRCLASS\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130405);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets shell open cmds for various file types from USRCLASS\.DAT";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching cmd_shell_u v.".$VERSION);
	::rptMsg("cmd_shell_u v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my @shells = ("\.exe","exefile","ftp","http","https");
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $sh (@shells) {
		my $key_path = $sh."\\shell\\open\\command";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#			::rptMsg("");
			my $val;
			eval {
				$val = $key->get_value("")->get_data();
				::rptMsg("  Cmd: ".$val);
				::rptMsg("");
			};
			::rptMsg("Error: ".$@) if ($@);
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
}
1;