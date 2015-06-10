#-----------------------------------------------------------
# kb950582.pl
# Get autorun settings WRT KB950582
#
# Change history
#    18 Dec 2008 - Updated to new name; added checks for Registry
#                  keys
#
# References
#    http://support.microsoft.com/kb/953252
#    http://www.microsoft.com/technet/prodtechnol/windows2000serv/reskit
#         /regentry/91525.mspx?mfr=true
#
# copyright 2008-2009 H. Carvey
#-----------------------------------------------------------
package kb950582;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20081212);

sub getConfig{return %config}
sub getShortDescr {
	return "KB950582 - Gets autorun settings from HKLM hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching kb950582 v.".$VERSION);
	::rptMsg("kb950582 v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	eval {
		my $path = "Microsoft\\Windows\\CurrentVersion\\Uninstall\\KB950582";
		if (my $kbkey = $root_key->get_subkey($path)) {
			my $install = $kbkey->get_value("InstallDate")->get_data();
			::rptMsg("KB950528 Uninstall Key   ".gmtime($kbkey->get_timestamp()));
			::rptMsg("  InstallDate = ".$install."\n");
		}
	};
	::rptMsg("Uninstall\\KB950528 does not appear to be installed.\n") if ($@);
	
	eval {
		my $path = "Microsoft\\Updates\\Windows XP\\SP4\\KB950582";
		if (my $kbkey = $root_key->get_subkey($path)) {
			my $install = $kbkey->get_value("InstalledDate")->get_data();
			::rptMsg("KB950528 Update Key ".gmtime($kbkey->get_timestamp()));
			::rptMsg("  InstalledDate = ".$install."\n");
		}
	};
	::rptMsg("KB950528 does not appear to be installed.\n") if ($@);
	
	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $nodrive = $key->get_value("NoDriveTypeAutoRun")->get_data();
			my $str = sprintf "%-20s 0x%x","NoDriveTypeAutoRun",$nodrive;
			::rptMsg($str);
		};
		::rptMsg("NoDriveTypeAutoRun value may not exist: ".$@) if ($@);

# http://support.microsoft.com/kb/953252		
		eval {
			my $honor = $key->get_value("HonorAutorunSetting")->get_data();
			my $str = sprintf "%-20s 0x%x","HonorAutorunSetting",$honor;
			::rptMsg($str);
		};
		::rptMsg("HonorAutorunSetting not found.") if ($@);
		::rptMsg("");
		::rptMsg("Autorun settings in the HKLM hive take precedence over those in");
		::rptMsg("the HKCU hive.");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;