#-----------------------------------------------------------
# kbdcrash.pl
#
# Ref: 
#   http://support.microsoft.com/kb/244139
#
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package kbdcrash;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081212);

sub getConfig{return %config}

sub getShortDescr {
	return "Checks to see if system is config to crash via keyboard";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $enabled = 0;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching kbdcrash v.".$VERSION);
	::rptMsg("kbdcrash v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $svc = "ControlSet00".$current."\\Services";
		
		eval {
			my $ps2 = $svc->get_subkey("i8042prt\\Parameters")->get_value("CrashOnCtrlScroll")->get_data();
			::rptMsg("CrashOnCtrlScroll set for PS2 keyboard") if ($ps2 == 1);
			$enabled = 1 if ($ps2 == 1);
		};
		
		eval {
			my $usb = $svc->get_subkey("kbdhid\\Parameters")->get_value("CrashOnCtrlScroll")->get_data();
			::rptMsg("CrashOnCtrlScroll set for USB keyboard") if ($usb == 1);
			$enabled = 1 if ($usb == 1);
		};
		::rptMsg("CrashOnCtrlScroll not set");
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;
