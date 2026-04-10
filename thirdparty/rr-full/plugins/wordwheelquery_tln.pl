#-----------------------------------------------------------
# wordwheelquery_tln.pl
# For Windows 7+
#
# Change history
#   20200916 - MITRE updates
#   20200824 - Unicode update
#   20200325 - created, copied from wordwheelquery.pl
#	  20100330 - original plugin created
#
# References
#   http://www.winhelponline.com/blog/clear-file-search-mru-history-windows-7/
# 
# copyright 2020 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package wordwheelquery_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "user activity",
			  output		=> "tln",
              version       => 20200916);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's WordWheelQuery key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
#	::logMsg("Launching wordwheelquery v.".$VERSION);
#	::rptMsg("wordwheelquery v.".$VERSION); # banner
#  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\WordWheelQuery";
	my $key;
	my $search = "";
	if ($key = $root_key->get_subkey($key_path)) {
		my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 1) {
			my $data = $key->get_value("MRUListEx")->get_data();
			my @list = unpack("V*",$data);
			if ($list[0] != 0xffffffff) {
				$search = $key->get_value($list[0])->get_data();
				$search = ::getUnicodeStr($search);
			} 
			::rptMsg($lw."|REG|||WordWheelQuery most recent search: ".$search);
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

1;