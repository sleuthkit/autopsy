#-----------------------------------------------------------
# kankan.pl
# Looks for and retrieves Office Addins from Software/NTUSER.DAT
# hives; Win32/KanKan uses one as a persistence mech.
#
# Change history
#   20131011 - created
#
# References
#   http://www.welivesecurity.com/2013/10/11/win32kankan-chinese-drama/
#   http://msdn.microsoft.com/en-us/library/bb386106.aspx
#
# Copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package kankan;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              category      => "malware",
              version       => 20131011);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

sub getShortDescr {
	return "Extracts Office app Addin Settings";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching kankan v.".$VERSION);
  ::rptMsg("kankan v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @apps = ("Word","Excel","PowerPoint");
	
	my @paths = ("Software\\Microsoft\\Office",
	             "Wow6432Node\\Software\\Microsoft\\Office",
# Software hive	             
	             "Microsoft\\Office",
	             "Wow6432Node\\Microsoft\\Office");
	
	foreach my $key_path (@paths) {
		foreach my $app (@apps) {
			if ($key = $root_key->get_subkey($key_path."\\".$app."\\Addins")) {
				my @subkeys = $key->get_list_of_subkeys();
				
				if (scalar(@subkeys) > 0) {
					::rptMsg($app." Addins");
					foreach my $s (@subkeys) {
						::rptMsg($s->get_name()."  [".gmtime($s->get_timestamp())."]");
						
						eval {
							my $desc = $s->get_value("Description")->get_data();
							::rptMsg("  Description : ".$desc);
						};
						
						eval {
							my $fr = $s->get_value("FriendlyName")->get_data();
							::rptMsg("  FriendlyName: ".$fr);
						};
						
						eval {
							my $load = $s->get_value("LoadBehavior")->get_data();
							::rptMsg("  LoadBehavior: ".$load);
						};
						::rptMsg("");
					}
				}
				::rptMsg("");
			}
		}
	}
	::rptMsg("Tip: At least one identified variant of Win32/KanKan creates an Addin named");
	::rptMsg("InputEnhance\.Connect");
}

1;
