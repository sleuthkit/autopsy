#-----------------------------------------------------------
# macaddr.pl
# Attempt to locate MAC address in either Software or System hive files;
# The plugin will determine which one its in and use the appropriate
# code
# 
# History:
#  20210319 - added NetworkSetup2 check
#  20201005 - MITRE update
#  20200515 - updated date output format
#  20190506 - updated
#  20090118 - created
#
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package macaddr;
use strict;

my %config = (hive          => "system,software",
              MITRE         => "",
              category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  report		=> "report",
              version       => 20210319);

sub getConfig{return %config}

sub getShortDescr {
	return "Various checks for MAC address(es)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching macaddr v.".$VERSION);
	::rptMsg("macaddr v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $guess = guessHive($hive);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	if ($guess eq "System") {
# Code for System file, getting CurrentControlSet
 		my $current;
		my $key_path = 'Select';
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			$current = $key->get_value("Current")->get_data();
			my $ccs = "ControlSet00".$current;

			my $key_path = $ccs."\\Control\\Class\\{4D36E972-E325-11CE-BFC1-08002bE10318}";
			my $key;
			my $found = 0;
			::rptMsg($key_path);
			if ($key = $root_key->get_subkey($key_path)) {
				my @subkeys = $key->get_list_of_subkeys();
				if (scalar (@subkeys) > 0) {
					foreach my $s (@subkeys) {
						my $name = $s->get_name();
						my $na;
						eval {
							$na = $key->get_subkey($name)->get_value("NetworkAddress")->get_data();
							::rptMsg("  ".$name.": NetworkAddress = ".$na);
							::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
							$found = 1;
						};	
					}
#					::rptMsg("No NetworkAddress value found.") if ($found == 0);
					::rptMsg("");
				}
				else {
					::rptMsg($key_path." has no subkeys.");
				}
			}
			else {
				::rptMsg($key_path." not found.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
# added 20210319
		my $ccs = ::getCCS($root_key);
		my $key_path = $ccs."\\Control\\NetworkSetup2\\Interfaces";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					
					eval {
						my $addr = $s->get_subkey("Kernel")->get_value("CurrentAddress")->get_data();
						$addr = join(':',unpack("(H2)*",$addr));
						$addr =~ tr/a-z/A-Z/;
						::rptMsg("Interface     : ".$s->get_subkey("Kernel")->get_value("IfAlias")->get_data());
						::rptMsg("CurrentAddress: ".$addr);
						::rptMsg("");
					};
				}
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}		

	}
	elsif ($guess eq "Software") {
		my $key_path = "Microsoft\\Windows Genuine Advantage";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my $mac;
			my $found = 0;
			eval {
				$mac = $key->get_value("MAC")->get_data();
				::rptMsg("Mac Address(es) = ".$mac);
				$found = 1;
			};
			::rptMsg("No MAC address(es) found.") if ($found == 0);	
		}
		else {
			::rptMsg($key_path." not found.");
		}	
	}
	else {
		::rptMsg("Hive file ".$hive." appeared to be neither a Software nor a");
		::rptMsg("System hive file.");
	}
}

#-------------------------------------------------------------
# guessHive() - attempts to determine the hive type; if NTUSER.DAT,
#   attempt to retrieve the SID for the user; this function populates
#   global variables (%config, @sids)
#-------------------------------------------------------------
sub guessHive {
	my $hive = shift;
	my $hive_guess;
	my $reg;
	my $root_key;
	eval {
		$reg = Parse::Win32Registry->new($hive);
	  $root_key = $reg->get_root_key;
	};
	::rptMsg($hive." may not be a valid hive.") if ($@);
	
# Check for SAM
	eval {
		if (my $key = $root_key->get_subkey("SAM\\Domains\\Account\\Users")) {
			$hive_guess = "SAM";
		}
	};
# Check for Software	
	eval {
		if ($root_key->get_subkey("Microsoft\\Windows\\CurrentVersion") &&
				$root_key->get_subkey("Microsoft\\Windows NT\\CurrentVersion")) {
			$hive_guess = "Software";
		}
	};

# Check for System	
	eval {
		if ($root_key->get_subkey("MountedDevices") && $root_key->get_subkey("Select")) {
			$hive_guess = "System";
		}
	};
	
# Check for Security	
	eval {
		if ($root_key->get_subkey("Policy\\Accounts") &&	$root_key->get_subkey("Policy\\PolAdtEv")) {
			$hive_guess = "Security";
		}
	};
# Check for NTUSER.DAT	
	eval {
	 	if ($root_key->get_subkey("Software\\Microsoft\\Windows\\CurrentVersion")) { 
	 		$hive_guess = "NTUSER\.DAT";
	 	}
	};	
	return $hive_guess;
}


1;