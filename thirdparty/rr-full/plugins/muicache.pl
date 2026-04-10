#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# muicache.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# MUICache values 
#
# References
#  https://www.youtube.com/watch?v=ea2nvxN878s&t=2s
#  https://www.magnetforensics.com/blog/forensic-analysis-of-muicache-files-in-windows/
#
# Change history
#  20221121 - reference update, added check for hive
#  20200922 - MITRE update
#  20200525 - updated date output format, removed alertMsg() functionality
#  20130425 - added alertMsg() functionality
#  20120522 - updated to collect info from Win7 USRCLASS.DAT
#
# 
# copyright 2022 Quantum Research Analytics, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package muicache;
use strict;

my %config = (hive          => "NTUSER\.DAT,USRCLASS\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1059",
              category      => "program execution",
			  output		=> "report",
              version       => 20221121);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets EXEs from user's MUICache key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching muicache v.".$VERSION);
	::rptMsg("muicache v.".$VERSION);
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = ();
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my @paths = ();
 	if ($hive_guess eq "usrclass") {
 		$key_path = 'Local Settings\\Software\\Microsoft\\Windows\\Shell\\MUICache';
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = 'Software\\Microsoft\\Windows\\ShellNoRoam\\MUICache';
 	}
 	else {}
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				next if ($name =~ m/^@/ || $name eq "LangID");
				my $data = $v->get_data();
				::rptMsg(sprintf "%-80s %-30s",$name,$data);
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}

	::rptMsg("");
	::rptMsg("Analysis Tip: MUICache holds information from apps run by the user, incorporating metadata from the file's");
	::rptMsg("\.rsrc section, or file version information. This artifact does NOT include time stamps.");
	::rptMsg("");
	::rptMsg("Ref: https://www.magnetforensics.com/blog/forensic-analysis-of-muicache-files-in-windows/");
	::rptMsg("Ref: https://www.youtube.com/watch?v=ea2nvxN878s&t=2s");

}
1;