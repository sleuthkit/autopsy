#-----------------------------------------------------------
# shutdowncount.pl
#
# *Value info first seen at:
#   http://forensicsfromthesausagefactory.blogspot.com/2008/06/install-dates-and-shutdown-times-found.html
#   thanks to DC1743@gmail.com
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shutdowncount;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080709);

sub getConfig{return %config}

sub getShortDescr {
	return "Retrieves ShutDownCount value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shutdowncount v.".$VERSION);
	::rptMsg("shutdowncount v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 	my $current;
 	my $ccs;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
	}
	else {
		::logMsg("Could not find ".$key_path);
		return
	}

	$key_path = $ccs."\\Control\\Watchdog\\Display";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("ShutdownCount");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $count = 0;
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				if ($v->get_name() eq "ShutdownCount") {
					$count = 1;
					::rptMsg("ShutdownCount = ".$v->get_data());
				}
			}
			::rptMsg("ShutdownCount value not found.") if ($count == 0);
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;
