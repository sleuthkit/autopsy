#-----------------------------------------------------------
# winnt_cv.pl
# Get and display the contents of the Windows\CurrentVersion key
# Output sorted based on length of data
#
# Change History:
# 20080609: added translation of InstallDate time
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winnt_cv;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080609);

sub getConfig{return %config}
sub getShortDescr {
	return "Get & display the contents of the Windows NT\\CurrentVersion key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winnt_cv v.".$VERSION);
	::rptMsg("winnt_cv v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("WinNT_CV");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my %cv;
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my $data = $v->get_data();
				$data = gmtime($data)." (UTC)" if ($name eq "InstallDate");
				my $len  = length($data);
				next if ($name eq "");
				if ($v->get_type() == 3) {
					$data = _translateBinary($data);
				}
				push(@{$cv{$len}},$name." : ".$data);
			}
			foreach my $t (sort {$a <=> $b} keys %cv) {
				foreach my $item (@{$cv{$t}}) {
					::rptMsg("  $item");
				}
			}	
		}
		else {
			::rptMsg($key_path." has no values.");
			::logMsg($key_path." has no values");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}


sub _translateBinary {
	my $str = unpack("H*",$_[0]);
	my $len = length($str);
	my @nstr = split(//,$str,$len);
	my @list = ();
	foreach (0..($len/2)) {
		push(@list,$nstr[$_*2].$nstr[($_*2)+1]);
	}
	return join(' ',@list);
}
1;