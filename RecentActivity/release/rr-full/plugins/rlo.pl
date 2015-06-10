#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# rlo.pl
# Check key/value names in a hive for RLO issue; the plugin attempts to
# show what the key/value name "looks like" via Windows-based tools, 
# which interpret the RLO control charater
#
# Change history
#   20130904 - created
#
# References:
#   https://blog.commtouch.com/cafe/malware/exe-read-backwards-spells-malware/
#
# 
# copyright 2013 QAR, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package rlo;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "malware",
              version       => 20130904);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse hive, check key/value names for RLO character";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %regkeys;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching rlo v.".$VERSION);
	::rptMsg("rlo v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	
	my $root = $root_key->as_string();
	$root = (split(/\[/,$root))[0];
	chop($root);

	traverse($root_key,$root);
}

sub traverse {
	my $key = shift;
	my $mask = shift;
  my $ts = $key->get_timestamp();
  my $path = $key->as_string();

  $path = (split(/\[/,$path))[0];
	$path =~ s/$mask//;
#	print $path."\n";
	
  my $name = $key->get_name();
  if (checkRLO($name) == 1) {
  	my ($n,$n2) = convertRLOName($name);
  	$path =~ s/$name/$n/;
  	::rptMsg("RLO control char detected in key name: ".$path." [".$n2."]");
  }
  
  foreach my $val ($key->get_list_of_values()) {
  	my $val_name = $val->get_name();
  	if (checkRLO($val_name) == 1) {
  		my ($n,$n2) = convertRLOName($val_name);
  		::rptMsg("RLO control char detected in value name: ".$path.":".$n." [".$n2."]");
  	}
  }
    
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey,$mask);
  }
}

sub checkRLO {
	my $name = shift;
	
	my @name_list = split(//,$name);
	my ($hex,@hex_list);
	my $rlo = 0;
	$hex = unpack("H*",$name);
			
	for (my $i = 0; $i < length $hex; $i+=2) {
		push(@hex_list, substr($hex,$i,2));
	}
		
	if (scalar(@name_list) == scalar(@hex_list)) {
		foreach my $i (0..(scalar(@name_list) - 1)) {
			$rlo = 1 if (($hex_list[$i] eq "2e") && ($name_list[$i] ne "\.")); 
		}
	}
	else {
		return undef;
	}
	return $rlo;
}

sub convertRLOName {
	my $name = shift;
	my @name_list = split(//,$name);
	
	my ($hex,@hex_list);
	$hex = unpack("H*",$name);
	for (my $i = 0; $i < length $hex; $i+=2) {
		push(@hex_list, substr($hex,$i,2));
	}
	
	foreach my $i (0..(scalar(@name_list) - 1)) {
		if ($hex_list[$i] eq "2e") {
			$name_list[$i] = "\.";
		}
	}
	my $str1 = join('',@name_list);
	
	my ($f,$l) = split(/\./,$str1);
	my $str2 = $f.reverse($l);
	
	return ($str1,$str2);
}

1;