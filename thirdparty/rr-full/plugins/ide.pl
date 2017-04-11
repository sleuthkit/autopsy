#-----------------------------------------------------------
# ide.pl
# Get IDE device info from the System hive file
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ide;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080418);

sub getConfig{return %config}

sub getShortDescr {
	return "Get IDE device info from the System hive file";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching ide v.".$VERSION);
	::rptMsg("ide v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	::rptMsg("IDE");
	 
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
		::logMsg("Could not find ".$key_path);
		return
	}

	my $key_path = $ccs."\\Enum\\IDE";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("");
				::rptMsg($s->get_name()." [".gmtime($s->get_timestamp())."]");
				my @sk = $s->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $s2 (@sk) {
						::rptMsg($s2->get_name()." [".gmtime($s2->get_timestamp())." (UTC)]");
						eval {
							::rptMsg("FriendlyName : ".$s2->get_value("FriendlyName")->get_data());
						};
						::rptMsg("");
					}
				}
				
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
	my $key_path = $ccs."\\Control\\DeviceClasses\\{53f56307-b6bf-11d0-94f2-00a0c91efb8b}";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("DevClasses - Disks");
		::rptMsg($key_path);
		my %disks;
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				next unless (grep(/IDE/,$name));
				my $lastwrite = $s->get_timestamp();
				my ($dev, $serial) = (split(/#/,$name))[4,5];
				push(@{$disks{$lastwrite}},$dev.",".$serial);
			}
			
			if (scalar(keys %disks) == 0) {
				::rptMsg("No IDE subkeys were found.");
				return;
			}
			::rptMsg("");
			foreach my $t (reverse sort {$a <=> $b} keys %disks) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$disks{$t}}) {
					::rptMsg("\t$item");
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;