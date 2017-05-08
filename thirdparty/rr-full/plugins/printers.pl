#-----------------------------------------------------------
# printers.pl
#   Get information about printers used by a user; System hive
#   info is volatile
#
# Ref: 
#   http://support.microsoft.com/kb/102966
#   http://support.microsoft.com/kb/252388
#   http://support.microsoft.com/kb/102116
#
#   The following references contain information from the System
#   hive that is volatile.
#   http://www.undocprint.org/winspool/registry
#   http://msdn.microsoft.com/en-us/library/aa394363(VS.85).aspx
#   
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package printers;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090223);

sub getConfig{return %config}

sub getShortDescr {
	return "Get user's printers";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching printers v.".$VERSION);
	::rptMsg("printers v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\PrinterPorts";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time: ".gmtime($key->get_timestamp()));
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg("  ".$v->get_name()." (".$v->get_data().")");
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
		::rptMsg("");
# Get default printer
		my $def_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows";
		my $def;
		eval {
			$def = $root_key->get_subkey($def_path)->get_value("Device")->get_data();
			::rptMsg("Default Printer (via CurrentVersion\\Windows): ".$def);
		};
# another attempt to get the default printer
		$def_path = "Printers";
		eval {
			$def = $root_key->get_subkey($def_path)->get_value("DeviceOld")->get_data();
			::rptMsg("Default Printer (via Printers->DeviceOld): ".$def);
		};		
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
