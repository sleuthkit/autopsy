#-----------------------------------------------------------
# fsdepends.pl
# get VHDX settings
#
# History
#  20220809 - created
#
# References
#   https://apprize.best/microsoft/internals_1/2.html
#   "VHDs can be contained within a VHD, so Windows limits the number of nesting levels of VHDs that 
#    it will present to the system as a disk to two, with the maximum number of nesting levels specified 
#    by the registry value HKLM\System\CurrentControlSet\Services\FsDepends\Parameters\VirtualDiskMaxTreeDepth. 
#
#    Mounting VHDs can be prevented by setting the registry value 
#    HKLM\System\CurrentControlSet\Services\FsDepends\Parameters\VirtualDiskNoLocalMount to 1."
#   https://insights.sei.cmu.edu/blog/the-dangers-of-vhd-and-vhdx-files/
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package fsdepends;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1553\.005",
              category      => "defense evasion",
			  output		=> "report",
              version       => 20220809);

sub getConfig{return %config}
sub getShortDescr {
	return "Get VHD[X] Settings";	
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
	
	::logMsg("Launching fsdepends v.".$VERSION);
	::rptMsg("fsdepends v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\FsDepends\\Parameters";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @vals = ("VirtualDiskExpandOnMount", "VirtualDiskMaxTreeDepth","VirtualDiskNoLocalMount");
		
		foreach my $v (@vals) {
			eval {
				my $i = $key->get_value($v)->get_data();
				::rptMsg(sprintf "%-25s 0x%04x",$v,$i);
			};
			::rptMsg("Error getting ".$v." value: ".$@) if ($@);
		}
		
		
		::rptMsg("");
		::rptMsg("Analysis Tip: The values listed impact how Windows handles VHD[X] files, which can be used to bypass security measures,");
		::rptMsg("including AV and MOTW.");
		::rptMsg("");
		::rptMsg("VirtualDiskMaxTreeDepth determines how deep to do with embedding VHD files.");
		::rptMsg("VirtualDiskNoLocalMount set to 1 prevents mounting of VHD[X] files.");
		::rptMsg("");
		::rptMsg("Ref: https://insights.sei.cmu.edu/blog/the-dangers-of-vhd-and-vhdx-files/");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1