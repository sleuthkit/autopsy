#-----------------------------------------------------------
# gpohist_tln.pl
# 
#
# History
#   20150529 - created
#
# References
#   https://support.microsoft.com/en-us/kb/201453
#   
# copyright 2015 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package gpohist_tln;
use strict;

my %config = (hive          => "Software","NTUSER\.DAT",
              osmask        => 22,
              category      => "settings",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20150529);

sub getConfig{return %config}

sub getShortDescr {
	return "Collects system/user GPO history (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %gpolink = (0 => "No Link Information",
               1 => "Linked to a local machine",
               2 => "Linked to a site",
               3 => "Linked to a Domain",
               4 => "Linked to an OU");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	::logMsg("Launching gpohist_tln v.".$VERSION);
	::rptMsg("gpohist_tln v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
  my @paths = ("Microsoft\\Windows\\CurrentVersion\\Group Policy\\History",
               "Software\\Microsoft\\Windows\\CurrentVersion\\Group Policy\\History");
  foreach my $key_path (@paths) {
		my $key;
		
		if ($key = $root_key->get_subkey($key_path)) {
		
			my @subkeys1 = $key->get_list_of_subkeys();
		
			if (scalar(@subkeys1) > 0) {
				foreach my $sk1 (@subkeys1) {
				
					my @subkeys2 = $sk1->get_list_of_subkeys();
					if (scalar(@subkeys2) > 0) {
						foreach my $sk2 (@subkeys2) {
							::rptMsg($sk2->get_timestamp()."|REG|||[GPO Hist] ".$sk2->get_value("DisplayName")->get_data()." - ".$sk2->get_value("FileSysPath")->get_data().
							   " (".$gpolink{$sk2->get_value("GPOLink")->get_data()}.")");
						}
					}
				}
			}
		}
		else {
			
		}
	}
}
1;

