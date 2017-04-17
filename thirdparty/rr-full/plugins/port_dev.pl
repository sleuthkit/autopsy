#-----------------------------------------------------------
# port_dev
# Parse Microsoft\Windows Portable Devices\Devices key on Vista
# Get historical information about drive letter assigned to devices
#
# NOTE: Credit for "discovery" goes to Rob Lee
#
# Change History:
#  20090118 - changed the name of the plugin from "removdev"
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package port_dev;
use strict;

my %config = (hive          => "Software",
              osmask        => 192,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090118);

sub getConfig{return %config}

sub getShortDescr {
	return "Parses Windows Portable Devices key (Vista)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching port_dev v.".$VERSION);
	::rptMsg("port_dev v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows Portable Devices\\Devices";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("RemovDev");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lastwrite = $s->get_timestamp();
				
				my $letter;
				eval {
					$letter = $s->get_value("FriendlyName")->get_data();
				};
				::rptMsg($name." key error: $@") if ($@);
				
				my $half;
				if (grep(/##/,$name)) {
					$half = (split(/##/,$name))[1];
				}
					
				if (grep(/\?\?/,$name)) {
					$half = (split(/\?\?/,$name))[1];
				}
			
				my ($dev,$sn) = (split(/#/,$half))[1,2];
				
				::rptMsg("Device    : ".$dev);
				::rptMsg("LastWrite : ".gmtime($lastwrite)." (UTC)");
				::rptMsg("SN        : ".$sn);				
				::rptMsg("Drive     : ".$letter);
				::rptMsg("");
				
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;