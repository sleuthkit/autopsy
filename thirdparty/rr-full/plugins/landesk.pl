#-----------------------------------------------------------
# landesk.pl 
# parses LANDESK Monitor Logs
#
#
#  https://community.landesk.com/docs/DOC-3249
#
# Change history
#   20160823 - added "Current Duration" parsing
#   20160822 - updated based on client engagement
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
              version       => 20160823);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of programs monitored by LANDESK - Software hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my (@ts,$d);

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
				  ::rptMsg($s->get_name());
					::rptMsg("  LastWrite: ".gmtime($s->get_timestamp())." Z");
					
					eval {
						@ts = unpack("VV",$s->get_value("Last Started")->get_data());
						::rptMsg("  Last Started: ".gmtime(::getTime($ts[0],$ts[1]))." Z");
					};
					
					eval {
						@ts = unpack("VV",$s->get_value("Last Duration")->get_data());
						my $i = c64($ts[0],$ts[1]);
						$i = $i/10000000;
						::rptMsg("  Last Duration: ".$i." sec");
					};
					
					eval {
						@ts = unpack("VV",$s->get_value("Current Duration")->get_data());
						my $i = c64($ts[0],$ts[1]);
						$i = $i/10000000;
						::rptMsg("  Current Duration: ".$i." sec");
					};
					
					eval {
						@ts = unpack("VV",$s->get_value("Total Duration")->get_data());
						my $i = c64($ts[0],$ts[1]);
						$i = $i/10000000;
						::rptMsg("  Total Duration: ".$i." sec");
					};
					
					eval {
						@ts = unpack("VV",$s->get_value("First Started")->get_data());
						::rptMsg("  First Started: ".gmtime(::getTime($ts[0],$ts[1]))." Z");
					};
					
					eval {
						::rptMsg("  Total Runs: ".$s->get_value("Total Runs")->get_data());
					};
					
					eval {
						::rptMsg("  Current User: ".$s->get_value("Current User")->get_data());
					};
					
					::rptMsg("");
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
	
	::rptMsg("");
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

# Thanks to David Cowen for sharing this code
sub c64 {
	my $n1 = shift;
	my $n2 = shift;
	
	if ($n2 != 0) {
		$n2 = ($n2 * 4294967296);
		my $n = $n1 + $n2;
		return $n;
	}
	else {
		return $n1;
	}
}

1;