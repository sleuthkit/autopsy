#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# null.pl
# Check key/value names in a hive for a leading null character
#
# Change history
#   20160119 - created
#
# References:
#   http://www.symantec.com/connect/blogs/kovter-malware-learns-poweliks-persistent-fileless-registry-update
#
# 
# copyright 2016 QAR, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package null;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "malware",
              version       => 20160119);

sub getConfig{return %config}
sub getShortDescr {
	return "Check key/value names in a hive for leading null char";	
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
	::logMsg("Launching null v.".$VERSION);
	::rptMsg("null v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	
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
  if (checkNull($name) == 1) {
  	::rptMsg("Null char detected in key name: ".$path);
  }
  
  foreach my $val ($key->get_list_of_values()) {
  	my $val_name = $val->get_name();
  	next if ($val_name eq "");
  	if (checkNull($val_name) == 1) {
  		::rptMsg("Null char detected in value name");
  		::rptMsg("  Key  :".$path);
  		::rptMsg("  Value:".$val_name);
  		::rptMsg(""); 
  	}
  }
    
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey,$mask);
  }
}

sub checkNull {
	my $name = shift;
	my @name_list = split(//,$name);
	my $null = 0;
	my $hex = unpack("H*",$name);
	my $test = substr($hex,0,2);
	$null = 1 if ($test == 0);
	return $null;
}

1;