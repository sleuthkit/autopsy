#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# fileless.pl
#  
#
# Change history
#    20150110 - updated with additional detection
#    20150101 - Created
# 
# Ref:
#    https://www.mysonicwall.com/sonicalert/searchresults.aspx?ev=article&id=761
#    http://www.malwaretech.com/2014/12/phase-bot-fileless-rootkit.html
#    http://www.kernelmode.info/forum/viewtopic.php?f=16&t=3669
#
#
# copyright 2015 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package fileless;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20150110);

sub getConfig{return %config}
sub getShortDescr {
	return "Scans a hive file looking for fileless malware entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %vals;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching fileless v.".$VERSION);
	::rptMsg("fileless v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner 
	traverse($root_key);
}

sub traverse {
	my $key = shift;
  my $ts = $key->get_timestamp();
  
  foreach my $val ($key->get_list_of_values()) {
  	my $type = $val->get_type();
  	if ($type == 1 || $type == 2) {
  		my $data = $val->get_data();
			$data = lc($data);
			if ($data =~ m/^rundll32 javascript/ || $data =~ m/^mshta/) {
				::rptMsg("**Possible fileless malware found\.");
				my $path = $key->get_path();
				my @p = split(/\\/,$path);
  			$path = join('\\',@p[1..(scalar(@p) - 1)]);
				::rptMsg($path);
				::rptMsg("LastWrite time: ".gmtime($ts)." UTC");
				::rptMsg("Data: ".$data);		
				::rptMsg("");
			}
  	}
  }
  
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey);
  }
}

1;