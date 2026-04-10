#-----------------------------------------------------------
# rundisabled
# Get Startup items that were disabled via Task Manager or SysInternals Autoruns
#
# History:
#   20220706 - created
#
# Ref:
#   https://renenyffenegger.ch/notes/Windows/registry/tree/HKEY_CURRENT_USER/Software/Microsoft/Windows/CurrentVersion/Explorer/StartupApproved/Run/
#   https://social.technet.microsoft.com/Forums/sharepoint/en-US/f2a2b59b-aa59-46de-922c-342fbdaf6d8c/registry-key-startupapproved-ignored?forum=autoruns
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package rundisabled;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
              MITRE         => "T1562\.001",
              category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20220706);

sub getConfig{return %config}

sub getShortDescr {
	return "Get status of items in autostart locations";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching rundisabled v.".$VERSION);
	::rptMsg("rundisabled v.".$VERSION); 
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
		@paths = ("Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run",
	             "Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run32",
	             "Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\StartupFolder");
	}
	elsif ($hive_guess eq "ntuser") {
		@paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run32",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\StartupFolder");
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
					my @d = unpack("VVV",$vals{$v});
					if ($d[0] == 2 || $d[0] == 6) {
#						::rptMsg("  ".$v." - Enabled");
						::rptMsg(sprintf "%-40s %-30s",$v,"Enabled");
					}
					elsif ($d[0] == 3) {
						my $t = ::getTime($d[1],$d[2]);
#						::rptMsg("  ".$v." - Disabled ".::format8601Date($t)."Z");
						::rptMsg(sprintf "%-40s %-30s",$v,"Disabled ".::format8601Date($t)."Z");
					}
					else {}	
				}
				::rptMsg("");
			}
			else {
#				::rptMsg($key_path." has no values.");
			}
		
		}
		else {
#			::rptMsg($key_path." not found.");
#			::rptMsg("");
		}
	}
	
# Check for entries disabled via SysInternals Autoruns	
	my @paths = ();
	if ($hive_guess eq "software") {
		@paths = ("Microsoft\\Windows\\CurrentVersion\\Run\\AutorunsDisabled",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run\\AutorunsDisabled");
	}
	elsif ($hive_guess eq "ntuser") {
		@paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\Run\\AutorunsDisabled",
	             "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run\\AutorunsDisabled");
	}
	else {}
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "%-40s %-40s",$v->get_name(),$v->get_data());
				}
			}
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
			$vals{$v->get_name()} = $v->get_data();
		}
	}
	else {
	
	}
	return %vals;
}

1;