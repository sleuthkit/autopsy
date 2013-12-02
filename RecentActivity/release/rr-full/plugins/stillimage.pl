#-----------------------------------------------------------
# stillimage.pl
# Parses contents of Enum\USB key for web cam
# 
# History
#   20100222 - created
#
# References
#   http://msdn.microsoft.com/en-us/library/ms791870.aspx
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package stillimage;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100222);

sub getConfig{return %config}

sub getShortDescr {
	return "Get info on StillImage devices";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $reg;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	$reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching stillimage v.".$VERSION);
	::rptMsg("stillimage v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
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
	
	my $key_path = $ccs."\\Control\\Class\\{6BDD1FC6-810F-11D0-BEC7-08002BE2092F}";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			::rptMsg("");
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				next unless ($name =~ m/\d\d/);
				::rptMsg($name);
				
				eval {
					my $desc = $s->get_value("DriverDesc")->get_data();
					::rptMsg("  ".$desc);
				};
				
				eval {
					my $desc = $s->get_value("MatchingDeviceID")->get_data();
					::rptMsg("  ".$desc);
				};
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}

# http://msdn.microsoft.com/en-us/library/ms791870.aspx
# StillImage logging levels
	my $key_path = $ccs."\\Control\\StillImage\\Logging";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("StillImage Logging Level");
		eval {
			my $level = $key->get_subkey("STICLI")->get_value("Level")->get_data();
			my $str = sprintf "  STICLI Logging Level = 0x%x",$level;
			::rptMsg($str);
		};
		::rptMsg("STICLI Error: ".$@) if ($@);
		
		eval {
			my $level = $key->get_subkey("STIMON")->get_value("Level")->get_data();
			my $str = sprintf "  STIMON Logging Level = 0x%x",$level;
			::rptMsg($str);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;