#-----------------------------------------------------------
# WinLogon
# Get values from WinLogon key
# 
# History
#    20100219 - Updated output to better present some data
#    20080415 - created
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package winlogon;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100219);

sub getConfig{return %config}

sub getShortDescr {
	return "Get values from the WinLogon key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winlogon v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
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
				if ($v->get_type() == 3 && $name ne "DCacheUpdate") {
					$data = _translateBinary($data);
				}
				
				$data = sprintf "0x%x",$data if ($name eq "SfcQuota");
				if ($name eq "DCacheUpdate") {
					my @v = unpack("VV",$data);
					$data = gmtime(::getTime($v[0],$v[1]));
				}
				
				push(@{$wl{$len}},$name." = ".$data);
			}
			
			foreach my $t (sort {$a <=> $b} keys %wl) {
				foreach my $item (@{$wl{$t}}) {
					::rptMsg("  $item");
				}
			}	
			
			::rptMsg("");
			::rptMsg("Analysis Tips: The UserInit and Shell values are executed when a user logs on.");
			
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