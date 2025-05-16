#-----------------------------------------------------------
# volumecaches
#
# Change history:
#  20221101 - created
# 
# Ref:
#  https://ss64.com/nt/cleanmgr-registry.html
#  https://www.hexacorn.com/blog/2018/09/02/beyond-good-ol-run-key-part-86/
#  https://learn.microsoft.com/en-us/windows/win32/lwef/disk-cleanup?redirectedfrom=MSDN#registration
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package volumecaches;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1070\.004",
			  output		=> "report",
              version       => 20221101);

sub getConfig{return %config}
sub getShortDescr {
	return "Check VolumeCaches settings for use with cleanmgr";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching volumecaches v.".$VERSION);
	::rptMsg("volumecaches v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE ATT&CK: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $key_path = ('Microsoft\\Windows\\CurrentVersion\\Explorer\\VolumeCaches');
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $count = 0;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				if (checkForStateFlags($s)) {
					
					::rptMsg($key_path."\\".$s->get_name());
					::rptMsg("LastWrite Time ".::format8601Date($s->get_timestamp())."Z");
					::rptMsg("");
				
					getStateFlagsValue($s);
					$count++;
				}
			}
			::rptMsg("No StateFlagsXXXX values found.") if ($count == 0);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: StateFlagsXXXX values beneath the VolumeCaches subkeys can be used via cleanmgr\.exe to automate");
	::rptMsg("cleanup operations by deleting files. Ex: \"cleanmgr /sagerun:64\" will clean all folders with \"StateFlags0064\"");
	::rptMsg("values set to \"2\", deleting the files in those folders; setting the value to \"0\" will disable this activity.");
#	::rptMsg("");
	::rptMsg("");
	::rptMsg("Ref: https://ss64.com/nt/cleanmgr-registry.html");
}

sub checkForStateFlags {
	my $key = shift;
	
	my $flag = 0;
	my $tag  = "StateFlags";
	
	my @vals = $key->get_list_of_values();
	if (scalar @vals > 0) {
		foreach my $v (@vals) {
			$flag = 1 if ($v->get_name() =~ m/^$tag/);
		}
	}
	return $flag;
}

sub getStateFlagsValue {
	my $key = shift;
	my $tag  = "StateFlags";
	
	my @vals = $key->get_list_of_values();
	if (scalar @vals > 0) {
		foreach my $v (@vals) {
			if ($v->get_name() =~ m/^$tag/) {
				::rptMsg(sprintf "%-16s 0x%04x",$v->get_name(),$v->get_data());
			}
		}
	}
	
}

1;