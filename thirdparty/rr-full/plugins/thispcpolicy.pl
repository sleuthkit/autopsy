#-----------------------------------------------------------
# thispcpolicy
#
# This value, when set to "Hide", allows the 'extra' folders in Explorer to 
# be hidden.
#
# MITRE ATT&CK: https://attack.mitre.org/techniques/T1564/001/
# 
# Change history:
#  20200916 - MITRE updates
#  20200511 - updated date output format
#  20191002 - created
# 
# Ref:
#  https://twitter.com/craiglandis/status/1178476402942676992
#  https://www.askvg.com/tip-remove-6-extra-folders-from-windows-10-explorer-this-pc/
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package thispcpolicy;
use strict;

my %config = (hive          => "Software",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1564\.001",
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets ThisPCPolicy values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching thispcpolicy v.".$VERSION);
	::rptMsg("thispcpolicy v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	
	my %guids = ("3D Objects" => "{31C0DD25-9439-4F12-BF41-7FF4EDA38722}",
		           "Pictures"   => "{0ddd015d-b06c-45d5-8c4c-f59713854639}",
		           "Videos"     => "{35286a68-3c57-41a1-bbb1-0eae73d76c95}",
		           "Downloads"  => "{7d83ee9b-2244-4e70-b1f5-5393042af1e4}",
		           "Music"      => "{a0c69a99-21c8-4671-8703-7934162fcf1d}",
		           "Desktop"    => "{B4BFCC3A-DB2C-424C-B029-7FE99A87C641}",
		           "Documents"  => "{B4BFCC3A-DB2C-424C-B029-7FE99A87C641}");
		           
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $g (keys %guids) {
		my $key;
		::rptMsg($g." Folder");
		my $key_path = 'Microsoft\\Windows\\CurrentVersion\\Explorer\\FolderDescriptions\\'.$guids{$g}.'\\PropertyBag';
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			
			my $policy;
			eval {
				$policy = $key->get_value("ThisPCPolicy")->get_data();
				::rptMsg("ThisPCPolicy value = ".$policy);
			};
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
}
1;