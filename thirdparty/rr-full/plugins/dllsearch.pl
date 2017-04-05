#-----------------------------------------------------------
# dllsearch.pl
#
# References: 
#  http://support.microsoft.com/kb/2264107
#
# Change History:
#  20100824: created
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package dllsearch;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100824);

sub getConfig{return %config}

sub getShortDescr {
	return "Get crash control information";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching dllsearch v.".$VERSION);
	::rptMsg("dllsearch v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		
		my $cc_path = "ControlSet00".$current."\\Control\\Session Manager";
		my $cc;
		if ($cc = $root_key->get_subkey($cc_path)) {
			::rptMsg("dllsearch v.".$VERSION);
			::rptMsg("");
			my $found = 1;
			eval {
				my $cde = $cc->get_value("CWDIllegalInDllSearch")->get_data();
				$found = 0;
				::rptMsg(sprintf "CWDIllegalInDllSearch = 0x%x",$cde);
			};
			::rptMsg("CWDIllegalInDllSearch value not found.") if ($found);
		}	
		else {
			::rptMsg($cc_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
