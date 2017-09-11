#-----------------------------------------------------------
# svc_tln.pl
# Gets services information, only outputs alerts/warnings in TLN format
# (regtime.pl gets the key LastWrite times; svc_tln.pl generates alerts
#  or warnings based on the values)
# 
# Change history
#   20130911 - updated IAW svc.pl
#   20130509 - created, based on svc.pl
# 
# Ref:
#   http://msdn.microsoft.com/en-us/library/aa394073(VS.85).aspx
#
# Analysis Tip: Several services keys have Parameters subkeys that point to
#   the ServiceDll value; During intrusions, a service key may be added to 
#   the system's Registry; using this module, send the output to .csv format
#   and sort on column B to get the names to line up
#
# Note: some checks/alerts borrowed from E. Schweinsberg's svc_plus.pl 
#       (bethlogic@gmail.com)
#
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package svc_tln;
#use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130911);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists Services key contents by LastWrite time (CSV)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %obj = ("nt authority\\localservice" => 1,
           "nt authority\\networkservice" => 1,
           "localsystem" => 1);

my %types = (0x001 => "Kernel driver",
             0x002 => "File system driver",
             0x004 => "Adapter",
             0x010 => "Own_Process",
             0x020 => "Share_Process",
             0x100 => "Interactive",
             0x110 => "Own_Process",
             0x120 => "Share_Process");

my %starts = (0x00 => "Boot Start",
              0x01 => "System Start",
              0x02 => "Auto Start",
              0x03 => "Manual",
              0x04 => "Disabled");
              
my @alerts = ("recycle","globalroot","temp","system volume information","appdata",
	            "application data");

my $display = "";
my $start   = "";
my $image   = "";
my $dll     = "";
my $object  = "";
my $para    = "";	            
	              
sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching svc_tln v.".$VERSION);
#	::rptMsg("svc_tln v.".$VERSION); # banner
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
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
					my $lw = $s->get_timestamp();
					
					eval {
						$display = $s->get_value("DisplayName")->get_data();
# take commas out of the display name, replace w/ semi-colons
						$display =~ s/,/;/g;
					};
					$display = "" if ($@);
					
					eval {
						$t = $s->get_value("Type")->get_data();
						(exists $types{$t}) ? ($type = $types{$t}) : ($type = $t);
					};
					$type = "" if ($@);

					eval {
						$image = $s->get_value("ImagePath")->get_data();
						my $lcimage = $image;
						$lcimage =~ tr/[A-Z]/[a-z]/;
						if (($t == 0x01 || $t == 0x02) && ($lcimage ne "")) {
							::alertMsg($lw."|ALERT|||svc_tln: ".$name." Driver does not end in \.sys: ".$image) unless ($lcimage =~ m/\.sys$/);
						}
						$image = "" if ($@);
						
						foreach my $a (@alerts) {
							::alertMsg($lw."|ALERT|||svc_tln: ".$a." found in path: ".$image) if (grep(/$a/,$lcimage));
						}
						
						my @list = split(/\\/,$image);
						my $last = scalar(@list) - 1;
						::alertMsg($lw."|ALERT|||svc_tln: Poss. ADS in path: ".$image) if (grep(/:/,$list[$last]));
						
					};
					
#					if (($t == 0x01 || $t == 0x02) && ($image ne "")) {
#						my $lcimage = $image;
#						$lcimage =~ tr/[A-Z]/[a-z]/;
#						::alertMsg($lw."|ALERT|||svc_tln: ".$name." Driver not in system32\\drivers folder: ".$image) unless (grep(/system32\\drivers/,$lcimage));
#					}
					
# added 20130911
# ref: http://technet.microsoft.com/en-us/library/cc742019.aspx
					eval {
						my $fa = $s->get_value("FailureAction")->get_data();
						::alertMsg($lw."|ALERT|||svc_tln: Service ".$name." has FailureAction value: ".$fa);
					};								
					
					eval {
						my $st = $s->get_value("Start")->get_data();
						(exists $starts{$st}) ? ($start = $starts{$st}) : ($start = $st);
					};
					$start = "" if ($@);
				
					eval {
						$object = $s->get_value("ObjectName")->get_data();
						my $lcobj = $object;
						$lcobj =~ tr/[A-Z]/[a-z]/;
						::alertMsg($lw."|ALERT|||svc_tln: ".$name." Unknown ObjectName: ".$object) unless (exists $obj{$lcobj});
						::alertMsg($lw."|ALERT|||svc_tln: ".$name." Driver with ObjectName: ".$object) if (($type == 0x01 || $type == 0x02) && ($object ne ""));
					};
					
					my $str = $name."\|".$display."\|".$image."\|".$type."\|".$start."\|".$object;
					push(@{$svcs{$s->get_timestamp()}},$str) unless ($str eq "");
# Get ServiceDll value if there is one					
					eval {
						$para = $s->get_subkey("Parameters");
						$dll = $para->get_value("ServiceDll")->get_data();
						my $lcdll = $dll;
						$lcdll =~ tr/[A-Z]/[a-z]/;
						::alertMsg($p_lw."|ALERT|||svc_tln: ".$name." ServiceDll does not end in \.dll\.") unless ($lcdll =~ m/\.dll$/);
						
						foreach my $a (@alerts) {
							my $lcdll = $dll;
							$lcdll =~ tr/[A-Z]/[a-z]/;
							::alertMsg($lw."|ALERT|||svc_tln: ".$a." found in path: ".$dll) if (grep(/$a/,$lcdll));
						}
						
						my @list = split(/\\/,$dll);
						my $last = scalar(@list) - 1;
						::alertMsg($lw."|ALERT|||svc_tln: Poss. ADS in path: ".$dll) if (grep(/:/,$list[$last]));
						
						my $str = $name."\\Parameters\|\|".$dll."\|\|\|";
						push(@{$svcs{$para->get_timestamp()}},$str);
					};
					
				}
			}
			else {
				::rptMsg($s_path." has no subkeys.");
			}			
		}
		else {
			::rptMsg($s_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;