#-----------------------------------------------------------
# installerlogging.pl
# Attempts to get InstallDate, DisplayName, DisplayVersion, and 
# Publisher values from Installer\UserData subkeys
#
# History
#  20230213 - created
#
# Ref:
#  https://learn.microsoft.com/ja-jp/troubleshoot/windows-client/application-management/enable-windows-installer-logging
#
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package installerlogging;
use strict;

my %config = (hive          => "software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "config",
              MITRE         => "", 
			  output        => "report",
              version       => 20230213);

sub getConfig{return %config}
sub getShortDescr {
	return "Determines product/MSI install logging";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#	::logMsg("Launching installerlogging v.".$VERSION);
	::rptMsg("Launching installerlogging v.".$VERSION);
	::rptMsg("(".getHive().") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Policies\\Microsoft\\Windows\\Installer';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Installer");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		eval {
			my $l = $key->get_value("logging")->get_data();
			::rptMsg("logging value: ".$l);
			::rptMsg("");
			::rptMsg("Analysis Tip: Parse the REG_SZ value based on the below reference.");
			::rptMsg("");
			::rptMsg("Ref: https://learn.microsoft.com/ja-jp/troubleshoot/windows-client/application-management/enable-windows-installer-logging");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;