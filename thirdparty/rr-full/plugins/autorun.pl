#-----------------------------------------------------------
# autorun.pl
# Get autorun settings
#
# Change history
#
#
# References
#    http://support.microsoft.com/kb/953252
#    http://www.microsoft.com/technet/prodtechnol/windows2000serv/reskit
#         /regentry/91525.mspx?mfr=true
#
# copyright 2008-2009 H. Carvey
#-----------------------------------------------------------
package autorun;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20081212);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets autorun settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching autorun v.".$VERSION);
	::rptMsg("autorun v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		eval {
			my $nodrive = $key->get_value("NoDriveTypeAutoRun")->get_data();
			my $str = sprintf "%-20s 0x%x","NoDriveTypeAutoRun",$nodrive;
			::rptMsg($str);
		};
		::rptMsg("Error: ".$@) if ($@);

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
		::logMsg($key_path." not found.");
	}

}

1;