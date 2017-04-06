#-----------------------------------------------------------
# wordwheelquery.pl
# For Windows 7
#
# Change history
#	  20100330 - created
#
# References
#   http://www.winhelponline.com/blog/clear-file-search-mru-history-windows-7/
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package wordwheelquery;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100330);

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
	::logMsg("Launching wordwheelquery v.".$VERSION);
	::rptMsg("wordwheelquery v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\WordWheelQuery";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my @list;
			my %wwq;
			foreach my $v (@vals) { 
				my $name = $v->get_name();
				if ($name eq "MRUListEx") {
					@list = unpack("V*",$v->get_data());
					pop(@list) if ($list[scalar(@list) - 1] == 0xffffffff);
				}
				else {
					my $data = $v->get_data();
					$data =~ s/\00//g;
					$wwq{$name} = $data;
				}
			}
# list searches in MRUListEx order
			::rptMsg("");
			::rptMsg("Searches listed in MRUListEx order");
			::rptMsg("");			
			foreach my $l (@list) {
				::rptMsg(sprintf "%-4d %-30s",$l,$wwq{$l});
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