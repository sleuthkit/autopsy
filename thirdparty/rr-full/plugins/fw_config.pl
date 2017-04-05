#-----------------------------------------------------------
# fw_config
#
# References
#   http://technet2.microsoft.com/WindowsServer/en/library/47f25d7d-
#          882b-4f87-b05f-31e5664fc15e1033.mspx?mfr=true
#
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package fw_config;
use strict;

my %config = (hive          => "System",
              osmask        => 20,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080328);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets the Windows Firewall config from the System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching fw_config v.".$VERSION);
	::rptMsg("fw_config v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# Code for System file, getting CurrentControlSet
 	my $current;
 	my $ccs;
	my $select_path = 'Select';
	my $sel;
	if ($sel = $root_key->get_subkey($select_path)) {
		$current = $sel->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
	}
	else {
		::rptMsg($select_path." could not be found.");
		::logMsg($select_path." could not be found.");
		return;
	}

	my @profiles = ("DomainProfile","StandardProfile");
	foreach my $profile (@profiles) {
		my $key_path = $ccs."\\Services\\SharedAccess\\Parameters\\FirewallPolicy\\".$profile;
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("Windows Firewall Configuration");
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
					::rptMsg("\t".$v." -> ".$vals{$v});
				}
			}
			else {
#				::rptMsg($key_path." has no values.");
			}
			
			my @configs = ("RemoteAdminSettings", 
			               "IcmpSettings",
			               "GloballyOpenPorts\\List",
			               "AuthorizedApplications\\List");
		
			foreach my $config (@configs) {
				eval {
					my %vals = getKeyValues($key->get_subkey($config));
					if (scalar(keys %vals) > 0) {
						::rptMsg("");
						::rptMsg($key_path."\\".$config);
						::rptMsg("LastWrite Time ".gmtime($key->get_subkey($config)->get_timestamp())." (UTC)");
						foreach my $v (keys %vals) {
							::rptMsg("\t".$v." -> ".$vals{$v});
						}
					}
				};
			}
		}
		else {
			::rptMsg($key_path." not found.");
			::logMsg($key_path." not found.");
		}
		::rptMsg("");
	} # end foreach
}

sub getKeyValues {
	my $key = shift;
	my %vals;
	
	my @vk = $key->get_list_of_values();
	if (scalar(@vk) > 0) {
		foreach my $v (@vk) {
			next if ($v->get_name() eq "" && $v->get_data() eq "");
			$vals{$v->get_name()} = $v->get_data();
		}
	}
	else {
	
	}
	return %vals;
}
1;