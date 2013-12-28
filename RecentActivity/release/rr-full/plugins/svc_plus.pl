#-----------------------------------------------------------
# svc_plus.pl
#   Plugin for Registry Ripper; Access System hive file to get the
#   services, display short format (hence "svc", shortened version
#   of service.pl plugin)
# 
# Change history
#   20080610 [hca] % created
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# Author Elizabeth schweinsberg bethlogic@gmail.com
# based on svc2.pl copyright 2008 H. Carvey
#-----------------------------------------------------------
package svc_plus;
#use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120625);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists services/drivers in Services key by LastWrite times in a short format with warnings for type mismatches\n^^^^ Indicates non-standard Type\n<<<< Indicates Start mismatch for Driver\n**** Indicates ObjectName mismatch for Driver\n>>>> Indicates Start mismatch for Service\n++++ Indicates nonstandard ObjectName for Service.";	
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
	::logMsg("Launching svc_plus v.".$VERSION);
        ::rptMsg("svc_plus v.".$VERSION); # 20110830 [fpi] + banner
        ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

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
					};
				
				 	$name = $s->get_name();
					my $display;
					eval {
						$display = $s->get_value("DisplayName")->get_data();
						# take commas out of the display name, replace w/ semi-colons
						$display =~ s/,/;/g;
					};
					
					my $image;
					eval {
						$image = $s->get_value("ImagePath")->get_data();
					};
					
					my $start;
					eval {
						$start = $s->get_value("Start")->get_data();
					};
					
					my $object;
					eval {
						$object = $s->get_value("ObjectName")->get_data();
					};
					# Check for the proper start for each type
					if ($type == 0x001 || $type == 0x002) {
						if ($start == 0x002) {
							$start = "<<<<".$starts{$start};
						}
						else {
							if (exists $starts{$start}) {
                        		$start = $starts{$start};
                            }
                        }
						# Drivers should not have an object
						if ($object ne "") {
							$object = "++++".$object;
						}
					}
					if ($type == 0x010 || $type == 0x020 || $type == 0x100) {
						if ($start == 0x000 || $start == 0x001) {
							$start = ">>>>".$starts{$start}
						}
						else {
							if (exists $starts{$start}) {
								$start = $starts{$start};
							}
						}
						# Services MUST have an ObjectName, and if it's not one of these 3, check it out
						@list = ("nt authority\\localservice", "nt authority\\networkservice", "localsystem");
                        if (grep {"$_" eq lc($object)} @list ) {
						}
                        else {
                        	$object = "****".$object;
                        }
					}
					
					if (exists $types{$type}) {
						$type = $types{$types};
					}
					else {
						$type = "^^^^".$type;
					}
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
					foreach my $item (@{$svcs{$t}}) {
						my ($n,$d,$i,$t2,$s,$o) = split(/\|/,$item,6);						
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
