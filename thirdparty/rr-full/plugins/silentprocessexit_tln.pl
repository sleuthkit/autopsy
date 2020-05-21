#-----------------------------------------------------------
# silentprocessexit_tln
#
# Change history:
#  20180601 - created
# 
# Ref:
#  https://oddvar.moe/2018/04/10/persistence-using-globalflags-in-image-file-execution-options-hidden-from-autoruns-exe/
#
# copyright 2018 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package silentprocessexit_tln;
use strict;

my %config = (hive          => "Software",
							category      => "autostart",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180601);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of SilentProcessExit key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#	::rptMsg("Launching silentProcessexit v.".$VERSION);
#	::rptMsg("silentprocessexit v.".$VERSION); # banner
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $key_path = ('Microsoft\\Windows NT\\CurrentVersion\\SilentProcessExit');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @sk = $key->get_list_of_subkeys();
		foreach my $s (@sk) {
			
			my $mon;
			eval {
				$mon = $s->get_value("MonitorProcess")->get_data();
			};
			::rptMsg($s->get_timestamp()."|REG|||SilentProcessExit: ".$s->get_name()." - MonitorProcess: ".$mon);
		}
	}
}
1;