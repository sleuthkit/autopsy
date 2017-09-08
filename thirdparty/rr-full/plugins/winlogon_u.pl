#-----------------------------------------------------------
# winlogon_u
# Get values from user's WinLogon key
#
# Change History:
#   20130425 - added alertMsg() functionality
#   20130410 - added Wow6432Node support
#   20130328 - updated with ThreatExpert info
#   20091021 - created
#
# References:
#   http://support.microsoft.com/kb/119941
#   http://www.threatexpert.com/report.aspx?md5=c463f9829bc79e0bb7296e1396ce4e01
#
# copyright 2013 QAR,LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winlogon_u;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130425);

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
	::rptMsg("winlogon_u v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my @paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon",
		           "Software\\Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon");
	
	foreach my $key_path (@paths) {
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
# checks added 20130425	
					::alertMsg("ALERT: winlogon_u: ".$key_path." RunGrpConv value found: ".$data) if ($name eq "RunGrpConv");
					if ($name =~ m/^[Ss]hell/) {
						::alertMsg("ALERT: winlogon_u: ".$key_path." Shell value not explorer\.exe: ".$data) unless ($data eq "explorer\.exe");
					}	
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
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("Analysis Tip: Existence of RunGrpConv = 1 value may indicate that the");
	::rptMsg("  system had been infected with Bredolab (Symantec)\.  Also, check the");
	::rptMsg("  contents of a \"shell\" value - should only include Explorer\.exe, if");
	::rptMsg("  it exists\.");
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