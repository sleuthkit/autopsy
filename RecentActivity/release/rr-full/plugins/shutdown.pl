#-----------------------------------------------------------
# shutdown.pl
# Plugin for Registry Ripper; Access System hive file to get the
# contents of the ShutdownTime value
# 
# Change history
#
#
# References
#   
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package shutdown;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets ShutdownTime value from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shutdown v.".$VERSION);
	::rptMsg("shutdown v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		my $win_path = $ccs."\\Control\\Windows";
		my $win;
		if ($win = $root_key->get_subkey($win_path)) {
			::rptMsg($win_path." key, ShutdownTime value");
			::rptMsg($win_path);
			::rptMsg("LastWrite Time ".gmtime($win->get_timestamp())." (UTC)");
			my $sd;
			if ($sd = $win->get_value("ShutdownTime")->get_data()) {
				my @vals = unpack("VV",$sd);
				my $shutdown = ::getTime($vals[0],$vals[1]);
				::rptMsg("  ShutdownTime = ".gmtime($shutdown)." (UTC)");
				
			}
			else {
				::rptMsg("ShutdownTime value not found.");
			}
		}
		else {
			::rptMsg($win_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;