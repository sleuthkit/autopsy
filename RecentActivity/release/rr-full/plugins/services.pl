#-----------------------------------------------------------
# services.pl
# Plugin for Registry Ripper; Access System hive file to get the
# services
# 
# Change history
#   20080507 - Added collection of Type and Start values; separated
#              data by Services vs. Drivers; created separate plugin
#              for Drivers
#   20080505 - Added collection of ImagePath and DisplayName, if avail.
#
# References
#
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package services;
#use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080507);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists services/drivers in Services key by LastWrite times";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

# Reference for types and start types:
# http://msdn.microsoft.com/en-us/library/aa394420(VS.85).aspx
my %types = (0x001 => "Kernel driver",
             0x002 => "File system driver",
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
	::logMsg("Launching services v.".$VERSION);
	::rptMsg("services v.".$VERSION); # banner
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
			::rptMsg($s_path);
			::rptMsg(getShortDescr());
			::rptMsg("");
# Get all subkeys and sort based on LastWrite times
			my @subkeys = $svc->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
					
					my $type;
					eval {
						$type = $s->get_value("Type")->get_data();
# Only look for services; drivers handled in another plugin
						if (exists $types{$type}) {
							$type = $types{$type};
						}
						else {
							$type = sprintf "0x%x",$t;
						}
					};
				
				 	$name = $s->get_name();
					my $display;
					eval {
						$display = $s->get_value("DisplayName")->get_data();
					};
					
					my $image;
					eval {
						$image = $s->get_value("ImagePath")->get_data();
					};
					
					my $start;
					eval {
						$start = $s->get_value("Start")->get_data();
						if (exists $starts{$start}) {
							$start = $starts{$start};
						}
					};
					
					my $group;
					eval {
						$group = $s->get_value("Group")->get_data();
					};
					
					my $str = $name.";".$display.";".$image.";".$type.";".$start.";".$group;
					push(@{$svcs{$s->get_timestamp()}},$str) unless ($str eq "");
				}
			
				foreach my $t (reverse sort {$a <=> $b} keys %svcs) {
					::rptMsg(gmtime($t)."Z");
					foreach my $item (@{$svcs{$t}}) {
						my ($n,$d,$i,$t,$s,$g) = split(/;/,$item,6);
						::rptMsg("  Name      = ".$n);
						::rptMsg("  Display   = ".$d);
						::rptMsg("  ImagePath = ".$i);
						::rptMsg("  Type      = ".$t);
						::rptMsg("  Start     = ".$s);
						::rptMsg("  Group     = ".$g);
						::rptMsg("");
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