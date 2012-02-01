#-----------------------------------------------------------
# taskman.pl
# Get Taskman value from Winlogon
#
# References
#   http://www.geoffchappell.com/viewer.htm?doc=notes/windows/shell/explorer/
#          taskman.htm&tx=3,5-7,12;4&ts=0,19
#   http://technet.microsoft.com/en-us/library/cc957402.aspx
#
# Change History: 
#   20091116 - created
#   
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package taskman;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091116);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets Taskman from HKLM\\..\\Winlogon";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching taskman v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
	if (my $key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		
		eval {
			::rptMsg("");
			my $task = $key->get_value("Taskman")->get_data();
			::rptMsg("Taskman value = ".$task);
		};
		if ($@) {
		  ::rptMsg("Taskman value not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;