#-----------------------------------------------------------
# attachmgr.pl
# The Windows Attachment Manager manages how attachments are handled,
# and settings are on a per-user basis.  Malware has been shown to access
# these settings and make modifications.
#
# Category: Malware
#
# Change history
#  20220926 - updated
#  20200814 - MITRE updates
#  20200525 - updated date output format, removed alertMsg() functionality
#  20130425 - added alertMsg() functionality
#  20130117 - created
#
# References
#  http://journeyintoir.blogspot.com/2010/10/anatomy-of-drive-by-part-2.html
#  http://support.microsoft.com/kb/883260
#  https://support.microsoft.com/en-us/topic/information-about-the-attachment-manager-in-microsoft-windows-c48a4dcd-8de5-2af5-ee9b-cd795ae42738
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package attachmgr;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              MITRE         => "T1553\.005",
              category      => "defense evasion",
              version       => 20220926);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks user's keys that manage the Attachment Manager functionality";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	
	::logMsg("Launching attachmgr v.".$VERSION);
	::rptMsg("attachmgr v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my @attach = ('Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Associations',
	             'Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Attachments');
	
	foreach my $key_path (@attach) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) { 
					my $name = $v->get_name();
					my $data = $v->get_data();
					::rptMsg(sprintf "%-30s  %-6s",$name,$data);
				}
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
#	::rptMsg("");
	::rptMsg("Analysis Tip: Attachment Manager settings can determine security settings related to attachments.");
	::rptMsg("");
	::rptMsg("SaveZoneInformation = 1 disables saving of zone information (MOTW)");
	::rptMsg("HideZoneInfoOnProperties = 1 hides the ability for the users to manually remove zone info from files.");
	::rptMsg("");
	::rptMsg("Ref: https://support.microsoft.com/en-us/topic/information-about-the-attachment-manager-in-microsoft-windows-c48a4dcd-8de5-2af5-ee9b-cd795ae42738");
}

1;