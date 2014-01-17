#-----------------------------------------------------------
# landesk.pl 
# parses LANDESK Monitor Logs
#
#
# Change history
#   20130326 - added Wow6432Node path
#   20130214 - updated w/ Logon info
#   20090729 - updates, H. Carvey
#
# Orignal copyright 2009 Don C. Weber
# Updated copyright 2013 QAR, LLC
#-----------------------------------------------------------
package landesk;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130326);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of programs monitored by LANDESK - Software hive";	
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
	::logMsg("Launching landesk v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# updated added 20130326  
  my @paths = ("LANDesk\\ManagementSuite\\WinClient\\SoftwareMonitoring\\MonitorLog",
               "Wow6432Node\\LANDesk\\ManagementSuite\\WinClient\\SoftwareMonitoring\\MonitorLog");
    
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("");
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					eval {
						my $lw = $s->get_timestamp();
# Push the data into a hash of arrays 
						push(@{$ls{$lw}},$s->get_name());
					};
				}
			
				foreach my $t (reverse sort {$a <=> $b} keys %ls) {
					::rptMsg(gmtime($t)." (UTC)");
					foreach my $item (@{$ls{$t}}) {
						::rptMsg("  $item");
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
	
# update added 20130327
	my @paths = ("LANDesk\\Inventory\\LogonHistory\\Logons",
	             "Wow6432Node\\LANDesk\\Inventory\\LogonHistory\\Logons");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");	
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
			::rptMsg("");	
		
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					::rptMsg($data."  Logon: ".gmtime($name));
				}
			
			}
			else {
				::rptMsg($key_path." has not values\.");
			}
		}
		else {
			::rptMsg($key_path." not found\.");
		}
	}	
}

1;