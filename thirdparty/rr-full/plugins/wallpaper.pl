#-----------------------------------------------------------
# wallpaper.pl
#
# Wallpaper MRU 
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wallpaper;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 200800810);

sub getConfig{return %config}

sub getShortDescr {
	return "Parses Wallpaper MRU Entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching wallpaper v.".$VERSION);
	::rptMsg("wallpaper v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Wallpaper\\MRU";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("wallpaper");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my %wp;
		my @mrulist;
		
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (sort @vals) {
				my $name = $v->get_name();
				if ($name =~ m/^\d/) {
					my $data = $v->get_data();
					my $str = getStringValue($data);
					$wp{$name} = $str;
				}
				elsif ($name =~ m/^MRUList/) {
					@mrulist = unpack("V*",$v->get_data());
				}
				else {
# nothing to do					
				}
			}
			foreach my $m (@mrulist) {
				next if ($m == 0xffffffff);
				::rptMsg($m." -> ".$wp{$m});
			}
		}
		else {
			::rptMsg($key_path." has no values");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

#-----------------------------------------------------------
# getStringValue() - given a binary data type w/ a Unicode 
# string at the beginning, delimited by \x00\x00, return an ASCII
# string
#-----------------------------------------------------------
sub getStringValue {
	my $bin = shift;
	my $str = (split(/\x00\x00/,$bin,2))[0];
	$str =~ s/\x00//g;
	return $str;
}
1;