#-----------------------------------------------------------
# jumplistdata.pl
#  
#
# Change history
#   20180611 - created (per request submitted by John McCash)
#
# References
#  https://twitter.com/sv2hui/status/1005763370186891269
# 
# copyright 2018 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package jumplistdata;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180611);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's JumpListData key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching jumplistdata v.".$VERSION);
	::rptMsg("jumplistdata v.".$VERSION); 
  ::rptMsg("- ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Search\\JumpListData';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my @t = unpack("VV",$v->get_data());
				my $w = ::getTime($t[0],$t[1]);
				::rptMsg(gmtime($w)." UTC  $name");
				
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;