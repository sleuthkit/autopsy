#-----------------------------------------------------------
# enablelinkedconn.pl
#
# Change history:
#  20220707 - added CISA alert to references
#  20220214 - updated with BlackByte info
#  20201028 - created
# 
# Ref:
#  https://docs.microsoft.com/en-us/troubleshoot/windows-client/networking/mapped-drives-not-available-from-elevated-command
#  https://www.bleepingcomputer.com/news/security/ako-ransomware-another-day-another-infection-attacking-businesses/
#  https://redcanary.com/blog/blackbyte-ransomware/ <- added 02142022 (BlackByte; use with remoteuac.pl)
#  https://www.cisa.gov/uscert/ncas/alerts/aa22-181a
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package enablelinkedconn;
use strict;

my %config = (hive          => "Software",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1112",
			  output		=> "report",
              version       => 20220707);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets EnableLinkedConnections value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching enablelinkedconn v.".$VERSION);
	::rptMsg("enablelinkedconn v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	
	my $key_path = 'Microsoft\\Windows\\CurrentVersion\\Policies\\System';
	
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		eval {
			my $en = $key->get_value("EnableLinkedConnections")->get_data();
			::rptMsg("EnableLinkedConnections value = ".$en);
			
		};	
		::rptMsg("EnableLinkedConnections value not found.") if ($@);	
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: When UAC is enabled, the system creates two logon sessions at user logon. Both logon sessions are linked");
	::rptMsg("to one another. One session represents the user during an elevated session, and the other session where you run under ");
	::rptMsg("least user rights.");
	::rptMsg("");
	::rptMsg("When drive mappings are created, the system creates symbolic link objects (DosDevices) that associate the drive letters"); 
	::rptMsg(" to the UNC paths. These objects are specific for a logon session and are not shared between logon sessions.");
	::rptMsg("");
	::rptMsg("This setting has been seen being enabled by AKO, BlackByte, and MedusaLocker ransomware actors/samples.");
}
1;
