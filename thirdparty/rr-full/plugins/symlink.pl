#-----------------------------------------------------------
# symlink.pl
# 
#
# Change history:
#   20220613 - created
#
# References:
#	https://www.microsoft.com/security/blog/2022/06/13/the-many-lives-of-blackcat-ransomware/
#	https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.FileSys::SymlinkEvaluation
#   
#   
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package symlink;
use strict;

my %config = (hive          => "software,system",
			  category      => "defense evasion",
			  MITRE         => "T1562\.001",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20220613);

sub getConfig{return %config}

sub getShortDescr {
	return "Check NTFS Symlink settings";	
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
	::logMsg("Launching symlink v.".$VERSION);
	::rptMsg("symlink v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
    ::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key = ();
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my $key_path = ();
 	if ($hive_guess eq "software") {
 		$key_path = "Policies\\Microsoft\\Windows\\Filesystems\\NTFS";
 	}
 	elsif ($hive_guess eq "system") {
		my $ccs = ::getCCS($root_key);
 		$key_path = $ccs."\\Control\\FileSystem";
 	}
 	else {}
	
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @values = ("SymlinkLocalToLocalEvaluation",
					"SymlinkLocalToRemoteEvaluation",
					"SymlinkRemoteToRemoteEvaluation",
		            "SymlinkRemoteToLocalEvaluation");
		
		foreach my $v (@values) {
			eval {
				my $t = $key->get_value($v)->get_data();
				::rptMsg(sprintf "%-35s %-2d",$v,$t);
			};
		}
	}
	else {
		::rptMsg($key_path." key not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: A setting of \"1\" indicates that the evaluation is performed. The BlackCat ransomware was observed");
	::rptMsg("setting the R2L and R2R evaluations to \"1\" via fsutil.");
	::rptMsg("");
	::rptMsg("Ref: https://www.microsoft.com/security/blog/2022/06/13/the-many-lives-of-blackcat-ransomware/");
}
1;