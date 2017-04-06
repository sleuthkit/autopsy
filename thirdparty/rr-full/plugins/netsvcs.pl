#-----------------------------------------------------------
# netsvcs.pl
# Plugin that takes contents of netsvcs value in SvcHost key (from 
# Software hive) and compares that to specific Windows services in the
# System hive.
#
# Steps:
# 1. From the names in @list, convert the names to all lower case, and create 
#    the %netsvcs hash.
# 2. Parse the Services subkey names, looking for Parameters\ServiceDLL values;
#    if found, lower case the service name and see if it exists in the %netsvcs
#    hash.  If it does, add the ServiceDLL value and the Parameters key LastWrite
#    time to the %netsvcs hash.
# 3. Determine if the service has an entry beneath the Enum\Root subkeys, with
#    a name that begins with "LEGACY_"; if so, add that information to the hash. 
#
# History:
#  20130905 - created
#
# References:
#  
#
# 
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package netsvcs;
use strict;

my %config = (hive          => "System",
							hivemask      => 4,
							output        => "report",
							category      => "malware",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31,  #XP - Win7
              version       => 20130905);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks services for netsvcs entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

# Caveat: this list of services is nominal, from a Windows 7 system.  It is not
# all inclusive, nor is it complete for XP/2003
my @list = qw/AeLookupSvc CertPropSvc SCPolicySvc lanmanserver gpsvc IKEEXT 
                 AudioSrv FastUserSwitchingCompatibility Ias Irmon Nla Ntmssvc 
                 NWCWorkstation Nwsapagent Rasauto Rasman Remoteaccess SENS Sharedaccess
                 SRService Tapisrv Wmi WmdmPmSp TermService wuauserv BITS ShellHWDetection 
                 LogonHours PCAudit helpsvc uploadmgr iphlpsvc seclogon AppInfo msiscsi MMCSS 
                 winmgmt SessionEnv browser EapHost schedule hkmsvc wercplsupport ProfSvc 
                 Themes BDESVC AppMgmt/;

my %svcdll;
my %netsvcs;

#Ref: http://support.microsoft.com/kb/103000
my %start_type = (0x00 => "Boot",
                  0x01 => "System",
                  0x02 => "Auto",
                  0x03 => "On-Demand",
                  0x04 => "Disabled");
                  
my %types = (0x01 => "Kernel Driver",
             0x02 => "File Sys Driver",
             0x04 => "Adapter args",
             0x10 => "Own Process",
             0x20 => "Share Process");
             
sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching netsvcs v.".$VERSION);
	::rptMsg("netsvcs v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
# Set up our hash		
		foreach my $l (@list) {
			my $d = $l;
			$l =~ tr/[A-Z]/[a-z]/;
			$netsvcs{$l}{DisplayName} = $d;
		}
		
		my $path = $ccs."\\Services";
		my $svc;
		if ($svc = $root_key->get_subkey($path)) {
			my @subkeys = $svc->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
								
					eval {
						my $dll = $s->get_subkey("Parameters")->get_value("ServiceDLL")->get_data();
						my $start = $s->get_value("Start")->get_data();
						my $type  = $s->get_value("Type")->get_data();
						my $name = $s->get_name();
						my $display = $name;
						$name =~ tr/[A-Z]/[a-z]/;
						if (exists $netsvcs{$name}) {
# Note: the entry in the SvcHost key netsvcs value may be spelled differently
							$netsvcs{$name}{Svc_DisplayName} = $display;
							$netsvcs{$name}{ServiceDLL} = $dll;
							$netsvcs{$name}{Start}      = $start_type{$start};
							$netsvcs{$name}{Type}       = $types{$type};
							$netsvcs{$name}{ServiceDLL_LastWrite} = $s->get_subkey("Parameters")->get_timestamp();
						}
					};
				}
			}	
		}
# check for enum\Root\LEGACY_* keys
		$path = $ccs."\\Enum\\Root";
		my $enum;
		if ($enum = $root_key->get_subkey($path)) {
			my @subkeys = $enum->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
					next unless ($name =~ m/^LEGACY_/);
					my $short = $name;
					$short =~ s/^LEGACY_//;
					$short =~ tr/[A-Z]/[a-z]/;
					
					if (exists $netsvcs{$short}) {
						$netsvcs{$short}{Legacy_LastWrite} = $s->get_timestamp();
# Try this next step...it may not work						
						eval {
							my $o = $s->get_subkey("0000")->get_timestamp();
							$netsvcs{$short}{Legacy_0000_LastWrite} = $o;
						};
					}
				}
			}
		}		
		
		foreach my $n (keys %netsvcs) {
			if (exists $netsvcs{$n}{ServiceDLL}) {
# Output: Parameters key LastWrite time, DisplayName, ServiceDLL, Svc Start, Svc Type				
				my $out = gmtime($netsvcs{$n}{ServiceDLL_LastWrite})." Z,".$netsvcs{$n}{Svc_DisplayName}.",".$netsvcs{$n}{ServiceDLL}.
				          ",".$netsvcs{$n}{Start}.",".$netsvcs{$n}{Type};
# Check to see if there's a LEGACY_* entry for the service				          
				if (exists $netsvcs{$n}{Legacy_LastWrite}) {
					$out .= ",".gmtime($netsvcs{$n}{Legacy_LastWrite})." Z";
				}          
				          
				::rptMsg($out);
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;