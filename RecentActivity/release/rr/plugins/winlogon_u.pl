#-----------------------------------------------------------
# winlogon_u
# Get values from user's WinLogon key
#
# Change History:
#   20091021 - created
#
# References:
#   http://support.microsoft.com/kb/119941
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winlogon_u;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091021);

sub getConfig{return %config}

sub getShortDescr {
	return "Get values from the user's WinLogon key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winlogon_u v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %wl;
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my $data = $v->get_data();
				my $len  = length($data);
				next if ($name eq "");
				if ($v->get_type() == 3) {
					$data = _translateBinary($data);
				}
				push(@{$wl{$len}},$name." = ".$data);
			}
			
			foreach my $t (sort {$a <=> $b} keys %wl) {
				foreach my $item (@{$wl{$t}}) {
					::rptMsg("  $item");
				}
			}	
			
			::rptMsg("");
			::rptMsg("Analysis Tip: Existence of RunGrpConv = 1 value may indicate that the");
			::rptMsg("              system had been infected with Bredolab (Symantec).");
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
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