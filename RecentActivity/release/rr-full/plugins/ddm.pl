#-----------------------------------------------------------
# ddm.pl
#
# History:
#   20081129 - created
#
# Note - Not really sure what this is for or could be used for, other
#        than to show devices that had been connected to the system
#
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ddm;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081129);

sub getConfig{return %config}

sub getShortDescr {
	return "Get DDM data from Control Subkey";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching ddm v.".$VERSION);
	::rptMsg("ddm v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;

		my $key_path = $ccs."\\Control\\DDM";
		my $key;
		my %dev;
		if ($key = $root_key->get_subkey($key_path)) {
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
					my $tag = (split(/\./,$name,2))[1];
					$dev{$tag}{timestamp} = $s->get_timestamp();
					eval {
						$dev{$tag}{make} = $s->get_value("MakeName")->get_data();
						$dev{$tag}{model} = $s->get_value("ModelName")->get_data();
					};
				}
				foreach my $d (sort keys %dev) {
					::rptMsg(gmtime($dev{$d}{timestamp})."Z  Device\.".$d."  ".$dev{$d}{make}." ".$dev{$d}{model});
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
#			::logMsg($key_path." not found.");
		}
	}
	else {
		::logMsg("Current value not found.");
	}
}
1;