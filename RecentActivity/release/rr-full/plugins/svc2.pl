#-----------------------------------------------------------
# svc2.pl
# Plugin for Registry Ripper; Access System hive file to get the
# services, display short format (hence "svc", shortened version
# of service.pl plugin); outputs info in .csv format
# 
# Change history
#   20081129 - created
# 
# Ref:
#   http://msdn.microsoft.com/en-us/library/aa394073(VS.85).aspx
#
# Analysis Tip: Several services keys have Parameters subkeys that point to
#   the ServiceDll value; During intrusions, a service key may be added to 
#   the system's Registry; using this module, send the output to .csv format
#   and sort on column B to get the names to line up
#
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package svc2;
#use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20081129);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists Services key contents by LastWrite times (CSV)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %types = (0x001 => "Kernel driver",
             0x002 => "File system driver",
             0x004 => "Adapter",
             0x010 => "Own_Process",
             0x020 => "Share_Process",
             0x100 => "Interactive");

my %starts = (0x00 => "Boot Start",
              0x01 => "System Start",
              0x02 => "Auto Start",
              0x03 => "Manual",
              0x04 => "Disabled");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#	::logMsg("Launching svc2 v.".$VERSION);
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
#			::rptMsg($s_path);
#			::rptMsg(getShortDescr());
#			::rptMsg("");
# Get all subkeys and sort based on LastWrite times
			my @subkeys = $svc->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
					$name = $s->get_name();
					my $display;
					eval {
						$display = $s->get_value("DisplayName")->get_data();
# take commas out of the display name, replace w/ semi-colons
						$display =~ s/,/;/g;
					};
					
					my $type;
					eval {
						$type = $s->get_value("Type")->get_data();
						$type = $types{$type} if (exists $types{$type});
							
					};

					my $image;
					eval {
						$image = $s->get_value("ImagePath")->get_data();
					};
					
					my $start;
					eval {
						$start = $s->get_value("Start")->get_data();
						$start = $starts{$start} if (exists $starts{$start});
					};
					
					my $object;
					eval {
						$object = $s->get_value("ObjectName")->get_data();
					};
					
					my $str = $name."\|".$display."\|".$image."\|".$type."\|".$start."\|".$object;
					push(@{$svcs{$s->get_timestamp()}},$str) unless ($str eq "");
# Get ServiceDll value if there is one					
					eval {
						my $para = $s->get_subkey("Parameters");
						my $dll = $para->get_value("ServiceDll")->get_data();
						my $str = $name."\\Parameters\|\|".$dll."\|\|\|";
						push(@{$svcs{$para->get_timestamp()}},$str);
					};
					
				}
			
				foreach my $t (reverse sort {$a <=> $b} keys %svcs) {
#					::rptMsg(gmtime($t)."Z");
					foreach my $item (@{$svcs{$t}}) {
						my ($n,$d,$i,$t2,$s,$o) = split(/\|/,$item,6);
#						::rptMsg($t.",".$n.",".$d.",".$i.",".$t2.",".$s.",".$o);
						::rptMsg(gmtime($t)."Z".",".$n.",".$d.",".$i.",".$t2.",".$s.",".$o);
					}
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