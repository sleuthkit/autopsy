#-----------------------------------------------------------
# amsiproviders.pl
# Get AMSI providers
#
# Change history:
#   20210601 - updated to check for removal of Windows Defender GUID
#   20210526 - updated
#   20210521 - created
#
# References:
#   https://pentestlab.blog/2021/05/17/persistence-amsi/
#   https://b4rtik.github.io/posts/antimalware-scan-interface-provider-for-persistence/
#   https://docs.microsoft.com/en-us/windows/win32/amsi/antimalware-scan-interface-portal
#   https://pentestlaboratories.com/2021/06/01/threat-hunting-amsi-bypasses/
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package amsiproviders;
use strict;

my %config = (hive          => "software",
			  category      => "persistence",
			  MITRE         => "T1546",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20210601);

sub getConfig{return %config}

sub getShortDescr {
	return "Get AMSI Providers";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my $key;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $wd_count = 0;
	::logMsg("Launching amsiproviders v.".$VERSION);
	::rptMsg("amsiproviders v.".$VERSION);
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\AMSI\\Providers",
	             "Wow6432Node\\Microsoft\\AMSI\\Providers");
	
	foreach my $key_path (@paths) {

		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			
			eval {
				my $f = $key->get_value("FeatureBits")->get_data();
				::rptMsg("FeatureBits value: ".$f);
				
			};
			if ($@) {
				::rptMsg("FeatureBits value not found.");
			}
			::rptMsg("");	

			my $wd = "{2781761E-28E0-4109-99FE-B9D127C57AFE}";
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
					$wd_count = 1 unless ($name eq $wd);
#					::rptMsg("Name: ".$name);
					my $a;
					if ($a = $s->get_value("")) {
						my $lw   = ::format8601Date($s->get_timestamp())."Z";
						::rptMsg($name);
						::rptMsg("LastWrite time: ".$lw);
						::rptMsg("Provider      : ".$a->get_data());
						
						if ($name ne "") {
							my $key_path = "Classes\\CLSID\\".$name."\\InProcServer32";
							if (my $inproc = $root_key->get_subkey($key_path)) {
								::rptMsg("Provider DLL  : ".$inproc->get_value("")->get_data());
							}
						}
						::rptMsg("");
					}
				}
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	if ($wd_count == 1) {
		::rptMsg("The AMSI provider for Windows Defender seems to have been removed/could not be found.");
		::rptMsg("");
	}
	::rptMsg("Analysis Tip: AMSI providers can be used for persistence. Ref: https://pentestlab.blog/2021/05/17/persistence-amsi/");
	::rptMsg("");
	::rptMsg("The FeatureBit check determines if Authenicode signing is enabled or not.");
	::rptMsg("  0x01 - signing check is disabled; this is the default behavior (applies if value not found)");
	::rptMsg("  0x02 - signing check is enabled");
}
1