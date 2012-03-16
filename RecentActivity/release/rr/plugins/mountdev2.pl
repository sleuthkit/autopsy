#-----------------------------------------------------------
# mountdev2.pl
# Plugin for Registry Ripper; Access System hive file to get the
# MountedDevices
# 
# Change history
#   20091116 - changed output
#
# References
#
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package mountdev2;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20091116);

sub getConfig{return %config}
sub getShortDescr {
	return "Return contents of System hive MountedDevices key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching mountdev2 v.".$VERSION);
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'MountedDevices';
	my $key;
	my (%md,%dos,%vol);
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time = ".gmtime($key->get_timestamp())."Z");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $data = $v->get_data();
				my $len = length($data);
				if ($len == 12) {
					my $sig = _translateBinary(substr($data,0,4));
#					my $sig = _translateBinary($data);
					$vol{$v->get_name()} = $sig;
				}
				elsif ($len > 12) {
					$data =~ s/\00//g;
					push(@{$md{$data}},$v->get_name());
				}
				else {
					::logMsg("mountdev2 v.".$VERSION."\tData length = $len");
				}
			}
			
			::rptMsg(sprintf "%-50s  %-20s","Volume","Disk Sig");
			::rptMsg(sprintf "%-50s  %-20s","-------","--------");
			foreach my $v (sort keys %vol) {
				my $str = sprintf "%-50s  %-20s",$v,$vol{$v};
				::rptMsg($str);
			}
			
			::rptMsg("");
			foreach my $m (sort keys %md) {
				::rptMsg("Device: ".$m);
				foreach my $item (@{$md{$m}}) {
					::rptMsg("\t".$item);
				}
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		::logMsg($key_path." has no values.");
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