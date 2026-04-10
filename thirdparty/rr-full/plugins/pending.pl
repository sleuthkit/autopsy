#-----------------------------------------------------------
# pending.pl
#
# History:
#  20230510 - added reference
#  20200922 - MITRE update
#  20130711 - created
#
# References:
#  http://technet.microsoft.com/en-us/library/cc960241.aspx
#  https://github.com/gtworek/PSBits/blob/master/Misc/PendingFileRenameOperations.cmd
#
#
# 
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package pending;
use strict;

my %config = (hive          => "System",
			  output        => "report",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1547",  
              version       => 20230510);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of PendingFileRenameOperations value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching pending v.".$VERSION);
	::rptMsg("pending v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $sm_path = $ccs."\\Control\\Session Manager";
		my $sm;
		if ($sm = $root_key->get_subkey($sm_path)) {
			
			eval {
				my $pend = $sm->get_value("PendingFileRenameOperations")->get_value();
				::rptMsg($pend);
				::rptMsg("");
				::rptMsg("Analysis Tip: While the Registry value is intended to record files to be renamed or deleted, it can also be ");
				::rptMsg("used as a persistence mechanism.");
				::rptMsg("");
				::rptMsg("Ref: https://github.com/gtworek/PSBits/blob/master/Misc/PendingFileRenameOperations.cmd");
			};
			if ($@) {
				::rptMsg("PendingFileRenameOperations value not found\.");
			}
			
		}
		else {
			::rptMsg($sm_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;