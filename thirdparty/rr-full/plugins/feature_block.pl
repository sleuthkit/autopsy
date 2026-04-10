#-----------------------------------------------------------
# feature_block.pl
# 
#
# Change history:
#   20230724 - created
#
# References:
#   https://www.microsoft.com/en-us/security/blog/2023/07/11/storm-0978-attacks-reveal-financial-and-espionage-motives/   
#   https://msrc.microsoft.com/update-guide/vulnerability/CVE-2023-36884
#   
#        
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package feature_block;
use strict;

my %config = (hive          => "software",
			  category      => "lateral movement",
			  MITRE         => "T1210",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20230724);

sub getConfig{return %config}

sub getShortDescr {
	return "Get FEATURE_BLOCK_CROSS_PROTOCOL_FILE_NAVIGATION key values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching feature_block v.".$VERSION);
	::rptMsg("feature_block v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Policies\\Microsoft\\Internet Explorer\\Main\\FeatureControl\\FEATURE_BLOCK_CROSS_PROTOCOL_FILE_NAVIGATION";
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Key path: ".$key_path);
		::rptMsg("Key LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-25s %-4d",$v->get_name(),$v->get_data());
			}
		}
		else {
			::rptMsg($key_path." has no values\.");
		}
	}
	else {
		::rptMsg($key_path." not found");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: For some MS customers, the \"Block all Office applications from creating child processes\"");
	::rptMsg("attack surface reduction rule will reportedly protected them from attempts to exploit CVE-2023-36884. For");
	::rptMsg("customers who cannot take advantage of these protections can set key values to \"1\" to avoid exploitation.");
	::rptMsg("");
	::rptMsg("Ref: https://www.microsoft.com/en-us/security/blog/2023/07/11/storm-0978-attacks-reveal-financial-and-espionage-motives/");
	::rptMsg("Ref: https://msrc.microsoft.com/update-guide/vulnerability/CVE-2023-36884");
}
1;