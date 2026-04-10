#-----------------------------------------------------------
# dllsearch.pl
#
# History:
#  20210705 - created
#
# References:
#  https://attack.mitre.org/techniques/T1574/001/
#  https://www.tenable.com/plugins/nessus/48763
#
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package dllsearch;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "persistence", # also, privilege escalation, defense evasion
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1574\.001",  
              version       => 20210705);

sub getConfig{return %config}
sub getShortDescr {
	return "Check values that impact DLL Search Order loading";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $root_key = ();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching dllsearch v.".$VERSION);
	::rptMsg("dllsearch v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
  
	my $reg = Parse::Win32Registry->new($hive);
	$root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\Session Manager";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $i = $key->get_value("CWDIllegalInDllSearch")->get_data();
			::rptMsg(sprintf "CWDIllegalInDllSearch value: 0x%8x",$i);
		};
		::rptMsg("CWDIllegalInDllSearch value not found.") if ($@);
		
		eval {
			my $i = $key->get_value("SafeDLLSearchMode")->get_data();
			::rptMsg("SafeDLLSearchMode value    : ".$i);
		};
		::rptMsg("SafeDLLSearchMode not found.") if ($@);
	}
	else {
		::rptMsg($key_path." value not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Both values impact DLL search order processing.");
	::rptMsg("");
	::rptMsg("CWDIllegalInDllSearch:");
	::rptMsg("0xFFFFFFFF - Removes the current working directory from the default DLL search order");
	::rptMsg("0x00000001 - Blocks a DLL Load from CWD if CWD is set to a WebDAV folder");
	::rptMsg("0x00000002 - Blocks a DLL Load from CWD if CWD is set to a remote folder");
	::rptMsg("");
	::rptMsg("SafeDLLSearchMode:");
	::rptMsg("1 - Enabled; forces system to search the %SystemRoot% path before the applications CWD");
}

1;
