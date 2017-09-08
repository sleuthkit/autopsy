#-----------------------------------------------------------
# mountdev2.pl
# Plugin for Registry Ripper; Access System hive file to get the
# MountedDevices
# 
# Change history
#   20140721 - update provided by Espen Øyslebø <eoyslebo@gmail.com>
#   20130530 - updated to output Disk Signature in correct format, thanks to
#              info provided by Tom Yarrish (see ref.)
#   20120403 - commented out time stamp info from volume GUIDs, added
#              listing of unique MAC addresses
#   20120330 - updated to parse the Volume GUIDs to get the time stamps
#   20091116 - changed output
#
# References
#   http://blogs.technet.com/b/markrussinovich/archive/2011/11/08/3463572.aspx
# 
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mountdev2;
use strict;

# Required for 32-bit versions of perl that don't support unpack Q
# update provided by Espen Øyslebø <eoyslebo@gmail.com>
my $little;
BEGIN { $little= unpack "C", pack "S", 1; }
sub squad {
	my $str = @_;
	my $big;
	if(! eval { $big= unpack( "Q", $str ); 1; }) {
		my($lo, $hi)= unpack $little ? "Ll" : "lL", $str;
		($hi, $lo)= ($lo, $hi) if (!$little);
		if ($hi < 0) {
			$hi = ~$hi;
			$lo = ~$lo;
			$big = -1 -$lo - $hi*(1 + ~0);
		} 
		else {
			$big = $lo + $hi*(1 + ~0);
		}
		if($big+1 == $big) {
			warn "Forced to approximate!\n";
		}
	}
	return $big;
}


my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140721);

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
	::rptMsg("mountdev2 v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'MountedDevices';
	my $key;
	my (%md,%dos,%vol,%offset,%macs);
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

# Section added by Espen Øyslebø <eoyslebo@gmail.com>
# gets the offset, which can be a value larger than what
# can be handled by 32-bit Perl
					my $o; #offset
					eval {
						$o = ( unpack ("Q", substr($data,4,8)) ); 
					};
					if ($@) {
						$o = (squad(substr($data,4,8)));
					}

					$vol{$v->get_name()} = $sig;
					$offset{$v->get_name()} = $o;
				}
				elsif ($len > 12) {
					$data =~ s/\x00//g;
					push(@{$md{$data}},$v->get_name());
				}
				else {
					::logMsg("mountdev2 v.".$VERSION."\tData length = $len");
				}
			}
			
			::rptMsg(sprintf "%-50s  %-20s  %20s","Volume","Disk Sig","Offset");
			::rptMsg(sprintf "%-50s  %-20s  %20s","-------","--------","--------");
			foreach my $v (sort keys %vol) {
				my $str = sprintf "%-50s  %-20s  %20s",$v,$vol{$v},$offset{$v};
				::rptMsg($str);
			}
			::rptMsg("");
			foreach my $v (sort keys %vol) {
				next unless ($v =~ m/^\\\?\?\\Volume\{/);
				my $id = $v;
				$id =~ s/^\\\?\?\\Volume\{//;
				$id =~ s/}$//;
				$id =~ s/-//g;
				my $l = hex(substr($id,0,8));
				my $m = hex(substr($id,8,4));
				my $h = hex(substr($id,12,4)) & 0x0fff;
				$h = $m | $h << 16;
				my $t = (::getTime($l,$h) - 574819200);
				::rptMsg($v);
				::rptMsg("  ".gmtime($t));
			}
			
			::rptMsg("");
			foreach my $m (sort keys %md) {
				::rptMsg("Device: ".$m);
				foreach my $item (@{$md{$m}}) {
					
					if ($item =~ m/^\\\?\?\\Volume/) {
						my $id = $item;
						$id =~ s/^\\\?\?\\Volume\{//;
						$id =~ s/}$//;
#						$id =~ s/-//g;
#						my $l = hex(substr($id,0,8));
#						my $m = hex(substr($id,8,4));
#						my $h = hex(substr($id,12,4)) & 0x0fff;
#						my $h = $m | $h << 16;
#						my $t = (::getTime($l,$h) - 574819200);
#						$item .= "  ".gmtime($t);
						my $m = (split(/-/,$id,5))[4];
						$m = uc($m);
						$m = join(':',unpack("(A2)*",$m));
						$macs{$m} = 1;
					}
					
					::rptMsg("  ".$item);
				}
				::rptMsg("");
			}
			::rptMsg("");
			::rptMsg("Unique MAC Addresses:");
			foreach (keys %macs) {
				::rptMsg($_);
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
	return join(' ',reverse @list);
}

1;
