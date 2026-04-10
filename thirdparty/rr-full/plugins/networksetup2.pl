#-----------------------------------------------------------
# networksetup2
# Gets addresses from NetworkSetup2 subkeys
# 
#
# History:
#  20200922 - MITRE update
#  20191004 - created
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package networksetup2;
use strict;

my %config = (hive          => "system",
              MITRE         => "",
              category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}

sub getShortDescr {
	return "Get NetworkSetup2 subkey info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $reg;

my %types = (0x47 => "wireless",
             0x06 => "wired",
             0x17 => "broadband (3g)");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching networksetup2 v.".$VERSION);
	::rptMsg("networksetup2 v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n");
	$reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
	my $current;
	my $ccs;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
	}
	else {
		::rptMsg($key_path." not found.");
		return;
	}
	
	my $key_path = $ccs."\\Control\\NetworkSetup2\\Interfaces";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $k (@subkeys) {
				my $alias  = "";
				my $descr  = "";
				my $type   = "";
				my $iftype = "";
				
				eval {
					$alias = $k->get_subkey("Kernel")->get_value("IfAlias")->get_data();
					$descr = $k->get_subkey("Kernel")->get_value("IfDescr")->get_data();
					$type  = $k->get_subkey("Kernel")->get_value("IfType")->get_data();
					
					if (exists $types{$type}) {
						$iftype = $types{$type};
					}
					else {
						$iftype = sprintf "0x%x",$type;
					}
#					::rptMsg($alias." - ".$descr);
				};
				
				eval {
					my $a = $k->get_subkey("Kernel")->get_value("CurrentAddress")->get_data();
					my @addr = unpack("C6",$a);
					foreach my $i (0..5) {
#						::rptMsg(sprintf "%x",$ad);
						$addr[$i] = sprintf "%x",$addr[$i];
					}
					::rptMsg($alias." - ".$descr." (".$iftype.")");
					::rptMsg("  CurrentAddress   : ".join(':',@addr));
				};
				
				eval {
					my $a = $k->get_subkey("Kernel")->get_value("PermanentAddress")->get_data();
					my @addr = unpack("C6",$a);
					foreach my $i (0..5) {
#						::rptMsg(sprintf "%x",$ad);
						$addr[$i] = sprintf "%x",$addr[$i];
					}
					::rptMsg("  PermanentAddress : ".join(':',@addr));
				};
				
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;