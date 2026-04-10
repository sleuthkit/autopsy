#-----------------------------------------------------------
# automount.pl
# get automount settings
#
# History
#  20221010 - created
#
# References
#   https://learn.microsoft.com/en-us/windows/win32/api/vds/ne-vds-vds_san_policy
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package automount;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1091",
              category      => "initial access",
              version       => 20221010);

sub getConfig{return %config}
sub getShortDescr {
	return "Get automount Settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;
	
	::logMsg("Launching automount v.".$VERSION);
	::rptMsg("automount v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\mountmgr";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
				
		eval {
			my $i = $key->get_value("NoAutoMount")->get_data();
			::rptMsg("NoAutoMount value: ".$i);
		};
		::rptMsg("NoAutoMount value not found.") if ($@);

		
		::rptMsg("");
		::rptMsg("Analysis Tip: Modern Windows OSs will automount file systems, such as from USB devices, assigning a volume name.");
		::rptMsg("NoAutoMount = 0, or does not exist: enabled");
		::rptMsg("NoAutoMount = 1, disabled");
		::rptMsg("");
		::rptMsg("Ref: https://learn.microsoft.com/en-us/windows/win32/api/vds/ne-vds-vds_san_policy");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1