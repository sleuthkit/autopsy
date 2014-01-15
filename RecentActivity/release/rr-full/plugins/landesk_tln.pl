#-----------------------------------------------------------
# landesk_tln.pl
# 
#
#
# Change history
#   20130214 - updated with Logon info
#   20090729 - updates, H. Carvey
#
# Original copyright 2009 Don C. Weber
# Updated copyright 2013 QAR, LLC
#-----------------------------------------------------------
package landesk_tln;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130214);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of programs monitored by LANDESK from Software hive";	
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
	::logMsg("Launching landesk (TLN) v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
    
	# updated added 20130326  
  my @paths = ("LANDesk\\ManagementSuite\\WinClient\\SoftwareMonitoring\\MonitorLog",
               "Wow6432Node\\LANDesk\\ManagementSuite\\WinClient\\SoftwareMonitoring\\MonitorLog");
    
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
#			::rptMsg($key_path);
#			::rptMsg("");
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $lw = $s->get_timestamp();
					my $name = $s->get_name();
				
					my $user;
					eval {
						$user = $s->get_value("Current User")->get_data();
					};
					$user = "" if ($@);
				
#				::rptMsg($lw."|REG||".$user."|M... LanDesk - ".$name." key last modified");
				
					eval {
						my @f = unpack("VV",$s->get_value("First Started")->get_data());
						my $first = ::getTime($f[0],$f[1]);
						::rptMsg($first."|REG||".$user."|LanDesk - ".$name." First Started");
					};
				
					eval {
						my @f = unpack("VV",$s->get_value("Last Started")->get_data());
						my $first = ::getTime($f[0],$f[1]);
						::rptMsg($first."|REG||".$user."|LanDesk - ".$name." Last Started");
					};
				}
			}
			else {
#				::rptMsg($key_path." does not appear to have any subkeys.")
			}
		}
		else {
#		::rptMsg($key_path." not found.");
		}
	}
# update added 20130327
	my @paths = ("LANDesk\\Inventory\\LogonHistory\\Logons",
	             "Wow6432Node\\LANDesk\\Inventory\\LogonHistory\\Logons");

	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("");	
#		::rptMsg($key_path);
#		::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
#		::rptMsg("");	
		
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
#				::rptMsg($data."  Logon: ".gmtime($name));
        	::rptMsg($name."|REG||".$data."|LANDesk - user login recorded");
				}
			}
			else {
#			::rptMsg($key_path." has not values\.");
			}
		}
		else {
#		::rptMsg($key_path." not found\.");
		}
	}
}

1;