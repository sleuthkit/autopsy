#-----------------------------------------------------------
# pointandprint.pl
# Check Software hive for various settings - Point & print restriction policies
# affect CVE-2021-1675 patch effectiveness
#
# Change history:
#   20210705 - created
#
# References:
#   https://twitter.com/StanHacked/status/1410527329839980547
#   https://msrc.microsoft.com/update-guide/vulnerability/CVE-2021-34527
#   
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package pointandprint;
use strict;

my %config = (hive          => "software",
			  category      => "privilege escalation",
			  MITRE         => "T1068",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20210705);

sub getConfig{return %config}

sub getShortDescr {
	return "Check Point & Print restrition values";	
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
	::logMsg("Launching pointandprint v.".$VERSION);
	::rptMsg("pointandprint v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my @vals = ("NoWarningNoElevationOnInstall","NoWarningNoElevationOnUpdate","NoElevationOnInstall");
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Policies\\Microsoft\\Windows NT\\Printers\\PointAndPrint";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("");
		foreach my $v (@vals) {
			eval {
				my $i = $key->get_value($v)->get_data();
				::rptMsg(sprintf "%-20s %-5s",$v,$i);
			};
		}
	}	
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Even after applying the CVE-2021-1675 and -34527 patches, Point & Print restriction policies may");
	::rptMsg("render the patches ineffective, even on non-DC systems. This may be the case if the NoElevationOnInstall and/or ");
	::rptMsg("");
	::rptMsg("NoWarningNoElevationOnInstall values are set to \"1\".");
	::rptMsg("Ref: https://msrc.microsoft.com/update-guide/vulnerability/CVE-2021-34527");
# Ref: https://www.miltonsecurity.com/company/blog/printnightmare-0-day-exploit-windows-dc
#      If NoElevationOnInstall is set to "1", then the system is still vulnerable
}
1;