#-----------------------------------------------------------
# svcdll.pl
# 
# Change history
#   20091104 - created
# 
# Ref:
#   http://msdn.microsoft.com/en-us/library/aa394073(VS.85).aspx
#
# Analysis Tip: Several services keys have Parameters subkeys that point to
#   the ServiceDll value; During intrusions, a service key may be added to 
#   the system's Registry; this module provides a quick look, displaying the
#   Service names (in malware, sometimes random) and the ServiceDll value,
#   sorted based on the LastWrite time of the <service name>\Parameters subkey.
#
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package svcdll;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20091104);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists Services keys with ServiceDll values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

#my %types = (0x001 => "Kernel driver",
#             0x002 => "File system driver",
#             0x004 => "Adapter",
#             0x010 => "Own_Process",
#             0x020 => "Share_Process",
#             0x100 => "Interactive");

#my %starts = (0x00 => "Boot Start",
#              0x01 => "System Start",
#              0x02 => "Auto Start",
#              0x03 => "Manual",
#              0x04 => "Disabled");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching svcdll v.".$VERSION);
	::rptMsg("svcdll v.".$VERSION); # banner
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
		my $s_path = $ccs."\\Services";
		my $svc;
		my %svcs;
		if ($svc = $root_key->get_subkey($s_path)) {

# Get all subkeys and sort based on LastWrite times
			my @subkeys = $svc->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
#					my $display;
#					eval {
#						$display = $s->get_value("DisplayName")->get_data();
#					};
					
#					my $type;
#					eval {
#						$type = $s->get_value("Type")->get_data();
#						$type = $types{$type} if (exists $types{$type});						
#					};

#					my $image;
#					eval {
#						$image = $s->get_value("ImagePath")->get_data();
#					};
					
#					my $start;
#					eval {
#						$start = $s->get_value("Start")->get_data();
#						$start = $starts{$start} if (exists $starts{$start});
#					};
					
					my $dll;
					eval {
						$dll = $s->get_subkey("Parameters")->get_value("ServiceDll")->get_data();
						my $str = $name." -> ".$dll;
						push(@{$svcs{$s->get_timestamp()}},$str) unless ($str eq "");
					};					
				}
			
				foreach my $t (reverse sort {$a <=> $b} keys %svcs) {
					::rptMsg(gmtime($t)."Z");
					foreach my $item (@{$svcs{$t}}) {
						::rptMsg("  ".$item);
					}
					::rptMsg("");
				}
			}
			else {
				::rptMsg($s_path." has no subkeys.");
				::logMsg("Error: ".$s_path." has no subkeys.");
			}			
		}
		else {
			::rptMsg($s_path." not found.");
			::logMsg($s_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

1;