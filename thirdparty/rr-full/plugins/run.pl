#-----------------------------------------------------------
# run
# Get contents of Run key from Software & NTUSER.DAT hives
#
# History:
#   20220722 - added check for value types (https://twitter.com/keydet89/status/1550473251062747137)
#   20220706 - removed StartupApproved entries
#   20220630 - updated output to address symbolic links
#   20200921 - MITRE update
#   20200511 - created
#
# Ref:
#   https://attack.mitre.org/techniques/T1547/001/
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package run;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
              MITRE         => "T1547\.001",
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20220706);

sub getConfig{return %config}

sub getShortDescr {
	return "Get autostart key contents from Software/user hives";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching run v.".$VERSION);
	::rptMsg("run v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my @paths = ();

	if ($hive_guess eq "software") {
		@paths = ("Microsoft\\Windows\\CurrentVersion\\Run",
	             "Microsoft\\Windows\\CurrentVersion\\RunOnce",
	             "Microsoft\\Windows\\CurrentVersion\\RunServices",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	             "Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	             "Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	             "Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce");
	}
	elsif ($hive_guess eq "ntuser") {
		@paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunServices",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunServicesOnce",
	           "Software\\Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\".
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\".
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	           "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run");
	}
	else {}
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
					::rptMsg("  ".$v." - ".$vals{$v});
				}
				::rptMsg("");
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					::rptMsg("");
					::rptMsg($key_path."\\".$s->get_name());
					::rptMsg("LastWrite Time ".::format8601Date($s->get_timestamp())."Z");
					my %vals = getKeyValues($s);
					foreach my $v (keys %vals) {
						::rptMsg("  ".$v." -> ".$vals{$v});
					}
					::rptMsg("");
				}
			}
			else {
#				::rptMsg($key_path." has no subkeys.");
#				::rptMsg("");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
#			::rptMsg("");
		}
	}
}


#------------------------------------------------------------------------------
#
#
#------------------------------------------------------------------------------
sub getKeyValues {
	my $key = shift;
	my %vals;
	
	my @vk = $key->get_list_of_values();
	if (scalar(@vk) > 0) {
		foreach my $v (@vk) {
			next if ($v->get_name() eq "" && $v->get_data() eq "");
			
			my $data = $v->get_data();
			$data =~ s/\00//g if ($v->get_type() == 0x06);
			$vals{$v->get_name()} = $data;
			
# Added 20220722 
# https://twitter.com/keydet89/status/1550473251062747137
# https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-rprn/25cce700-7fcf-4bb6-a2f3-0f6d08430a55
			if ($v->get_type() != 0x01) {
				::rptMsg($v->get_name()." value is not type REG_SZ!");
			}
		}
	}
	else {
	
	}
	return %vals;
}

1;