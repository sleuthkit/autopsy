#-----------------------------------------------------------
# lsasecrets.pl
# Get update times for LSA Secrets from the Security hive file
# 
# History
#   20140408 - updated to handle instances where the keys/values are not found
#            - this seems to occur in cases of Windows 7
#   20100219 - created
#
# References
#   http://moyix.blogspot.com/2008/02/decrypting-lsa-secrets.html
#
# copyright 2014 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package lsasecrets;
use strict;

my %config = (hive          => "Security",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140408);

sub getConfig{return %config}
sub getShortDescr {
	return "TEST - Get update times for LSA Secrets";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching lsasecrets v.".$VERSION);
  ::rptMsg("lsasecrets v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Policy\\Secrets";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Domain secret - \$MACHINE\.ACC");
# http://support.microsoft.com/kb/175468
		eval {
			my $c = $key->get_subkey("\$MACHINE\.ACC\\CupdTime")->get_value("")->get_data();
			my @v = unpack("VV",$c);
			my $cupd = gmtime(::getTime($v[0],$v[1]));
			::rptMsg("CupdTime = ".$cupd);
			
			my $o = $key->get_subkey("\$MACHINE\.ACC\\OupdTime")->get_value("")->get_data();
			my @v = unpack("VV",$c);
			my $oupd = gmtime(::getTime($v[0],$v[1]));
			::rptMsg("OupdTime = ".$oupd);
		};
		::rptMsg("\$MACHINE\.ACC key not found") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;