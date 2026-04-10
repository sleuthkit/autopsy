#-----------------------------------------------------------
# wtg.pl
# If the Windows installation is set as "Windows To Go", some operations
# have been reported as failing.
#
# History:
#  20200909 - created
#
# References:
#		https://support.microsoft.com/en-us/help/2778881/multiple-operations-fail-if-windows-8-is-improperly-identified-as-a-wi
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wtg;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",  
              version       => 20200909);

sub getConfig{return %config}
sub getShortDescr {
	return "Check for Windows To Go setting";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching wtg v.".$VERSION);
	::rptMsg("wtg v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $ccs = ::getCCS($root_key);
	
	my $key_path = $ccs."\\Control";
	if (my $key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		eval {
			my $p = $key->get_value("PortableOperatingSystem")->get_data();
			::rptMsg("PortableOperatingSystem value = ".$p);
			::rptMsg("");
			::rptMsg("Analysis Tip: If the value is set to \"1\", the system believes it is Windows To Go");
		};
		::rptMsg("PortableOperatingSystem value not found.") if ($@);
	}
}

1;