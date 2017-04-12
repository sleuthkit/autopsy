#-----------------------------------------------------------
# termcert.pl
# Plugin for Registry Ripper; 
# 
# Change history
#   20110316 - created
#   
# copyright 2011 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package termcert;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20110316);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Terminal Server certificate";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching termcert v.".$VERSION);
	::rptMsg("termcert v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		my $ts_path = $ccs."\\Services\\TermService\\Parameters";
		my $ts;
		if ($ts = $root_key->get_subkey($ts_path)) {
			::rptMsg($ts_path);
			::rptMsg("LastWrite Time ".gmtime($ts->get_timestamp())." (UTC)");
			::rptMsg("");
			
			my $cert;
			eval {
				$cert = $ts->get_value("Certificate")->get_raw_data();
				
				printSector($cert);
			};
			::rptMsg("Certificate value not found.") if ($@);
		}
		else {
			::rptMsg($ts_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub printSector {
	my $data = shift;
	my $len = length($data);
	my $remaining = $len;
	my $i = 0;
	
	while ($remaining > 0) {
		my $seg1 = substr($data,$i * 16,16);
		my @str1 = split(//,unpack("H*",$seg1));

		my @s3;
		foreach my $i (0..15) {
			$s3[$i] = $str1[$i * 2].$str1[($i * 2) + 1];
		}

		my $h = join(' ',@s3);
		my @s1 = unpack("A*",$seg1);
		my $s2 = join('',@s1);
		$s2 =~ s/\W/\./g;

		::rptMsg(sprintf "%-50s %-20s",$h,$s2);
		$i++;
		$remaining -= 16;
	}
}

1;