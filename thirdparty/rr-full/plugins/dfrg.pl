#-----------------------------------------------------------
# dfrg.pl
# Gets contents of Dfrg\BootOptimizeFunction key
#
# Change history:
#   20110321 - created
#
# References
#   http://technet.microsoft.com/en-us/library/cc784391%28WS.10%29.aspx
#
# copyright 2011 Quantum Analytics Research, LLC (keydet89@yahoo.com)
#-----------------------------------------------------------
package dfrg;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20110321);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets content of Dfrg BootOptim. key";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching dfrg v.".$VERSION);
	::rptMsg("dfrg v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Dfrg\\BootOptimizeFunction";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Dfrg");
		::rptMsg($key_path);
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-20s %-20s",$v->get_name(),$v->get_data());
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;