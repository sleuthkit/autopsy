#-----------------------------------------------------------
# olsearch.pl
# Get OutLook search MRU
#
# Change history
#   20130124 - created
#
# References
#
# 
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package olsearch;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130124);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's OutLook Searches";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching olsearch v.".$VERSION);
	::rptMsg("olsearch v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows Messaging Subsystem\\Profiles\\Outlook\\0a0d020000000000c000000000000046';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("");
		my $search;
		eval {
			$search = $key->get_value("101f0445")->get_data();
			my %items = parseSearchMRU($search);
			::rptMsg(sprintf "%-4s %-45s","No.","Search Term");
			foreach my $i (sort keys %items) {
				::rptMsg(sprintf "%-4s %-45s",$i,$items{$i});
				
			}
		};
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub parseSearchMRU {
	my $data = shift;
	my $len = length($data);
	my %item;
	my @ofs = ();
	
	my $num = unpack("V",substr($data,0,4));

# Ugly kludge to check for 64-bit OutLook; this is ugly b/c it
# won't work if the data is really, really huge...enough to require
# 8 bytes to store the offset to the string
	if (unpack("V",substr($data,8,4)) == 0) {
		my @o = unpack("V*",substr($data,4,4 * ($num * 2)));
		foreach my $i (0..(scalar(@o) - 1)) {
			push(@ofs,$o[$i]) if (($i % 2) == 0);
		}
	}
	else {
		@ofs = unpack("V*",substr($data,4,4 * $num));
	}
	push(@ofs,$len);
	
	foreach my $i (0..($num - 1)) {
		$item{$i} = substr($data,$ofs[$i], $ofs[$i + 1] - $ofs[$i]);
		$item{$i} =~ s/\00//g;
	}
	return %item;
}

1;