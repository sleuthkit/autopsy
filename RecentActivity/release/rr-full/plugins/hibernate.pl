#-----------------------------------------------------------
# hibernate.pl
#
# Ref: 
#   http://support.microsoft.com/kb/293399 & testing
#
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package hibernate;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081216);

sub getConfig{return %config}

sub getShortDescr {
	return "Check hibernation status";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching hibernate v.".$VERSION);
	::rptMsg("hibernate v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;

		my $power_path = $ccs."\\Control\\Session Manager\\Power";
		my $power;
		if ($power = $root_key->get_subkey($power_path)) {
			
			my $heur;
			eval {
				my $bin_val = $power->get_value("Heuristics")->get_data();
				$heur = (unpack("v*",$bin_val))[3];
				if ($heur == 0) {
					::rptMsg("Hibernation disabled.");
				}
				elsif ($heur == 1) {
					::rptMsg("Hibernation enabled.");
				}
				else {
					::rptMsg("Unknown hibernation value: ".$heur);
				}
				
			};
			::rptMsg("Error reading Heuristics value.") if ($@);
			
		}
		else {
			::rptMsg($power_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
#		::logMsg($key_path." not found.");
	}
	
}
1;