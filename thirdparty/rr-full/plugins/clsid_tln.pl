#-----------------------------------------------------------
# clsid_tln.pl
# Plugin to extract file association data from the Software hive file
# Can take considerable time to run; recommend running it via rip.exe
#
# History
#   20180823 - minor code fix
#   20180820 - created
#
# References
#   http://msdn.microsoft.com/en-us/library/ms724475%28VS.85%29.aspx
#   https://docs.microsoft.com/en-us/windows/desktop/com/treatas
#
# #copyright 2010, Quantum Analytics Research, LLC
# copyright 2018, Quantum Analytics Research, LLC
#-----------------------------------------------------------
package clsid_tln;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20180823);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of CLSID/registered classes";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %clsid;
#	::logMsg("Launching clsid v.".$VERSION);
#	::rptMsg("clsid v.".$VERSION); # banner
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

#	my $key_path = "Classes\\CLSID";
  my @paths = ("Classes\\CLSID","Classes\\Wow6432Node\\CLSID");
  foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
#			::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#			::rptMsg("");
# First step will be to get a list of all of the file extensions
			my %ext;
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					my ($descr,$ts,$proc,$treat);
					
					$descr = $s->get_name();
					$ts = $s->get_timestamp();
					eval {
						my $n = $s->get_value("")->get_data();
						$descr .= "  ".$n unless ($n eq "");
					};
				
			  	eval {
			  		my $proc = $s->get_subkey("InprocServer32")->get_value("")->get_data();
						$descr .= "  InprocServer32: ".$proc;
			  	};
				
					eval {
			  		my $treat = $s->get_subkey("TreatAs")->get_value("")->get_data();
						$descr .= "  TreatAs: ".$treat;
			  	};
			  	::rptMsg($ts."|CLSID|||".$descr);
				}
			}
			else {
#				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
}


1;