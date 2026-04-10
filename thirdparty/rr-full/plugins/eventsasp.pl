#-----------------------------------------------------------
# eventsasp.pl
# The contents of the key queried point to what's executed when someone clicks on 
# the "Event Log Online Help" link when viewing the Event Properties dialog
#  
# Change history
#   20230217 - updated (reference, added value)
#   20220613 - created
#
# References
#	https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.EventViewer::EventViewer_RedirectionProgramCommandLineParameters
#   https://www.stigviewer.com/stig/windows_server_2012_member_server/2014-01-07/finding/V-15672  
#   https://www.hexacorn.com/blog/2019/02/15/beyond-good-ol-run-key-part-103/ 
#
# Copyright 2023 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package eventsasp;
use strict;

my %config = (hive          => "software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1204\.001",
              category      => "user execution",
			  output 		=> "report",
              version       => 20230217);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching eventsasp v.".$VERSION);
	::rptMsg("eventsasp v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr());   
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @paths = ("Policies\\Microsoft\\EventViewer",
	             "Microsoft\\Windows NT\\CurrentVersion\\Event Viewer");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");

			my @values = ("MicrosoftEventVwrDisableLinks", 
						  "MicrosoftRedirectionURL",
						  "MicrosoftRedirectionProgram",
						  "MicrosoftRedirectionProgramCommandLineParameters",
						  "ConfirmURL");
						  
			foreach my $v (@values) {
				eval {
					my $t = $key->get_value($v)->get_data();
					::rptMsg(sprintf "%-50s %-30s",$v,$t);
				};
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	} 
	::rptMsg("");
	::rptMsg("Analysis Tip: The settings queried by this plugin address what occurs when a user clicks the \"Event Log Online Help\"");
	::rptMsg("link in the Event Properties dialog; this can lead to system compromise.");
	::rptMsg("");
	::rptMsg("To disable this capability, the MicrosoftEventVwrDisableLinks value must be set to \"0\"");
	::rptMsg("Ref:  https://www.stigviewer.com/stig/windows_server_2012_member_server/2014-01-07/finding/V-15672");

}

1;
