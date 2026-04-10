#-----------------------------------------------------------
# allowedenum.pl
#   
#  To whitelist or show “Documents”, add the GUID {FDD39AD0-238F-46AF-ADB4-6C85480369C7} 
#  and set its value data to 1. To hide “Documents” remove the GUID value, or set its 
#  data to 0.
#
# If the “AllowedEnumeration” key exists without any whitelisted entries, none of the 
# special folders will show up in File Explorer and Desktop.
#
# Value name, or GUID, represents special folder namespace; data of 1 == show, 0 == hidden
#
# MITRE ATT&CK: https://attack.mitre.org/techniques/T1564/001/
#  
# Change history
#   20200813 - minor updates
#   20200511 - updated date output format
#   20191002 - created
#
# References
#   https://www.winhelponline.com/blog/show-hide-shell-folder-namespace-windows-10/
#
# Copyright 2019-2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package allowedenum;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1564\.001",
              category      => "defense evasion",
			  output        => "report",
              version       => 20200813);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Extracts AllowedEnumeration values to determine hidden special folders";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching allowedenum v.".$VERSION);
	::rptMsg("allowedenum v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr());   
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\AllowedEnumeration",
	             "Microsoft\\Windows\\CurrentVersion\\Explorer\\AllowedEnumeration");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");

			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					::rptMsg($v->get_name()." : ".$v->get_data());
				}
			} else {
				::rptMsg($key_path." found, has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	} 

}

1;
