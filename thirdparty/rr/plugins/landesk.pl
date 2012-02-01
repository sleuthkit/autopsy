#-----------------------------------------------------------
# LANDESK Monitor Logs
#
#
# Change history
#   20090729 - updates, H. Carvey
#
# copyright 2009 Don C. Weber
#-----------------------------------------------------------
package landesk;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090729);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of programs monitored by LANDESK from Software hive file";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %ls;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching LANDESK v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
    
	my $key_path = "LANDesk\\ManagementSuite\\WinClient\\SoftwareMonitoring\\MonitorLog";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				eval {
					my ($val1,$val2) = unpack("VV",$s->get_value("Last Started")->get_data());
# Push the data into a hash of arrays 
					push(@{$ls{::getTime($val1,$val2)}},$s->get_name());
				};
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %ls) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$ls{$t}}) {
					::rptMsg("\t$item");
				}
			}
		}
		else {
			::rptMsg($key_path." does not appear to have any subkeys.")
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;