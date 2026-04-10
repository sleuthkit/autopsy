#-----------------------------------------------------------
# thumbnail_cleanup.pl
# 
#
# Change history:
#   20210315 - created
#
# References:
#   https://www.ghacks.net/2019/03/04/how-to-block-the-automatic-cleaning-of-windows-10s-thumbnail-cache/
#   
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package thumbnail_cleanup;
use strict;

my %config = (hive          => "software",
			  category      => "collection",
			  MITRE         => "T1005",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20210315);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Thumbnail Cache Autorun value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching thumbnail_cleanup v.".$VERSION);
	::rptMsg("thumbnail_cleanup v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Windows\\CurrentVersion\\Explorer\\VolumeCaches\\Thumbnail Cache",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VolumeCaches\\Thumbnail Cache");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
#			::rptMsg("");
			
			eval {
				my $a = $key->get_value("Autorun")->get_data();
				::rptMsg("Autorun value: ".$a);
			};
							
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: As of the Fall Creators update to Windows 10, the OS performs a number of automatic maintenance tasks,");
	::rptMsg("one of which is to automatically clear the Thumbnail Cache. A Registry setting impacts this functionality.");
	::rptMsg("");
	::rptMsg("0 - Blocks maintenance task from deleting thumbnail cache");
	::rptMsg("1 - Enables maintenance task to delete thumbnail cache");
	::rptMsg("A value of \"3\" may indicate a pre-1909 build of Windows 10");
	::rptMsg("Ref: https://www.ghacks.net/2019/03/04/how-to-block-the-automatic-cleaning-of-windows-10s-thumbnail-cache/");
}
1;